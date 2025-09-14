/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptmetric;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;

/**
 * Factory class for creating scorer instances based on the provided configuration.
 */
public final class MetricFactory {

    private MetricFactory() {
        throw new IllegalAccessError("Factory class should not be instantiated.");
    }

    /**
     * Factory method to create a scorer based on the provided configuration.
     * The name field indicates the type of scorer to create.
     * If the configuration is null, a MockMetric is returned by default.
     *
     * @param configuration The configuration specifying the type of scorer to create.
     * @param classifier The classifier to be used by the scorer.
     * @return An instance of a concrete scorer implementation.
     * @throws IllegalStateException If the configuration name does not match any known scorer types.
     */
    public static Metric createScorer(
            ModuleConfiguration configuration, Classifier classifier, ResultAggregator aggregator) {
        if (configuration == null) {
            return new MockMetric();
        }
        return switch (configuration.name()) {
            case "mock" -> new MockMetric();
            case "binary" -> new PointwiseMetric(configuration, classifier);
            case "fBeta" -> new FBetaMetric(configuration, classifier, aggregator);
            default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }
}
