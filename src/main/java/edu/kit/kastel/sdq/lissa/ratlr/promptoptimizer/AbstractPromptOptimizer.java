package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /**
     * Runs the iterative optimization process.
     * This method should be implemented by subclasses to define the specific optimization logic.
     */
    public abstract String optimize(ElementStore sourceStore, ElementStore targetStore, String prompt);

    protected abstract AbstractPromptOptimizer copyOf(AbstractPromptOptimizer original);

    public static AbstractPromptOptimizer createOptimizer(ModuleConfiguration configuration) {
        return switch (configuration.name().split(CONFIG_NAME_SEPARATOR)[0]) {
            case "mock" -> new MockOptimizer();
            case "simple" -> new SimpleOptimizer(configuration);
            default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }
}
