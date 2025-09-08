/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;

public class MockOptimizer extends AbstractPromptOptimizer {
    /**
     * The prompt used for optimization.
     * This is the initial prompt that will not be optimized.
     */
    private final String optimizationPrompt;

    public MockOptimizer() {
        super(1);
        this.optimizationPrompt = "";
    }

    @Override
    public String optimize(ElementStore sourceStore, ElementStore targetStore) {
        return optimizationPrompt;
    }

    @Override
    protected AbstractPromptOptimizer copyOf(AbstractPromptOptimizer original) {
        return new MockOptimizer();
    }
}
