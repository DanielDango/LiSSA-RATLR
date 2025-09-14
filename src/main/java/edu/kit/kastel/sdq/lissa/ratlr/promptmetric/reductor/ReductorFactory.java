/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptmetric.reductor;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;

/**
 * Factory class for creating Reductor instances based on the provided name.
 */
public final class ReductorFactory {

    private ReductorFactory() {
        throw new IllegalAccessError("Factory class should not be instantiated.");
    }

    /**
     * Factory method to create a Reductor based on the provided configuration.
     * The name field indicates the type of Reductor to create.
     *
     * @param configuration The configuration specifying the type of Reductor to create.
     * @return An instance of a concrete Reductor implementation.
     * @throws IllegalStateException If the configuration name does not match any known Reductor types.
     */
    public static Reductor createReductor(ModuleConfiguration configuration) {
        return switch (configuration.name()) {
            case "mean" -> new MeanReductor();
            default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }
}
