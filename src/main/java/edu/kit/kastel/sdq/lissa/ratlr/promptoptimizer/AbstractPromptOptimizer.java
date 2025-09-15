/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.sdq.lissa.ratlr.elementstore.SourceElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.TargetElementStore;

/**
 * Abstract base class for prompt optimizers in the LiSSA framework.
 * This class provides the foundation for implementing different prompt optimization strategies
 * for trace link analysis.
 */
public abstract class AbstractPromptOptimizer {
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
     * Runs the optimization process.
     * This method should be implemented by subclasses to define the specific optimization logic.
     *
     * @param sourceStore The store containing source elements of the domain/dataset the prompt is optimized for
     * @param targetStore The store containing target elements of the domain/dataset the prompt is optimized for
     * @return A string representing the optimized prompt
     */
    public abstract String optimize(SourceElementStore sourceStore, TargetElementStore targetStore);
}
