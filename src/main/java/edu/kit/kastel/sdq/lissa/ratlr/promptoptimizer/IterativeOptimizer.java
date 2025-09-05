/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import static edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore.reduceSourceElementStore;
import static edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore.reduceTargetStore;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.DEFAULT_OPTIMIZATION_TEMPLATE;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.ORIGINAL_PROMPT_KEY;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.kit.kastel.mcse.ardoco.metrics.ClassificationMetricsCalculator;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
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
import edu.kit.kastel.sdq.lissa.ratlr.utils.ChatLanguageModelUtils;

import dev.langchain4j.model.chat.ChatModel;

public class IterativeOptimizer extends AbstractPromptOptimizer {

    /**
     * The default threshold for the F1 score to determine when to stop the optimization process early.
     */
    protected static final double THRESHOLD_F1_SCORE = 1.0;

    /**
     * The default maximum number of iterations/requests for the optimization process.
     */
    private static final int MAXIMUM_ITERATIONS = 5;

    private static final String MAXIMUM_ITERATIONS_KEY = "maximum_iterations";

    /**
     * The default size of the training data used for optimization.
     * This is the number of elements in the source store.
     */
    protected static final int TRAINING_DATA_SIZE = 3;

    /**
     * The cache used to store and retrieve prompt optimization LLM requests.
     */
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

    /**
     * The maximum number of iterations for the optimization process.
     * This limits how many times the prompt can be modified and retried.
     */
    protected final int maximumIterations;

    protected final Classifier classifier;
    private final ResultAggregator aggregator;
    private final TraceLinkIdPostprocessor traceLinkIdPostProcessor;
    protected final Set<TraceLink> validTraceLinks;
    private final ClassificationMetricsCalculator cmc;
    /**
     * Creates a new iterative optimizer with the specified configuration.
     *
     * @param configuration The module configuration containing optimizer settings
     * @param goldStandard The set of trace links that represent the gold standard for evaluation
     * @param aggregator The result aggregator to collect and process classification results
     * @param traceLinkIdPostProcessor The postprocessor for trace link IDs
     * @param classifier The classifier used for classification tasks
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
        ElementStore trainingSourceStore = reduceSourceElementStore(sourceStore, TRAINING_DATA_SIZE);
        ElementStore trainingTargetStore = reduceTargetStore(trainingSourceStore, targetStore);
        return optimizeIntern(trainingSourceStore, trainingTargetStore);
    }

    /**
     * Optimizes the prompt by iteratively sending requests to the language model.
     * The optimization continues until the F1 score reaches a threshold or the maximum number of iterations is reached.
     *
     * @param sourceStore The store containing source elements
     * @param targetStore The store containing target elements
     * @return The optimized prompt after the iterative process
     */
    protected String optimizeIntern(ElementStore sourceStore, ElementStore targetStore) {
        double[] f1Scores = new double[maximumIterations];
        int i = 0;
        double f1Score;
        String modifiedPrompt = optimizationPrompt;
        do {
            logger.debug("Iteration {}: RequestPrompt = {}", i, modifiedPrompt);
            f1Score = evaluateF1(sourceStore, targetStore, modifiedPrompt);
            logger.debug("Iteration {}: F1-Score = {}", i, f1Score);
            f1Scores[i] = f1Score;
            String request = template.replace(ORIGINAL_PROMPT_KEY, optimizationPrompt);
            modifiedPrompt = cachedSanitizedRequest(request);
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
    protected String cachedSanitizedRequest(String request) {
        String response = ChatLanguageModelUtils.cachedRequest(request, provider, llm, cache);
        response = extractPromptFromResponse(response);
        return response;
    }

    /**
     * Evaluates the F1 score of the prompt by classifying trace links between the source and target stores using the
     * provided prompt.
     * @param sourceStore The store containing source elements
     * @param targetStore   The store containing target elements
     * @param prompt        The prompt to use for classification
     * @return The F1 score of the classification results
     */
    protected double evaluateF1(ElementStore sourceStore, ElementStore targetStore, String prompt) {

        Set<TraceLink> traceLinks = getTraceLinks(sourceStore, targetStore, prompt);
        Set<TraceLink> possibleTraceLinks = getReducedGoldStandardLinks(sourceStore, targetStore);
        var classification = cmc.calculateMetrics(traceLinks, possibleTraceLinks, null);
        return classification.getF1();
    }

    /**
     * Utilizes the internal classifier to determine existing trace links between the source and target stores using the
     * provided prompt.
     * The results are aggregated and post-processed.
     * @param sourceStore   The store containing source elements
     * @param targetStore   The store containing target elements
     * @param prompt        The prompt to use for classification
     * @return A set of trace links that were classified based on the prompt
     */
    protected Set<TraceLink> getTraceLinks(ElementStore sourceStore, ElementStore targetStore, String prompt) {
        classifier.setClassificationPrompt(prompt);
        List<ClassificationResult> results = classifier.classify(sourceStore, targetStore);

        Set<TraceLink> traceLinks =
                aggregator.aggregate(sourceStore.getAllElements(), targetStore.getAllElements(), results);
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
    protected Set<TraceLink> getReducedGoldStandardLinks(ElementStore sourceStore, ElementStore targetStore) {
        List<String> sourceTraceLinkIds = sourceStore.getAllElements().stream()
                .map(Element::getIdentifier)
                .toList();
        List<String> targetTraceLinkIds = targetStore.getAllElements().stream()
                .map(Element::getIdentifier)
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
