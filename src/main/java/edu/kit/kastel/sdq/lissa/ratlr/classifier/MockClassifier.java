/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import java.util.Optional;

import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

public class MockClassifier extends Classifier {
    public MockClassifier() {
        super(1);
    }

    @Override
    protected Optional<ClassificationResult> classify(Element source, Element target) {
        return Optional.of(ClassificationResult.of(source, target, 1.0));
    }

    @Override
    protected Classifier copyOf() {
        return new MockClassifier();
    }
}
