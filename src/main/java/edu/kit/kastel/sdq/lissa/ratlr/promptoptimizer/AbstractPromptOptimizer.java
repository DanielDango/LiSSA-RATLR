/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.postprocessor.TraceLinkIdPostprocessor;
import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;

/**
 * Abstract base class for prompt optimizers in the LiSSA framework.
 * This class provides the foundation for implementing different prompt optimization strategies
 * for trace link analysis.
 */
public abstract class AbstractPromptOptimizer {
    /**
     * Separator used in configuration names.
     */
    public static final String CONFIG_NAME_SEPARATOR = "_";
    /**
     * Start marker for the prompt in the optimization template.
     */
    public static final String PROMPT_START = "<prompt>";
    /**
     * End marker for the prompt in the optimization template.
     */
    public static final String PROMPT_END = "</prompt>";

    /**
     * Key for the prompt optimization template in the configuration.
     * This key is used to retrieve the prompt optimization template from the configuration.
     */
    protected static final String PROMPT_OPTIMIZATION_TEMPLATE_KEY = "optimization_template";
    /**
     * Key for the original prompt in the configuration.
     * This key is used to retrieve the original prompt from the configuration.
     */
    protected static final String PROMPT_KEY = "prompt";

    private static final Logger staticLogger = LoggerFactory.getLogger(AbstractPromptOptimizer.class);

    /**
     * Logger for the prompt optimizer.
     */
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * The number of threads to use for parallel processing.
     * This value is initialized in the constructor and must be at least 1.
     */
    protected final int threads;

    /**
     * Creates a new AbstractPromptOptimizer with the specified number of threads.
     * This constructor initializes the optimizer with a minimum of one thread.
     *
     * @param threads The number of threads to use for parallel processing
     */
    protected AbstractPromptOptimizer(int threads) {
        this.threads = Math.max(1, threads);
    }

    /**
     * Factory method to create an instance of AbstractPromptOptimizer based on the provided configuration.
     * This method uses the configuration name to determine which specific optimizer implementation to instantiate.
     *
     * @param configuration The configuration for the optimizer
     * @param goldStandard The gold standard trace links for evaluation
     * @param aggregator The result aggregator for collecting optimization results
     * @param traceLinkIdPostProcessor Postprocessor for trace link IDs
     * @param classifier The classifier used in the optimization process
     * @return An instance of AbstractPromptOptimizer based on the configuration
     */
    public static AbstractPromptOptimizer createOptimizer(
            ModuleConfiguration configuration,
            Set<TraceLink> goldStandard,
            ResultAggregator aggregator,
            TraceLinkIdPostprocessor traceLinkIdPostProcessor,
            Classifier classifier) {
        if (configuration == null) {
            return new MockOptimizer();
        }
        return switch (configuration.name().split(CONFIG_NAME_SEPARATOR)[0]) {
            case "mock" -> new MockOptimizer();
            case "simple" -> new SimpleOptimizer(configuration);
            case "iterative" ->
                new IterativeOptimizer(configuration, goldStandard, aggregator, traceLinkIdPostProcessor, classifier);
            case "feedback" ->
                new IterativeFeedbackOptimizer(
                        configuration, goldStandard, aggregator, traceLinkIdPostProcessor, classifier);
            case "gradient" ->
                new AutomaticPromptOptimizer(
                        configuration, goldStandard, aggregator, traceLinkIdPostProcessor, classifier);
            default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }

    /**
     * Runs the optimization process.
     * This method should be implemented by subclasses to define the specific optimization logic.
     *
     * @param sourceStore The store containing source elements of the domain/dataset the prompt is optimized for
     * @param targetStore The store containing target elements of the domain/dataset the prompt is optimized for
     * @return A string representing the optimized prompt
     */
    public abstract String optimize(ElementStore sourceStore, ElementStore targetStore);

    /**
     * Creates a copy of the current optimizer instance.
     * This method is used to create a new instance with the same configuration as the original.
     *
     * @param original The original optimizer instance to copy
     * @return A new instance of the optimizer with the same configuration
     */
    protected abstract AbstractPromptOptimizer copyOf(AbstractPromptOptimizer original);

    /**
     * Extracts the prompt from the response string and removes any surrounding quotes.
     * The prompt is expected to be enclosed between PROMPT_START and PROMPT_END markers.
     *
     * @param response The response string containing the prompt
     * @return The extracted prompt, or an empty string if no prompt is found
     */
    protected static String extractPromptFromResponse(String response) {
        String prompt = response;
        Pattern pattern = Pattern.compile(
                (PROMPT_START + "((?s).*?)" + PROMPT_END).replace("/", "\\/"), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            prompt = matcher.group(1).strip().replaceAll("((^[\"']+)|([\"']+$))", "");
        } else {
            staticLogger.warn("No prompt found in response: {}", response);
        }
        return prompt;
    }
}
