/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.PromptOptimizationUtils.getClassificationTasks;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.PromptOptimizationUtils.parseTaggedTextFirst;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.PromptOptimizationUtils.sanitizePrompt;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.DEFAULT_OPTIMIZATION_TEMPLATE;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.ORIGINAL_PROMPT_KEY;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ChatLanguageModelProvider;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.SourceElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.TargetElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.promptmetric.Metric;
import edu.kit.kastel.sdq.lissa.ratlr.utils.ChatLanguageModelUtils;

import dev.langchain4j.model.chat.ChatModel;

public class IterativeOptimizer extends AbstractPromptOptimizer {

    /**
     * The default threshold for the F1 score to determine when to stop the optimization process early.
     */
    private static final double DEFAULT_THRESHOLD_SCORE = 1.0;

    private static final String THRESHOLD_SCORE_CONFIGURATION_KEY = "threshold_score";

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

    protected final Set<TraceLink> validTraceLinks;
    protected final Metric metric;
    protected final double thresholdScore;
    /**
     * Creates a new iterative optimizer with the specified configuration.
     *
     * @param configuration The module configuration containing optimizer settings
     * @param goldStandard The set of trace links that represent the gold standard for evaluation
     * @param metric The metric used to score prompt classification
     */
    public IterativeOptimizer(ModuleConfiguration configuration, Set<TraceLink> goldStandard, Metric metric) {
        super(ChatLanguageModelProvider.threads(configuration));
        this.provider = new ChatLanguageModelProvider(configuration);
        this.template = configuration.argumentAsString(PROMPT_OPTIMIZATION_TEMPLATE_KEY, DEFAULT_OPTIMIZATION_TEMPLATE);
        this.maximumIterations = configuration.argumentAsInt(MAXIMUM_ITERATIONS_KEY, MAXIMUM_ITERATIONS);
        this.optimizationPrompt = configuration.argumentAsString(PROMPT_KEY, "");
        this.thresholdScore =
                configuration.argumentAsDouble(THRESHOLD_SCORE_CONFIGURATION_KEY, DEFAULT_THRESHOLD_SCORE);
        this.cache = CacheManager.getDefaultInstance().getCache(this, provider.getCacheParameters());
        this.llm = provider.createChatModel();
        this.validTraceLinks = goldStandard;
        this.metric = metric;
    }

    public IterativeOptimizer(ModuleConfiguration configuration, Set<TraceLink> goldStandard, Metric metric,
                              int maximumIterations) {
        super(ChatLanguageModelProvider.threads(configuration));
        this.provider = new ChatLanguageModelProvider(configuration);
        this.template = configuration.argumentAsString(PROMPT_OPTIMIZATION_TEMPLATE_KEY, DEFAULT_OPTIMIZATION_TEMPLATE);
        this.maximumIterations = configuration.argumentAsInt(MAXIMUM_ITERATIONS_KEY, maximumIterations);
        this.optimizationPrompt = configuration.argumentAsString(PROMPT_KEY, "");
        this.thresholdScore =
                configuration.argumentAsDouble(THRESHOLD_SCORE_CONFIGURATION_KEY, DEFAULT_THRESHOLD_SCORE);
        this.cache = CacheManager.getDefaultInstance().getCache(this, provider.getCacheParameters());
        this.llm = provider.createChatModel();
        this.validTraceLinks = goldStandard;
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
        double[] promptScores = new double[maximumIterations];
        int i = 0;
        double promptScore;
        List<ClassificationTask> examples = getClassificationTasks(sourceStore, targetStore, validTraceLinks);
        String modifiedPrompt = optimizationPrompt;
        do {
            logger.debug("Iteration {}: RequestPrompt = {}", i, modifiedPrompt);
            promptScore = this.metric.getMetric(modifiedPrompt, examples);
            logger.debug("Iteration {}: {} = {}", i, metric.getName(), promptScore);
            promptScores[i] = promptScore;
            String request = template.replace(ORIGINAL_PROMPT_KEY, optimizationPrompt);
            modifiedPrompt = cachedSanitizedRequest(request);
            optimizationPrompt = modifiedPrompt;
            i++;
        } while (i < maximumIterations && promptScore < thresholdScore);
        logger.info("Iterations {}: {} = {}", i, metric.getName(), promptScores);
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
    }
}
