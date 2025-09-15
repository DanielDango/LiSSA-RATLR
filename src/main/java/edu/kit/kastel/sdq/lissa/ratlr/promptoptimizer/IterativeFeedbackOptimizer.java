/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.promptmetric.Metric;
import edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.samplestrategy.OrderedFirstSampler;
import edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.samplestrategy.SampleStrategy;

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

    private final SampleStrategy sampleStrategy;

    private final String feedbackPrompt;
    private final String feedbackExampleBlock;
    private final int feedbackSize;
    /**
     * Creates a new instance of the iterative feedback optimizer.
     * This optimizer uses iterative feedback to refine the prompt based on classification results.
     *
     * @param configuration The module configuration containing optimizer settings
     * @param goldStandard The set of trace links that represent the gold standard for evaluation
     */
    public IterativeFeedbackOptimizer(ModuleConfiguration configuration, Set<TraceLink> goldStandard, Metric metric) {
        super(configuration, goldStandard, metric);
        this.feedbackPrompt =
                configuration.argumentAsString(FEEDBACK_PROMPT_CONFIGURATION_KEY, FEEDBACK_PROMPT_TEMPLATE);
        this.feedbackSize = configuration.argumentAsInt(FEEDBACK_SIZE_CONFIGURATION_KEY, FEEDBACK_SIZE);
        this.feedbackExampleBlock = configuration.argumentAsString(
                FEEDBACK_EXAMPLE_BLOCK_CONFIGURATION_KEY, DEFAULT_FEEDBACK_EXAMPLE_BLOCK);
        this.sampleStrategy = new OrderedFirstSampler();
    }

    @Override
    protected String optimizeIntern(List<ClassificationTask> examples) {
        double[] promptScores = new double[maximumIterations];
        int i = 0;
        double promptScore;
        String modifiedPrompt = optimizationPrompt;
        do {
            LOGGER.debug("Iteration {}: RequestPrompt = {}", i, modifiedPrompt);
            promptScore = this.metric.getMetric(modifiedPrompt, examples);
            LOGGER.debug("Iteration {}: {} = {}", i, this.metric.getName(), promptScore);
            promptScores[i] = promptScore;
            String request = generateOptimizationPrompt(modifiedPrompt);
            if (feedbackSize > 0) {
                request = generateFeedbackPrompt(modifiedPrompt, examples) + request;
            }
            modifiedPrompt = cachedSanitizedRequest(request);
            i++;
        } while (i < maximumIterations && promptScore < thresholdScore);
        LOGGER.info("Iterations {}: {}s = {}", i, this.metric.getName(), promptScores);
        return modifiedPrompt;
    }

    /**
     * Fills the feedback prompt with examples of misclassified trace links using the FEEDBACK_EXAMPLE_BLOCK template.
     *
     * @param prompt The prompt for which to generate feedback
     * @param tasks  The classification tasks on which the prompt should be evaluated
     * @return a formatted feedback prompt containing examples of misclassified trace links
     */
    private String generateFeedbackPrompt(String prompt, Collection<ClassificationTask> tasks) {
        StringBuilder feedback = new StringBuilder();

        Set<ClassificationTask> misclassifiedTasks = getMisclassifiedTasks(prompt, tasks);
        List<ClassificationTask> sampledTasks = sampleStrategy.sample(misclassifiedTasks, feedbackSize);
        int exampleNumber = 1;
        for (ClassificationTask task : sampledTasks) {
            LOGGER.debug("Misclassified TraceLink ({} -> {})", task.source(), task.target());
            feedback.append(generateMisclassifiedFeedbackBlock(task).replace("{identifier}", exampleNumber + "."));
            exampleNumber++;
        }
        return feedbackPrompt.replace("{examples}", feedback.toString());
    }

    /**
     * Identifies misclassified tasks based on the current prompt.
     * A task is considered misclassified based on the {@link #classifiedAsTraceLink(String, ClassificationTask)}
     * method.
     *
     * @param prompt The prompt used for classification
     * @param tasks  The collection of classification tasks to evaluate
     * @return A set of misclassified classification tasks
     */
    private Set<ClassificationTask> getMisclassifiedTasks(String prompt, Collection<ClassificationTask> tasks) {
        Set<ClassificationTask> misclassifiedTasks = new HashSet<>();
        for (ClassificationTask task : tasks) {
            if (!classifiedAsTraceLink(prompt, task)) {
                misclassifiedTasks.add(task);
            }
        }
        return misclassifiedTasks;
    }

    /**
     * Generates a feedback block for a misclassified trace link using the feedback example block template.
     * The placeholders in the template are replaced with the corresponding values from the classification task.
     * As this method is for misclassified trace links, the classification result is implicitly the inverted value of the
     * task label (correct classification).
     *
     * @param task The classification task that was misclassified
     * @return A formatted feedback block for the misclassified trace link
     */
    private String generateMisclassifiedFeedbackBlock(ClassificationTask task) {
        return feedbackExampleBlock
                .replace("{source_type}", task.source().getType())
                .replace("{target_type}", task.target().getType())
                .replace("{source_content}", task.source().getContent())
                .replace("{target_content}", task.target().getContent())
                .replace("{classification}", task.label() ? "No" : "Yes");
    }
}
