/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.ORIGINAL_PROMPT_KEY;

import java.util.HashSet;
import java.util.Set;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.SourceElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.TargetElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.postprocessor.TraceLinkIdPostprocessor;
import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;
import edu.kit.kastel.sdq.lissa.ratlr.scorer.AbstractScorer;
import edu.kit.kastel.sdq.lissa.ratlr.scorer.Scorer;

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
    public static final String FEEDBACK_PROMPT_TEMPLATE =
            """
            The current prompt is not performing well in classifying the following trace links. To help you improve the
            prompt, I will provide examples of misclassified trace links. Please analyze these examples and adjust the
            prompt accordingly. The examples are as follows:
            {examples}
            """;

    public static final String FEEDBACK_PROMPT_KEY = "feedback_prompt";

    /**
     * The template for the feedback example block.
     * This template is used to format each example of a misclassified trace link.
     * The placeholders {identifier}, {source_type}, {source_content}, {target_type}, {target_content}, and {classification}
     * will be replaced with specific values for each trace link.
     */
    private static final String FEEDBACK_EXAMPLE_BLOCK =
            """
            {identifier}
            {source_type}: '''{source_content}'''
            {target_type}: '''{target_content}'''
            Classification result: {classification}
            """;
    /**
     * The default number of feedback examples to include in the prompt.
     * This value determines how many misclassified trace links will be shown in the feedback prompt.
     */
    public static final int FEEDBACK_SIZE = 5;

    public static final String FEEDBACK_SIZE_KEY = "feedback_size";

    private final String feedbackPrompt;
    private final int feedbackSize;
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
            Scorer scorer) {
        super(configuration, goldStandard, aggregator, traceLinkIdPostProcessor, classifier, scorer);
        this.feedbackPrompt = configuration.argumentAsString(FEEDBACK_PROMPT_KEY, FEEDBACK_PROMPT_TEMPLATE);
        this.feedbackSize = configuration.argumentAsInt(FEEDBACK_SIZE_KEY, FEEDBACK_SIZE);
    }

    @Override
    protected AbstractPromptOptimizer copyOf(AbstractPromptOptimizer original) {
        throw new UnsupportedOperationException("This feature is not implemented yet.");
    }

    @Override
    protected String optimizeIntern(SourceElementStore sourceStore, TargetElementStore targetStore) {
        double[] f1Scores = new double[maximumIterations];
        Set<TraceLink> possibleTraceLinks = getReducedGoldStandardLinks(sourceStore, targetStore);
        int i = 0;
        double f1Score;
        Set<TraceLink> traceLinks;
        String modifiedPrompt = optimizationPrompt;
        do {
            logger.debug("Iteration {}: RequestPrompt = {}", i, modifiedPrompt);
            traceLinks = getTraceLinks(sourceStore, targetStore, modifiedPrompt);
            f1Score = evaluateF1(sourceStore, targetStore, modifiedPrompt);
            logger.debug("Iteration {}: F1-Score = {}", i, f1Score);
            f1Scores[i] = f1Score;
            String request = template.replace(ORIGINAL_PROMPT_KEY, optimizationPrompt);
            request = generateFeedbackPrompt(traceLinks, possibleTraceLinks, sourceStore, targetStore) + request;
            modifiedPrompt = cachedSanitizedRequest(request);
            optimizationPrompt = modifiedPrompt;
            i++;
        } while (i < maximumIterations && f1Score < THRESHOLD_F1_SCORE);
        logger.info("Iterations {}: F1-Scores = {}", i, f1Scores);
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
            logger.debug("Example {}: TraceLink {} was misclassified", count, traceLink);
            Element source = sourceStore.getById(traceLink.sourceId()).first();
            Element target = targetStore.getById(traceLink.targetId()).first();
            feedback.append(FEEDBACK_EXAMPLE_BLOCK
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
}
