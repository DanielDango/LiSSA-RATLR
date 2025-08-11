/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.PROMPT_END;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.SimpleOptimizer.PROMPT_START;

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

public abstract class AbstractPromptOptimizer {
    /**
     * Separator used in configuration names.
     */
    public static final String CONFIG_NAME_SEPARATOR = "_";

    protected static final String PROMPT_OPTIMIZATION_TEMPLATE_KEY = "optimization_template";
    protected static final String PROMPT_KEY = "prompt";

    private static final Logger staticLogger = LoggerFactory.getLogger(AbstractPromptOptimizer.class);
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    protected final int threads;

    protected AbstractPromptOptimizer(int threads) {
        this.threads = Math.max(1, threads);
    }

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
            default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }

    /**
     * Runs the iterative optimization process.
     * This method should be implemented by subclasses to define the specific optimization logic.
     */
    public abstract String optimize(ElementStore sourceStore, ElementStore targetStore);

    protected abstract AbstractPromptOptimizer copyOf(AbstractPromptOptimizer original);

    protected static String extractPromptFromResponse(String response) {
        Pattern pattern = Pattern.compile(
                (PROMPT_START + "((?s).*?)" + PROMPT_END).replace("/", "\\/"), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            response = matcher.group(1).strip();
        } else {
            staticLogger.warn("No prompt found in response: {}", response);
        }
        return response;
    }
}
