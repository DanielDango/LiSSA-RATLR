/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.DEFAULT_OPTIMIZATION_TEMPLATE;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.ORIGINAL_PROMPT_KEY;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.kit.kastel.mcse.ardoco.metrics.ClassificationMetricsCalculator;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ChatLanguageModelProvider;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationResult;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.SourceElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.TargetElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.postprocessor.TraceLinkIdPostprocessor;
import edu.kit.kastel.sdq.lissa.ratlr.promptmetric.Metric;
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
    private final Metric metric;
    /**
     * Creates a new iterative optimizer with the specified configuration.
     *
     * @param configuration The module configuration containing optimizer settings
     * @param goldStandard The set of trace links that represent the gold standard for evaluation
     * @param aggregator The result aggregator to collect and process classification results
     * @param traceLinkIdPostProcessor The postprocessor for trace link IDs
     * @param classifier The classifier used for classification tasks
     * @param metric The metric used to score prompt classification
     */
    public IterativeOptimizer(
            ModuleConfiguration configuration,
            Set<TraceLink> goldStandard,
            ResultAggregator aggregator,
            TraceLinkIdPostprocessor traceLinkIdPostProcessor,
            Classifier classifier,
            Metric metric) {
        super(ChatLanguageModelProvider.threads(configuration));
        this.provider = new ChatLanguageModelProvider(configuration);
        this.template = configuration.argumentAsString(PROMPT_OPTIMIZATION_TEMPLATE_KEY, DEFAULT_OPTIMIZATION_TEMPLATE);
        this.maximumIterations = configuration.argumentAsInt(MAXIMUM_ITERATIONS_KEY, MAXIMUM_ITERATIONS);
        this.optimizationPrompt = configuration.argumentAsString(PROMPT_KEY, "");
        this.cache = CacheManager.getDefaultInstance().getCache(this, provider.getCacheParameters());
        this.llm = provider.createChatModel();
        this.validTraceLinks = goldStandard;
        this.aggregator = aggregator;
        this.traceLinkIdPostProcessor = traceLinkIdPostProcessor;
        this.classifier = classifier;
        this.cmc = ClassificationMetricsCalculator.getInstance();
        this.metric = metric;
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
            Classifier classifier,
            Metric metric) {
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
        this.metric = metric;
    }

    @Override
    public String optimize(SourceElementStore sourceStore, TargetElementStore targetStore) {
        Element source = sourceStore.getAllElements(true).getFirst().first();
        Element target = targetStore
                .findSimilar(sourceStore.getAllElements(true).getFirst())
                .getFirst();
        // TODO consider going back to final instead of using a mutable variable
        template = template.replace("{source_type}", source.getType()).replace("{target_type}", target.getType());
        SourceElementStore trainingSourceStore = sourceStore.reduceSourceElementStore(TRAINING_DATA_SIZE);
        TargetElementStore trainingTargetStore = targetStore.reduceTargetElementStore(trainingSourceStore);
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
    protected String optimizeIntern(SourceElementStore sourceStore, TargetElementStore targetStore) {
        double[] f1Scores = new double[maximumIterations];
        int i = 0;
        double f1Score;
        double oldF1Score;
        List<ClassificationTask> examples = getClassificationTasks(sourceStore, targetStore, validTraceLinks);
        String modifiedPrompt = optimizationPrompt;
        do {
            logger.debug("Iteration {}: RequestPrompt = {}", i, modifiedPrompt);
            oldF1Score = evaluateF1(sourceStore, targetStore, modifiedPrompt);
            f1Score = this.metric.getMetric(modifiedPrompt, examples);
            if (Math.abs(f1Score - oldF1Score) < 1e-6) {
                logger.warn(
                        "Iteration {}: Different F1 score calculated by metric ({} vs. {}).", i, f1Score, oldF1Score);
                f1Score = oldF1Score;
            }
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
        return sanitizePrompt(parseTaggedTextFirst(response, PROMPT_START, PROMPT_END));
    }

    /**
     * Evaluates the F1 score of the prompt by classifying trace links between the source and target stores using the
     * provided prompt.
     * @param sourceStore The store containing source elements
     * @param targetStore   The store containing target elements
     * @param prompt        The prompt to use for classification
     * @return The F1 score of the classification results
     */
    protected double evaluateF1(SourceElementStore sourceStore, TargetElementStore targetStore, String prompt) {

        Set<TraceLink> traceLinks = getTraceLinks(sourceStore, targetStore, prompt);
        Set<TraceLink> possibleTraceLinks = getReducedGoldStandardLinks(sourceStore, targetStore);
        var classification = cmc.calculateMetrics(traceLinks, possibleTraceLinks, null);
        int tp = classification.getTruePositives().size();
        int fp = classification.getFalsePositives().size();
        int fn = classification.getFalseNegatives().size();
        logger.debug("TP: {}, FP: {}, FN: {}", tp, fp, fn);
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
    protected Set<TraceLink> getTraceLinks(
            SourceElementStore sourceStore, TargetElementStore targetStore, String prompt) {
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
    protected Set<TraceLink> getReducedGoldStandardLinks(
            SourceElementStore sourceStore, TargetElementStore targetStore) {
        Set<TraceLink> reducedGoldStandard = new HashSet<>();
        for (var source : sourceStore.getAllElements(true)) {
            for (Element target : targetStore.findSimilar(source)) {
                TraceLink traceLink = TraceLink.of(source.first().getIdentifier(), target.getIdentifier());
                if (validTraceLinks.contains(traceLink)) {
                    reducedGoldStandard.add(traceLink);
                }
            }
        }
        return reducedGoldStandard;
        /*
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
         */
    }

    @Override
    protected AbstractPromptOptimizer copyOf(AbstractPromptOptimizer original) {
        return null;
    }
}
