/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.DEFAULT_OPTIMIZATION_TEMPLATE;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.ORIGINAL_PROMPT_KEY;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.kit.kastel.mcse.ardoco.metrics.ClassificationMetricsCalculator;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheKey;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ChatLanguageModelProvider;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationResult;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.postprocessor.TraceLinkIdPostprocessor;
import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;

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
    private String template;

    /**
     * The prompt used for optimization.
     * This is the initial prompt that will be optimized iteratively.
     */
    private String optimizationPrompt;

    private final Classifier classifier;
    private final ResultAggregator aggregator;
    private final TraceLinkIdPostprocessor traceLinkIdPostProcessor;
    private final Set<TraceLink> validTraceLinks;
    private final ClassificationMetricsCalculator cmc;
    /**
     * Creates a new iterative optimizer with the specified configuration.
     *
     * @param configuration The module configuration containing optimizer settings
     */
    public IterativeOptimizer(
            ModuleConfiguration configuration,
            Set<TraceLink> goldStandard,
            ResultAggregator aggregator,
            TraceLinkIdPostprocessor traceLinkIdPostProcessor,
            Classifier classifier) {
        super(ChatLanguageModelProvider.threads(configuration));
        this.provider = new ChatLanguageModelProvider(configuration);
        this.template = configuration.argumentAsString(PROMPT_OPTIMIZATION_TEMPLATE_KEY, DEFAULT_OPTIMIZATION_TEMPLATE);
        this.optimizationPrompt = configuration.argumentAsString(PROMPT_KEY, "");
        this.cache = CacheManager.getDefaultInstance()
                .getCache(this.getClass().getSimpleName() + "_" + provider.modelName() + "_" + provider.seed());
        this.llm = provider.createChatModel();
        this.validTraceLinks = goldStandard;
        this.aggregator = aggregator;
        this.traceLinkIdPostProcessor = traceLinkIdPostProcessor;
        this.classifier = classifier;
        this.cmc = ClassificationMetricsCalculator.getInstance();
    }

    private IterativeOptimizer(
            int threads,
            Cache cache,
            ChatLanguageModelProvider provider,
            String template,
            String optimizationPrompt,
            Set<TraceLink> validTraceLinks,
            ResultAggregator aggregator,
            TraceLinkIdPostprocessor traceLinkIdPostProcessor,
            Classifier classifier) {
        super(threads);
        this.cache = cache;
        this.provider = provider;
        this.template = template;
        this.optimizationPrompt = optimizationPrompt;
        this.llm = provider.createChatModel();
        this.validTraceLinks = validTraceLinks;
        this.aggregator = aggregator;
        this.traceLinkIdPostProcessor = traceLinkIdPostProcessor;
        this.classifier = classifier;
        this.cmc = ClassificationMetricsCalculator.getInstance();
    }

    @Override
    public String optimize(ElementStore sourceStore, ElementStore targetStore) {
        Element source = sourceStore.getAllElements(true).getFirst().first();
        Element target = targetStore
                .findSimilar(sourceStore.getAllElements(true).getFirst())
                .getFirst();
        // TODO consider going back to final instead of using a mutable variable
        template = template.replace("{source_type}", source.getType()).replace("{target_type}", target.getType());
        ElementStore trainingSourceStore =
                new ElementStore(sourceStore.getAllElements(false).subList(0, TRAINING_DATA_SIZE), null);

        return optimizeIntern(trainingSourceStore, targetStore);
    }

    private String optimizeIntern(ElementStore sourceStore, ElementStore targetStore) {
        double[] f1Scores = new double[MAXIMUM_ITERATIONS];
        int i = 0;
        double f1Score;
        String modifiedPrompt = optimizationPrompt;
        do {
            logger.debug("Iteration {}: RequestPrompt = {}", i, modifiedPrompt);
            f1Score = scorePrompt(sourceStore, targetStore, modifiedPrompt);
            logger.info("Iteration {}: F1-Score = {}", i, f1Score);
            f1Scores[i] = f1Score;
            modifiedPrompt = optimize(optimizationPrompt);
            optimizationPrompt = modifiedPrompt;
            i++;
        } while (i < MAXIMUM_ITERATIONS && f1Score < THRESHOLD_F1_SCORE);
        logger.info("Iterations {}: F1-Scores = {}", i, f1Scores);
        return optimizationPrompt;
    }

    /**
     * Optimizes the given prompt using the language model.
     * This method is used for a single iterative optimization step.
     *
     * @param prompt The original prompt to be optimized
     * @return The optimized prompt
     */
    private String optimize(String prompt) {
        String request = template.replace(ORIGINAL_PROMPT_KEY, prompt);

        CacheKey cacheKey = CacheKey.of(provider.modelName(), provider.seed(), CacheKey.Mode.CHAT, request);
        String response = cache.get(cacheKey, String.class);
        if (response == null) {
            logger.info("Optimizing ({}): {}", provider.modelName(), request);
            response = llm.chat(request);
            cache.put(cacheKey, response);
        }
        logger.debug("Response: {}", response);
        response = extractPromptFromResponse(response);
        return response;
    }

    private double scorePrompt(ElementStore trainingStore, ElementStore targetStore, String prompt) {
        classifier.setClassificationPrompt(prompt);
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
        return new IterativeOptimizer(
                threads,
                cache,
                provider,
                template,
                optimizationPrompt,
                validTraceLinks,
                aggregator,
                traceLinkIdPostProcessor,
                classifier.copyOf());
    }
}
