package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import edu.kit.kastel.sdq.lissa.ratlr.Configuration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class Classifier {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

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

    public static Classifier createClassifier(Configuration.ModuleConfiguration configuration) {
        return switch (configuration.name()) {
        case "mock" -> new MockClassifier();
        case "simple_ollama" -> new SimpleOllamaClassifier(configuration);
        case "simple_openai" -> new SimpleOpenAiClassifier(configuration);
        case "reasoning_ollama" -> new ReasoningOllamaClassifier(configuration);
        default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }

    public record ClassificationResult(Element source, List<Element> targets) {
    }
}
