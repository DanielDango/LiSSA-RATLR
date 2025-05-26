/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

public class MockClassifier extends Classifier {
    public MockClassifier() {
        super(1);
    }

    @Override
    protected ClassificationResult classify(Element source, Element target) {
        return ClassificationResult.of(source, target, 1.0);
    }

    @Override
    protected Classifier copyOf() {
        return new MockClassifier();
    }
}
