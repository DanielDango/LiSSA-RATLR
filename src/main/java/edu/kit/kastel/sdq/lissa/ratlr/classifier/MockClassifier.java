package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

import java.util.List;

public class MockClassifier extends Classifier {
    @Override
    protected ClassificationResult classify(Element source, List<Element> targets) {
        return new ClassificationResult(source, targets);
    }
}
