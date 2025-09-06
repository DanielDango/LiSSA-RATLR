/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.evaluator.AbstractEvaluator;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.postprocessor.TraceLinkIdPostprocessor;
import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;
import edu.kit.kastel.sdq.lissa.ratlr.scorer.AbstractScorer;

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

    protected static final Logger staticLogger = LoggerFactory.getLogger(AbstractPromptOptimizer.class);

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
            Classifier classifier,
            AbstractScorer scorer,
            AbstractEvaluator evaluator) {
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
                        configuration,
                        goldStandard,
                        aggregator,
                        traceLinkIdPostProcessor,
                        classifier,
                        scorer,
                        evaluator);
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
     * @deprecated Use {@link #parseTaggedTextFirst(String, String, String)} instead.
     */
    @Deprecated(forRemoval = true)
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

        String result = parseTaggedTextFirst(prompt, PROMPT_START, PROMPT_END);
        if (!result.equals(prompt)) {
            staticLogger.warn("parseTaggedTextSingle found a different prompt than extractPromptFromResponse.");
        }
        return result;
    }

    /**
     * Parses and extracts a single substring from the input text that is enclosed between the specified start and end tags.
     * If multiple tagged substrings are found, a warning is logged and the first one is returned.
     * If no tagged substrings are found, a warning is logged and the original text is returned.
     *
     * @param text     The input text to parse
     * @param startTag The starting tag to look for
     * @param endTag   The ending tag to look for
     * @return         The extracted substring found between the specified tags, or the original text if none are found
     */
    protected static String parseTaggedTextFirst(String text, String startTag, String endTag) {
        List<String> taggedTexts = parseTaggedText(text, startTag, endTag);
        if (taggedTexts.size() > 1) {
            staticLogger.warn("Multiple tagged texts found, using the first one.");
        }
        if (taggedTexts.isEmpty()) {
            staticLogger.warn("No tagged text found, returning the original text.");
        }
        return parseTaggedText(text, startTag, endTag).stream().findFirst().orElse(text);
    }

    /**
     * Parses and extracts all substrings from the input text that are enclosed between the specified start and end tags.
     * The method uses regular expressions to identify and extract the tagged substrings, allowing for multi-line
     * content and case-insensitive matching of the tags.
     *
     * @param text     The input text to parse
     * @param startTag The starting tag to look for
     * @param endTag   The ending tag to look for
     * @return         A list possibly empty list of extracted substrings found between the specified tags
     */
    protected static List<String> parseTaggedText(String text, String startTag, String endTag) {
        List<String> texts = new ArrayList<>();

        Pattern pattern = Pattern.compile(startTag + "(.*?)" + endTag, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            texts.add(matcher.group(1));
        }
        return texts;
    }

    /**
     * Sanitizes the given prompt string by removing leading and trailing quotes and whitespace.
     *
     * @param prompt The prompt string to sanitize
     * @return The prompt string without leading/trailing quotes and whitespace
     */
    protected static String sanitizePrompt(String prompt) {
        // Remove leading and trailing quotes and whitespace
        return prompt.trim().replaceAll("((^[\"']+)|([\"']+$))", "").trim();
    }
}
