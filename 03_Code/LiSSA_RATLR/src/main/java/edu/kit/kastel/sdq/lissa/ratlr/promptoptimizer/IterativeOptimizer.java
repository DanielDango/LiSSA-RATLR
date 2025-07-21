/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import static edu.kit.kastel.sdq.lissa.ratlr.Statistics.getTraceLinksFromGoldStandard;
import static edu.kit.kastel.sdq.lissa.ratlr.classifier.SimpleClassifier.PROMPT_TEMPLATE_KEY;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.DEFAULT_OPTIMIZATION_TEMPLATE;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.ORIGINAL_PROMPT_KEY;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.PROMPT_END;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.PROMPT_START;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import edu.kit.kastel.mcse.ardoco.metrics.ClassificationMetricsCalculator;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheKey;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ChatLanguageModelProvider;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationResult;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.GoldStandardConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.postprocessor.TraceLinkIdPostprocessor;
import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;
import edu.kit.kastel.sdq.lissa.ratlr.utils.KeyGenerator;

import dev.langchain4j.model.chat.ChatModel;

public class IterativeOptimizer extends AbstractPromptOptimizer {

    private static final double THRESHOLD_F1_SCORE = 1.0;

    /**
     * The maximum number of iterations/requests for the optimization process.
     */
    private static final int MAXIMUM_ITERATIONS = 10;
    /**
     * The size of the training data used for optimization.
     * This is the number of training examples provided to the language model.
     */
    private static final int TRAINING_DATA_SIZE = 5;

    private final Cache cache;

    /**
     * Provider for the language model used in classification.
     */
    private final ChatLanguageModelProvider provider;

    /**
     * The language model instance used for classification.
     */
    private final ChatModel llm;

    /**
     * The template used for classification requests.
     */
    private final String template;

    private String optimizationPrompt;

    private ResultAggregator aggregator;
    private TraceLinkIdPostprocessor traceLinkIdPostProcessor;
    private final Set<TraceLink> validTraceLinks;
    private ClassificationMetricsCalculator cmc;
    /**
     * Creates a new iterative optimizer with the specified configuration.
     *
     * @param configuration The module configuration containing optimizer settings
     */
    public IterativeOptimizer(ModuleConfiguration configuration, GoldStandardConfiguration goldStandard) {
        super(ChatLanguageModelProvider.threads(configuration));
        this.provider = new ChatLanguageModelProvider(configuration);
        this.template = configuration.argumentAsString("optimization_template", DEFAULT_OPTIMIZATION_TEMPLATE);
        this.cache = CacheManager.getDefaultInstance()
                .getCache(this.getClass().getSimpleName() + "_" + provider.modelName() + "_" + provider.seed());
        this.llm = provider.createChatModel();
        this.validTraceLinks = getTraceLinksFromGoldStandard(goldStandard);
        setup();
    }

    private IterativeOptimizer(
            int threads,
            Cache cache,
            ChatLanguageModelProvider provider,
            String template,
            Set<TraceLink> validTraceLinks) {
        super(threads);
        this.cache = cache;
        this.provider = provider;
        this.template = template;
        this.llm = provider.createChatModel();
        this.validTraceLinks = validTraceLinks;
        setup();
    }
    /**
     * TODO: Configure in actual configuration file. Decide whether they should be args or adapted
     * Req2Req Example:
     *  "result_aggregator" : {
     *     "name" : "any_connection",
     *     "args" : {}
     *   },
     *   "tracelinkid_postprocessor" : {
     *     "name" : "req2req",
     *     "args" : {}
     */
    private void setup() {
        cmc = ClassificationMetricsCalculator.getInstance();
        this.aggregator = ResultAggregator.createResultAggregator(new ModuleConfiguration("any_connection", Map.of()));

        this.traceLinkIdPostProcessor =
                TraceLinkIdPostprocessor.createTraceLinkIdPostprocessor(new ModuleConfiguration("req2req", Map.of()));
    }

    @Override
    public String optimize(ElementStore sourceStore, ElementStore targetStore, String prompt) {
        Element source = sourceStore.getAllElements(true).getFirst().first();
        Element target = targetStore
                .findSimilar(sourceStore.getAllElements(true).getFirst().second())
                .getFirst();
        optimizationPrompt =
                template.replace("{source_type}", source.getType()).replace("{target_type}", target.getType());
        ElementStore trainingSourceStore =
                new ElementStore(sourceStore.getAllElements(false).subList(0, TRAINING_DATA_SIZE), -1);

        return optimizeIntern(trainingSourceStore, targetStore, prompt);
    }

    private String optimizeIntern(ElementStore sourceStore, ElementStore targetStore, String prompt) {
        double[] f1Scores = new double[MAXIMUM_ITERATIONS];
        int i = 0;
        double f1Score;
        String modifiedPrompt = prompt;
        do {
            logger.debug("Iteration {}: RequestPrompt = {}", i, modifiedPrompt);
            f1Score = scorePrompt(sourceStore, targetStore, modifiedPrompt);
            logger.info("Iteration {}: F1-Score = {}", i, f1Score);
            f1Scores[i] = f1Score;
            modifiedPrompt = optimize(prompt);
            prompt = modifiedPrompt;
            i++;
        } while (i < MAXIMUM_ITERATIONS && f1Score < THRESHOLD_F1_SCORE);
        logger.info("Iterations {}: F1-Scores = {}", i, f1Scores);
        return prompt;
    }

    /**
     * Optimizes the given prompt using the language model.
     * This method is used for a single iterative optimization step.
     *
     * @param prompt The original prompt to be optimized
     * @return The optimized prompt
     */
    private String optimize(String prompt) {
        String request = optimizationPrompt.replace(ORIGINAL_PROMPT_KEY, prompt);

        String key = KeyGenerator.generateKey(request);
        CacheKey cacheKey = new CacheKey(provider.modelName(), provider.seed(), CacheKey.Mode.CHAT, request, key);
        String response = cache.get(cacheKey, String.class);
        if (response == null) {
            logger.info("Optimizing ({}): {}", provider.modelName(), request);
            response = llm.chat(request);
            cache.put(cacheKey, response);
        }
        logger.debug("Response: {}", response);
        Pattern pattern = Pattern.compile(PROMPT_START + "(?s).*?" + PROMPT_END, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            response = matcher.group(0).strip();
        } else {
            logger.warn("No prompt found in response: {}", response);
            // Fallback to original prompt if no match found
            response = prompt;
        }
        return response;
    }

    private double scorePrompt(ElementStore trainingStore, ElementStore targetStore, String prompt) {
        ModuleConfiguration classifierConfig = new ModuleConfiguration(
                "simple_" + provider.platform(), Map.of("model", provider.modelName(), PROMPT_TEMPLATE_KEY, prompt));
        Classifier classifier = Classifier.createClassifier(classifierConfig);
        List<ClassificationResult> results = classifier.classify(trainingStore, targetStore);

        Set<TraceLink> traceLinks =
                aggregator.aggregate(trainingStore.getAllElements(), targetStore.getAllElements(), results);
        traceLinks = traceLinkIdPostProcessor.postprocess(traceLinks);
        List<String> traceLinkIds = trainingStore.getAllElements().stream()
                .map(Element::getIdentifier)
                .map(id -> id.substring(0, id.lastIndexOf(".")))
                .toList();
        Set<TraceLink> possibleTraceLinks = validTraceLinks.stream()
                .filter(tl -> traceLinkIds.contains(tl.sourceId()))
                .collect(Collectors.toSet());
        var classification = cmc.calculateMetrics(traceLinks, possibleTraceLinks, null);
        return classification.getF1();
    }

    @Override
    protected AbstractPromptOptimizer copyOf(AbstractPromptOptimizer original) {
        return new IterativeOptimizer(threads, cache, provider, template, validTraceLinks);
    }
}
