/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptmetric.scorer;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;

/**
 * Factory class for creating Scorer instances based on provided configurations.
 * This class should not be instantiated; it provides a static method to create
 * Scorer objects.
 */
public final class ScorerFactory {

    private ScorerFactory() {
        throw new IllegalAccessError("Factory class should not be instantiated.");
    }

    /**
     * Creates a Scorer instance based on the provided configuration.
     *
     * @param configuration The module configuration specifying the type of scorer to create.
     * @return A Scorer instance as specified by the configuration.
     * @throws IllegalStateException If the configuration name does not match any known scorer types.
     */
    public static Scorer createScorer(ModuleConfiguration configuration) {
        return switch (configuration.name()) {
            case "binary" -> new BinaryScorer(configuration);
            default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }
}
