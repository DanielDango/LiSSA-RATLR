/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.GoldStandardConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;

public abstract class AbstractPromptOptimizer {
    /**
     * Separator used in configuration names.
     */
    public static final String CONFIG_NAME_SEPARATOR = "_";

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    protected final int threads;

    protected AbstractPromptOptimizer(int threads) {
        this.threads = Math.max(1, threads);
    }

    public static AbstractPromptOptimizer createOptimizer(
            ModuleConfiguration configuration, GoldStandardConfiguration goldStandard) {
        if (configuration == null) {
            return new MockOptimizer();
        }
        return switch (configuration.name().split(CONFIG_NAME_SEPARATOR)[0]) {
            case "mock" -> new MockOptimizer();
            case "simple" -> new SimpleOptimizer(configuration);
            case "iterative" -> new IterativeOptimizer(configuration, goldStandard);
            default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }

    /**
     * Runs the iterative optimization process.
     * This method should be implemented by subclasses to define the specific optimization logic.
     */
    public abstract String optimize(ElementStore sourceStore, ElementStore targetStore, String prompt);

    protected abstract AbstractPromptOptimizer copyOf(AbstractPromptOptimizer original);
}
