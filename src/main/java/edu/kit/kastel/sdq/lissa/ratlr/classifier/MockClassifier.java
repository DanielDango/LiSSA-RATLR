/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import java.util.Optional;

import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

/**
 * A mock classifier implementation that always returns a positive classification result.
 * This classifier is primarily used for testing and demonstration purposes.
 */
public class MockClassifier extends Classifier {
    /**
     * Creates a new mock classifier instance.
     * The classifier uses a single thread for processing.
     */
    public MockClassifier() {
        super(1);
    }

    /**
     * Always classifies any pair of elements as related with maximum confidence.
     * This method is used for testing and demonstration purposes.
     *
     * @param source The source element
     * @param target The target element
     * @return A classification result with maximum confidence (1.0)
     */
    @Override
    protected Optional<ClassificationResult> classify(Element source, Element target) {
        return Optional.of(ClassificationResult.of(source, target, 1.0));
    }

    @Override
    protected Classifier copyOf() {
        return new MockClassifier();
    }
}
