/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.kastel.sdq.lissa.ratlr.artifactprovider.ArtifactProvider;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.Configuration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.embeddingcreator.EmbeddingCreator;
import edu.kit.kastel.sdq.lissa.ratlr.preprocessor.Preprocessor;
import edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.AbstractPromptOptimizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a single prompt optimization run of the LiSSA framework.
 * This class utilised part of the trace link analysis pipeline for a given configuration,
 * including:
 * <ul>
 *     <li>Artifact loading from source and target providers</li>
 *     <li>Preprocessing of artifacts into elements</li>
 *     <li>Embedding calculation for elements</li>
 * </ul>
 * <p>
 * The pipeline follows these steps:
 * <ol>
 *     <li>Load artifacts from configured providers</li>
 *     <li>Preprocess artifacts into elements</li>
 *     <li>Calculate embeddings for elements</li>
 *     <li>Build element stores for efficient access</li>
 *     <li>Optimizes the prompt</li>
 * </ol>
 */
public class Optimization {

    private static final Logger logger = LoggerFactory.getLogger(Optimization.class);
    private final Path configFile;

    private Configuration configuration;

    /**
     * Provider for source artifacts
     */
    private ArtifactProvider sourceArtifactProvider;
    /**
     * Provider for target artifacts
     */
    private ArtifactProvider targetArtifactProvider;
    /**
     * Preprocessor for source artifacts
     */
    private Preprocessor sourcePreprocessor;
    /**
     * Preprocessor for target artifacts
     */
    private Preprocessor targetPreprocessor;
    /**
     * Creator for element embeddings
     */
    private EmbeddingCreator embeddingCreator;
    /**
     * Store for source elements
     */
    private ElementStore sourceStore;
    /**
     * Store for target elements
     */
    private ElementStore targetStore;
    /**
     * Optimizer for prompt used in classification
     */
    private AbstractPromptOptimizer promptOptimizer;

    /**
     * Creates a new evaluation instance with the specified configuration file.
     * This constructor:
     * <ol>
     *     <li>Validates the configuration file path</li>
     *     <li>Loads and initializes the configuration</li>
     *     <li>Sets up all required components for the pipeline</li>
     * </ol>
     *
     * @param configFile Path to the configuration file
     * @throws IOException          If there are issues reading the configuration file
     * @throws NullPointerException If configFile is null
     */
    public Optimization(Path configFile) throws IOException {
        this.configFile = Objects.requireNonNull(configFile);
        setup();
    }

    /**
     * Sets up the evaluation pipeline components.
     * This method:
     * <ol>
     *     <li>Loads the configuration from file</li>
     *     <li>Initializes the cache manager</li>
     *     <li>Creates artifact providers</li>
     *     <li>Creates preprocessors</li>
     *     <li>Creates embedding creator</li>
     *     <li>Creates element stores</li>
     *     <li>Optimizes the prompt</li>
     * </ol>
     *
     * @throws IOException If there are issues reading the configuration
     */
    private void setup() throws IOException {
        configuration = new ObjectMapper().readValue(configFile.toFile(), Configuration.class);
        CacheManager.setCacheDir(configuration.cacheDir());

        sourceArtifactProvider = ArtifactProvider.createArtifactProvider(configuration.sourceArtifactProvider());
        targetArtifactProvider = ArtifactProvider.createArtifactProvider(configuration.targetArtifactProvider());

        sourcePreprocessor = Preprocessor.createPreprocessor(configuration.sourcePreprocessor());
        targetPreprocessor = Preprocessor.createPreprocessor(configuration.targetPreprocessor());

        embeddingCreator = EmbeddingCreator.createEmbeddingCreator(configuration.embeddingCreator());
        sourceStore = new ElementStore(configuration.sourceStore(), false);
        targetStore = new ElementStore(configuration.targetStore(), true);

        promptOptimizer = AbstractPromptOptimizer.createOptimizer(configuration.promptOptimizer());
        configuration.serializeAndDestroyConfiguration();
    }

    /**
     * Gets the configuration used for this evaluation.
     *
     * @return The configuration object
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Runs the complete trace link analysis pipeline.
     * This method:
     * <ol>
     *     <li>Loads artifacts from providers</li>
     *     <li>Preprocesses artifacts into elements</li>
     *     <li>Calculates embeddings for elements</li>
     *     <li>Builds element stores</li>
     *     <li>Optimizes the prompt</li>
     * </ol>
     *
     * @return Set of identified trace links
     */
    public String run() {
        logger.info("Loading artifacts");
        var sourceArtifacts = sourceArtifactProvider.getArtifacts();
        var targetArtifacts = targetArtifactProvider.getArtifacts();

        logger.info("Preprocessing artifacts");
        var sourceElements = sourcePreprocessor.preprocess(sourceArtifacts);
        var targetElements = targetPreprocessor.preprocess(targetArtifacts);

        logger.info("Calculating embeddings");
        var sourceEmbeddings = embeddingCreator.calculateEmbeddings(sourceElements);
        var targetEmbeddings = embeddingCreator.calculateEmbeddings(targetElements);

        logger.info("Building element stores");
        sourceStore.setup(sourceElements, sourceEmbeddings);
        targetStore.setup(targetElements, targetEmbeddings);

        logger.info("Optimizing Prompt");
        String result = promptOptimizer.optimize(sourceStore, targetStore, "Question: Here are two parts of software development artifacts.\n\n            {source_type}: '''{source_content}'''\n\n            {target_type}: '''{target_content}'''\n            Are they related?\n\n            Answer with 'yes' or 'no'.");
        logger.info("Optimized Prompt: {}", result);
        return result;
    }
}
