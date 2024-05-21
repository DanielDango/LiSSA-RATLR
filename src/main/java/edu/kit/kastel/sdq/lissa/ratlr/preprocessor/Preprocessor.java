package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

import java.util.List;

public abstract class Preprocessor {
    public abstract List<Element> preprocess(Artifact artifact);

    public static Preprocessor createPreprocessor(RatlrConfiguration.ModuleConfiguration configuration) {
        return switch (configuration.name()) {
        case "sentence" -> new SentencePreprocessor(configuration);
        default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }
}
