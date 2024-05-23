package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

import java.util.ArrayList;
import java.util.List;

public abstract class Classifier {

    public List<ClassificationResult> classify(ElementStore sourceStore, ElementStore targetStore) {
        List<ClassificationResult> results = new ArrayList<>();
        for (var query : sourceStore.getAllElements(true)) {
            var targetCandidates = targetStore.findSimilar(query.second());
            var classificationResult = classify(query.first(), targetCandidates);
            results.add(classificationResult);
        }
        return results;
    }

    protected abstract ClassificationResult classify(Element source, List<Element> targets);

    public static Classifier createClassifier(RatlrConfiguration.ModuleConfiguration configuration) {
        return switch (configuration.name()) {
        case "simple_ollama" -> new SimpleOllamaClassifier(configuration);
        default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }

    public record ClassificationResult(Element source, List<Element> targets) {
    }
}
