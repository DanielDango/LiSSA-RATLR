/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import static edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier.CONFIG_NAME_SEPARATOR;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

/**
 * A preprocessor extracts elements based on the given artifacts.
 */
public abstract class Preprocessor {
    public static final String SEPARATOR = "$";

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    public abstract List<Element> preprocess(List<Artifact> artifacts);

    public static Preprocessor createPreprocessor(ModuleConfiguration configuration) {
        return switch (configuration.name().split(CONFIG_NAME_SEPARATOR)[0]) {
            case "sentence" -> new SentencePreprocessor(configuration);
            case "code" ->
                switch (configuration.name()) {
                    case "code_chunking" -> new CodeChunkingPreprocessor(configuration);
                    case "code_method" -> new CodeMethodPreprocessor(configuration);
                    case "code_tree" -> new CodeTreePreprocessor(configuration);
                    default ->
                        throw new IllegalArgumentException("Unsupported preprocessor name: " + configuration.name());
                };
            case "model" ->
                switch (configuration.name()) {
                    case "model_uml" -> new ModelUMLPreprocessor(configuration);
                    default ->
                        throw new IllegalArgumentException("Unsupported preprocessor name: " + configuration.name());
                };
            case "summarize" -> new SummarizePreprocessor(configuration);
            case "artifact" -> new SingleArtifactPreprocessor();
            default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }
}
