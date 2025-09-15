/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import java.util.Set;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.evaluator.AbstractEvaluator;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.postprocessor.TraceLinkIdPostprocessor;
import edu.kit.kastel.sdq.lissa.ratlr.promptmetric.Metric;
import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;

/**
 * Factory class for creating instances of AbstractPromptOptimizer based on the provided configuration.
 * This class uses the factory design pattern to encapsulate the instantiation logic for different
 * prompt optimizer implementations.
 */
public final class OptimizerFactory {

    /**
     * Separator used in configuration names.
     */
    public static final String CONFIG_NAME_SEPARATOR = "_";

    private OptimizerFactory() {
        throw new IllegalAccessError("Factory class should not be instantiated.");
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
            Metric metric,
            AbstractEvaluator evaluator) {
        if (configuration == null) {
            return new MockOptimizer();
        }
        return switch (configuration.name().split(CONFIG_NAME_SEPARATOR)[0]) {
            case "mock" -> new MockOptimizer();
            case "simple" -> new SimpleOptimizer(configuration);
            case "iterative" -> new IterativeOptimizer(configuration, goldStandard, metric);
            case "feedback" ->
                new IterativeFeedbackOptimizer(
                        configuration, goldStandard, aggregator, traceLinkIdPostProcessor, classifier, metric);
            case "gradient" -> new AutomaticPromptOptimizer(configuration, goldStandard, classifier, metric, evaluator);
            default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }
}
