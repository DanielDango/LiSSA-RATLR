package edu.kit.kastel.sdq.lissa.ratlr;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.kastel.sdq.lissa.ratlr.artifactprovider.ArtifactProvider;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.embeddingcreator.EmbeddingCreator;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.preprocessor.Preprocessor;
import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final int GROUND_TRUTH_INDEX = 0;

    public static void main(String[] args) throws IOException {
        RatlrConfiguration configuration = new ObjectMapper().readValue(new File("config.json"), RatlrConfiguration.class);

        ArtifactProvider sourceArtifactProvider = ArtifactProvider.createArtifactProvider(configuration.sourceArtifactProvider());
        ArtifactProvider targetArtifactProvider = ArtifactProvider.createArtifactProvider(configuration.targetArtifactProvider());

        Preprocessor sourcePreprocessor = Preprocessor.createPreprocessor(configuration.sourcePreprocessor());
        Preprocessor targetPreprocessor = Preprocessor.createPreprocessor(configuration.targetPreprocessor());

        EmbeddingCreator embeddingCreator = EmbeddingCreator.createEmbeddingCreator(configuration.embeddingCreator());
        Classifier classifier = Classifier.createClassifier(configuration.classifier());
        ResultAggregator aggregator = ResultAggregator.createResultAggregator(configuration.resultAggregator());

        // RUN
        logger.info("Loading artifacts");
        var sourceArtifacts = sourceArtifactProvider.getArtifacts();
        var targetArtifacts = targetArtifactProvider.getArtifacts();

        logger.info("Preprocessing artifacts");
        var sourceElements = sourceArtifacts.stream().map(sourcePreprocessor::preprocess).flatMap(Collection::stream).toList();
        var targetElements = targetArtifacts.stream().map(targetPreprocessor::preprocess).flatMap(Collection::stream).toList();

        logger.info("Calculating embeddings");
        var sourceEmbeddings = embeddingCreator.calculateEmbeddings(sourceElements);
        var targetEmbeddings = embeddingCreator.calculateEmbeddings(targetElements);

        logger.info("Building element stores");
        var sourceStore = new ElementStore(configuration.sourceStore(), sourceElements, sourceEmbeddings);
        var targetStore = new ElementStore(configuration.targetStore(), targetElements, targetEmbeddings);

        logger.info("Classifying Tracelinks");
        var llmResults = classifier.classify(sourceStore, targetStore);
        var traceLinks = aggregator.aggregate(llmResults);

        logger.info("Evaluating Results");
        generateStatistics(args, traceLinks);
    }

    private static void generateStatistics(String[] args, Set<TraceLink> traceLinks) throws IOException {
        File groundTruth = new File(args[GROUND_TRUTH_INDEX]);
        Set<TraceLink> validTraceLinks = Files.readAllLines(groundTruth.toPath())
                .stream()
                .map(l -> l.split(","))
                .map(it -> new TraceLink(it[0] + ".txt", it[1] + ".java"))
                .collect(Collectors.toSet());
        logger.info("Valid TraceLinks: {}", validTraceLinks.size());
        logger.info("Found TraceLinks: {}", traceLinks.size());

        Set<TraceLink> truePositives = traceLinks.stream().filter(validTraceLinks::contains).collect(Collectors.toSet());
        Set<TraceLink> falsePositives = traceLinks.stream().filter(it -> !validTraceLinks.contains(it)).collect(Collectors.toSet());
        Set<TraceLink> falseNegatives = validTraceLinks.stream().filter(it -> !traceLinks.contains(it)).collect(Collectors.toSet());

        logger.info("True Positives: {}", truePositives.size());
        logger.info("False Positives: {}", falsePositives.size());
        logger.info("False Negatives: {}", falseNegatives.size());

        double precision = (double) truePositives.size() / (truePositives.size() + falsePositives.size());
        double recall = (double) truePositives.size() / (truePositives.size() + falseNegatives.size());
        double f1 = 2 * precision * recall / (precision + recall);
        logger.info("Precision: {}", precision);
        logger.info("Recall: {}", recall);
        logger.info("F1: {}", f1);
    }
}
