/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.DEFAULT_OPTIMIZATION_TEMPLATE;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.ORIGINAL_PROMPT_KEY;

import java.util.ArrayList;
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
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

import dev.langchain4j.model.chat.ChatModel;

public class IterativeOptimizer extends AbstractPromptOptimizer {

    protected static final double THRESHOLD_F1_SCORE = 1.0;

    /**
     * The maximum number of iterations/requests for the optimization process.
     */
    private static final int MAXIMUM_ITERATIONS = 10;

    private static final String MAXIMUM_ITERATIONS_KEY = "maximum_iterations";
    /**
     * The size of the training data used for optimization.
     * This is the number of training examples provided to the language model.
     */
    private static final int TRAINING_DATA_SIZE = 5;

    protected final Cache cache;

    /**
     * Provider for the language model used in classification.
     */
    protected final ChatLanguageModelProvider provider;

    /**
     * The language model instance used for classification.
     */
    protected final ChatModel llm;

    /**
     * The template used for classification requests.
     */
    protected String template;

    /**
     * The prompt used for optimization.
     * This is the initial prompt that will be optimized iteratively.
     */
    protected String optimizationPrompt;

    protected final int maximumIterations;

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
        this.maximumIterations = configuration.argumentAsInt(MAXIMUM_ITERATIONS_KEY, MAXIMUM_ITERATIONS);
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
            int maximumIterations,
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
        this.maximumIterations = maximumIterations;
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
        List<Pair<Element, float[]>> trainingTargetElements = new ArrayList<>();
        for (var element : trainingSourceStore.getAllElements(true)) {
            for (Element candidate : targetStore.findSimilar(element)) {
                trainingTargetElements.add(targetStore.getById(candidate.getIdentifier()));
            }
        }
        ElementStore trainingTargetStore = new ElementStore(trainingTargetElements, targetStore.getRetrievalStrategy());
        return optimizeIntern(trainingSourceStore, trainingTargetStore);
    }

    protected String optimizeIntern(ElementStore sourceStore, ElementStore targetStore) {
        double[] f1Scores = new double[maximumIterations];
        int i = 0;
        double f1Score;
        String modifiedPrompt = optimizationPrompt;
        do {
            logger.debug("Iteration {}: RequestPrompt = {}", i, modifiedPrompt);
            f1Score = scorePrompt(sourceStore, targetStore, modifiedPrompt);
            logger.info("Iteration {}: F1-Score = {}", i, f1Score);
            f1Scores[i] = f1Score;
            String request = template.replace(ORIGINAL_PROMPT_KEY, optimizationPrompt);
            modifiedPrompt = cachedRequest(request);
            optimizationPrompt = modifiedPrompt;
            i++;
        } while (i < maximumIterations && f1Score < THRESHOLD_F1_SCORE);
        logger.info("Iterations {}: F1-Scores = {}", i, f1Scores);
        return optimizationPrompt;
    }

    /**
     * Optimizes the prompt by sending a request to the language model.
     * The response is cached to avoid redundant calls.
     *
     * @param request The request to send to the language model
     * @return The optimized prompt extracted from the response
     */
    protected String cachedRequest(String request) {
        CacheKey cacheKey = CacheKey.of(provider.modelName(), provider.seed(), CacheKey.Mode.CHAT, request);
        String response = cache.get(cacheKey, String.class);
        if (response == null) {
            logger.info("Optimizing ({})", provider.modelName());
            response = llm.chat(request);
            cache.put(cacheKey, response);
        }
        logger.debug("Response: {}", response);
        response = extractPromptFromResponse(response);
        return response;
    }

    protected double scorePrompt(ElementStore trainingStore, ElementStore targetStore, String prompt) {

        Set<TraceLink> traceLinks = evaluateTraceLinks(trainingStore, targetStore, prompt);
        Set<TraceLink> possibleTraceLinks = getFindableTraceLinks(trainingStore, targetStore);
        var classification = cmc.calculateMetrics(traceLinks, possibleTraceLinks, null);
        return classification.getF1();
    }

    protected Set<TraceLink> evaluateTraceLinks(ElementStore trainingStore, ElementStore targetStore, String prompt) {
        classifier.setClassificationPrompt(prompt);
        List<ClassificationResult> results = classifier.classify(trainingStore, targetStore);

        Set<TraceLink> traceLinks =
                aggregator.aggregate(trainingStore.getAllElements(), targetStore.getAllElements(), results);
        return traceLinkIdPostProcessor.postprocess(traceLinks);
    }

    /**
     * Finds all trace links that can be created between the source and target stores which are included in the local
     * gold standard.
     *
     * @param sourceStore The store containing source elements
     * @param targetStore The store containing target elements
     * @return A subset of the gold standard trace links that can be created between the source and target stores
     */
    protected Set<TraceLink> getFindableTraceLinks(ElementStore sourceStore, ElementStore targetStore) {
        List<String> sourceTraceLinkIds = sourceStore.getAllElements().stream()
                .map(Element::getIdentifier)
                .map(id -> id.substring(0, id.lastIndexOf(".")))
                .toList();
        List<String> targetTraceLinkIds = targetStore.getAllElements().stream()
                .map(Element::getIdentifier)
                .map(id -> id.substring(0, id.lastIndexOf(".")))
                .toList();
        return validTraceLinks.stream()
                .filter(tl -> sourceTraceLinkIds.contains(tl.sourceId()))
                .filter(tl -> targetTraceLinkIds.contains(tl.targetId()))
                .collect(Collectors.toSet());
    }

    @Override
    protected AbstractPromptOptimizer copyOf(AbstractPromptOptimizer original) {
        return new IterativeOptimizer(
                threads,
                cache,
                provider,
                template,
                optimizationPrompt,
                maximumIterations,
                validTraceLinks,
                aggregator,
                traceLinkIdPostProcessor,
                classifier.copyOf());
    }
}
