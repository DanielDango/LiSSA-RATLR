package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;

public class MockOptimizer extends AbstractPromptOptimizer {
    public MockOptimizer() {
        super(1);
    }


    @Override
    public String optimize(ElementStore sourceStore, ElementStore targetStore, String prompt) {
        return prompt;
    }

    @Override
    protected AbstractPromptOptimizer copyOf(AbstractPromptOptimizer original) {
        return new MockOptimizer();
    }
}
