/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.PromptOptimizationUtils.getClassificationTasks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationResult;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.SourceElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.TargetElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.postprocessor.TraceLinkIdPostprocessor;
import edu.kit.kastel.sdq.lissa.ratlr.promptmetric.Metric;
import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;

/**
 * An optimizer that uses iterative feedback to refine the prompt based on classification results.
 * This optimizer iteratively improves the prompt by analyzing misclassified trace links and adjusting the prompt accordingly.
 */
public class IterativeFeedbackOptimizer extends IterativeOptimizer {

    /**
     * The default template for the feedback prompt.
     * This template is used to generate feedback based on misclassified trace links.
     * The {examples} placeholder will be replaced with specific examples of misclassified trace links, so this key
     * should be included in the prompt.
     */
    private static final String FEEDBACK_PROMPT_TEMPLATE =
            """
            The current prompt is not performing well in classifying the following trace links. To help you improve the
            prompt, I will provide examples of misclassified trace links. Please analyze these examples and adjust the
            prompt accordingly. The examples are as follows:
            {examples}
            """;

    private static final String FEEDBACK_PROMPT_CONFIGURATION_KEY = "feedback_prompt";

    /**
     * The template for the feedback example block.
     * This template is used to format each example of a misclassified trace link.
     * The placeholders {identifier}, {source_type}, {source_content}, {target_type}, {target_content}, and {classification}
     * will be replaced with specific values for each trace link.
     */
    private static final String DEFAULT_FEEDBACK_EXAMPLE_BLOCK =
            """
            {identifier}
            {source_type}: '''{source_content}'''
            {target_type}: '''{target_content}'''
            Classification result: {classification}
            """;

    private static final String FEEDBACK_EXAMPLE_BLOCK_CONFIGURATION_KEY = "feedback_example_block";

    /**
     * The default number of feedback examples to include in the prompt.
     * This value determines how many misclassified trace links will be shown in the feedback prompt.
     */
    private static final int FEEDBACK_SIZE = 5;

    private static final String FEEDBACK_SIZE_CONFIGURATION_KEY = "feedback_size";

    private static final Logger LOGGER = LoggerFactory.getLogger(IterativeFeedbackOptimizer.class);

    private final String feedbackPrompt;
    private final String feedbackExampleBlock;
    private final int feedbackSize;

    private final Classifier classifier;
    private final ResultAggregator aggregator;
    private final TraceLinkIdPostprocessor traceLinkIdPostProcessor;
    /**
     * Creates a new instance of the iterative feedback optimizer.
     * This optimizer uses iterative feedback to refine the prompt based on classification results.
     *
     * @param configuration The module configuration containing optimizer settings
     * @param goldStandard The set of trace links that represent the gold standard for evaluation
     * @param aggregator The result aggregator to collect and process classification results
     * @param traceLinkIdPostProcessor The postprocessor for trace link IDs
     * @param classifier The classifier used for classification tasks
     */
    public IterativeFeedbackOptimizer(
            ModuleConfiguration configuration,
            Set<TraceLink> goldStandard,
            ResultAggregator aggregator,
            TraceLinkIdPostprocessor traceLinkIdPostProcessor,
            Classifier classifier,
            Metric metric) {
        super(configuration, goldStandard, metric);
        this.feedbackPrompt =
                configuration.argumentAsString(FEEDBACK_PROMPT_CONFIGURATION_KEY, FEEDBACK_PROMPT_TEMPLATE);
        this.feedbackSize = configuration.argumentAsInt(FEEDBACK_SIZE_CONFIGURATION_KEY, FEEDBACK_SIZE);
        this.feedbackExampleBlock = configuration.argumentAsString(
                FEEDBACK_EXAMPLE_BLOCK_CONFIGURATION_KEY, DEFAULT_FEEDBACK_EXAMPLE_BLOCK);
        this.aggregator = aggregator;
        this.traceLinkIdPostProcessor = traceLinkIdPostProcessor;
        this.classifier = classifier;
    }

    @Override
    protected String optimizeIntern(SourceElementStore sourceStore, TargetElementStore targetStore) {
        double[] promptScores = new double[maximumIterations];
        Set<TraceLink> possibleTraceLinks = getReducedGoldStandardLinks(sourceStore, targetStore);
        int i = 0;
        double promptScore;
        List<ClassificationTask> examples = getClassificationTasks(sourceStore, targetStore, possibleTraceLinks);
        Set<TraceLink> traceLinks;
        String modifiedPrompt = optimizationPrompt;
        do {
            LOGGER.debug("Iteration {}: RequestPrompt = {}", i, modifiedPrompt);
            traceLinks = getTraceLinks(sourceStore, targetStore, modifiedPrompt);
            promptScore = this.metric.getMetric(modifiedPrompt, examples);
            LOGGER.debug("Iteration {}: {} = {}", i, this.metric.getName(), promptScore);
            promptScores[i] = promptScore;
            String request = formattedTemplate.replace(ORIGINAL_PROMPT_PLACEHOLDER, optimizationPrompt);
            request = generateFeedbackPrompt(traceLinks, possibleTraceLinks, sourceStore, targetStore) + request;
            modifiedPrompt = cachedSanitizedRequest(request);
            optimizationPrompt = modifiedPrompt;
            i++;
        } while (i < maximumIterations && promptScore < thresholdScore);
        LOGGER.info("Iterations {}: {}s = {}", i, this.metric.getName(), promptScores);
        return optimizationPrompt;
    }

    /**
     * Fills the feedback prompt with examples of misclassified trace links using the FEEDBACK_EXAMPLE_BLOCK template.
     *
     * @param foundTraceLinks the trace links found by the classifier for the current prompt
     * @param validTraceLinks the trace links that are considered valid according to the gold standard reduced to the
     *                        training set
     * @param sourceStore     the store containing source elements
     * @param targetStore     the store containing target elements
     * @return a formatted feedback prompt containing examples of misclassified trace links
     */
    private String generateFeedbackPrompt(
            Set<TraceLink> foundTraceLinks,
            Set<TraceLink> validTraceLinks,
            ElementStore sourceStore,
            ElementStore targetStore) {

        StringBuilder feedback = new StringBuilder();
        int count = 1;

        Set<TraceLink> correctlyClassifiedTraceLinks = new HashSet<>(validTraceLinks);
        correctlyClassifiedTraceLinks.retainAll(foundTraceLinks);

        Set<TraceLink> misclassifiedTraceLinks = new HashSet<>(validTraceLinks);
        misclassifiedTraceLinks.addAll(foundTraceLinks);
        misclassifiedTraceLinks.removeAll(correctlyClassifiedTraceLinks);

        for (TraceLink traceLink : misclassifiedTraceLinks.stream().sorted().toList()) {
            if (count > feedbackSize) {
                break;
            }
            LOGGER.debug("Example {}: TraceLink {} was misclassified", count, traceLink);
            Element source = sourceStore.getById(traceLink.sourceId()).first();
            Element target = targetStore.getById(traceLink.targetId()).first();
            feedback.append(feedbackExampleBlock
                    .replace("{identifier}", count + ".")
                    .replace("{source_type}", source.getType())
                    .replace("{target_type}", target.getType())
                    .replace("{source_content}", source.getContent())
                    .replace("{target_content}", target.getContent())
                    .replace("{classification}", foundTraceLinks.contains(traceLink) ? "Yes" : "No"));
            count++;
        }
        return feedbackPrompt.replace("{examples}", feedback.toString());
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
    private Set<TraceLink> getTraceLinks(
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
    private Set<TraceLink> getReducedGoldStandardLinks(SourceElementStore sourceStore, TargetElementStore targetStore) {
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
