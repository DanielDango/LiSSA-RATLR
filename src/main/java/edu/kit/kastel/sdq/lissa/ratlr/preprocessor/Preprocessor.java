package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

import java.util.List;

public abstract class Preprocessor {
    public static final String SEPARATOR = "$";

    public abstract List<Element> preprocess(Artifact artifact);

    public static Preprocessor createPreprocessor(RatlrConfiguration.ModuleConfiguration configuration) {
        return switch (configuration.name()) {
        case "sentence" -> new SentencePreprocessor(configuration);
        case "code_chunking" -> new CodeChunkingPreprocessor(configuration);
        case "code_method" -> new CodeMethodPreprocessor(configuration);
        case "model_uml" -> new ModelUMLPreprocessor(configuration);
        default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }
}
