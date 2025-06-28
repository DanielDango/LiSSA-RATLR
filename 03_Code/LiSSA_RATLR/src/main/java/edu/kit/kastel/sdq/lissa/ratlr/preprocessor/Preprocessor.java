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
 * Abstract base class for preprocessors that extract elements from artifacts.
 * Preprocessors are responsible for breaking down artifacts into smaller, more
 * manageable elements that can be used for trace link analysis.
 *
 * The class supports various types of preprocessors:
 * <ul>
 *     <li>sentence: Breaks down text into sentences</li>
 *     <li>code: Processes source code in different ways:
 *         <ul>
 *             <li>code_chunking: Splits code into chunks</li>
 *             <li>code_method: Extracts methods from code</li>
 *             <li>code_tree: Processes code using a tree structure</li>
 *         </ul>
 *     </li>
 *     <li>model: Processes model artifacts:
 *         <ul>
 *             <li>model_uml: Processes UML models</li>
 *         </ul>
 *     </li>
 *     <li>summarize: Creates summaries of artifacts</li>
 *     <li>artifact: Processes single artifacts without breaking them down</li>
 * </ul>
 *
 * Each preprocessor type is created based on the module configuration and
 * implements its own strategy for extracting elements from artifacts.
 */
public abstract class Preprocessor {
    /** Separator used in element identifiers */
    public static final String SEPARATOR = "$";

    /** Logger instance for this preprocessor */
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Preprocesses a list of artifacts to extract elements.
     * The specific extraction strategy is implemented by each preprocessor subclass.
     *
     * @param artifacts The list of artifacts to preprocess
     * @return A list of elements extracted from the artifacts
     */
    public abstract List<Element> preprocess(List<Artifact> artifacts);

    /**
     * Creates a preprocessor instance based on the module configuration.
     * The type of preprocessor is determined by the first part of the configuration name
     * (before the separator) and, for some types, the full configuration name.
     *
     * @param configuration The module configuration specifying the type of preprocessor
     * @return A new preprocessor instance
     * @throws IllegalArgumentException if the preprocessor name is not supported
     * @throws IllegalStateException if the configuration name is not recognized
     */
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
