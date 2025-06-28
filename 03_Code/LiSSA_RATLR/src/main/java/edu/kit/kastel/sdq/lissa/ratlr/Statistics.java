/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.mcse.ardoco.metrics.ClassificationMetricsCalculator;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.Configuration;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.GoldStandardConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;

/**
 * Utility class for generating and saving statistics about trace link analysis results.
 * This class provides functionality to:
 * <ul>
 *     <li>Calculate classification metrics (precision, recall, F1)</li>
 *     <li>Generate detailed statistics reports</li>
 *     <li>Save trace links to CSV files</li>
 *     <li>Compare results against gold standards</li>
 * </ul>
 *
 * The statistics include:
 * <ul>
 *     <li>Number of trace links in gold standard</li>
 *     <li>Number of source and target artifacts</li>
 *     <li>True positives, false positives, and false negatives</li>
 *     <li>Precision, recall, and F1 scores</li>
 * </ul>
 */
public final class Statistics {
    private static final Logger logger = LoggerFactory.getLogger(Statistics.class);

    private Statistics() {
        throw new IllegalAccessError("Utility class");
    }

    /**
     * Generates statistics for trace link analysis results.
     * This method:
     * <ol>
     *     <li>Loads the gold standard trace links</li>
     *     <li>Calculates classification metrics</li>
     *     <li>Generates a detailed report</li>
     *     <li>Saves the report to a markdown file</li>
     * </ol>
     *
     * @param traceLinks Set of identified trace links
     * @param configFile Configuration file used for the analysis
     * @param configuration Configuration object used for the analysis
     * @param sourceArtifacts Number of source artifacts
     * @param targetArtifacts Number of target artifacts
     * @throws UncheckedIOException If there are issues writing the statistics file
     */
    public static void generateStatistics(
            Set<TraceLink> traceLinks,
            File configFile,
            Configuration configuration,
            int sourceArtifacts,
            int targetArtifacts)
            throws UncheckedIOException {
        generateStatistics(
                configuration.getConfigurationIdentifierForFile(configFile.getName()),
                configuration.serializeAndDestroyConfiguration(),
                traceLinks,
                configuration.goldStandardConfiguration(),
                sourceArtifacts,
                targetArtifacts);
    }

    /**
     * Generates statistics for trace link analysis results with custom configuration identifier.
     * This method:
     * <ol>
     *     <li>Validates the gold standard configuration</li>
     *     <li>Loads valid trace links from the gold standard</li>
     *     <li>Calculates classification metrics</li>
     *     <li>Generates a detailed report with configuration and results</li>
     *     <li>Saves the report to a markdown file</li>
     * </ol>
     *
     * @param configurationIdentifier Unique identifier for the configuration
     * @param configurationSummary Summary of the configuration used
     * @param traceLinks Set of identified trace links
     * @param goldStandardConfiguration Gold standard configuration for comparison
     * @param sourceArtifacts Number of source artifacts
     * @param targetArtifacts Number of target artifacts
     * @throws UncheckedIOException If there are issues writing the statistics file
     */
    public static void generateStatistics(
            String configurationIdentifier,
            String configurationSummary,
            Set<TraceLink> traceLinks,
            GoldStandardConfiguration goldStandardConfiguration,
            int sourceArtifacts,
            int targetArtifacts)
            throws UncheckedIOException {

        if (goldStandardConfiguration == null || goldStandardConfiguration.path() == null) {
            logger.info(
                    "Skipping statistics generation since no path to ground truth has been provided as first command line argument");
            return;
        }

        Set<TraceLink> validTraceLinks = getTraceLinksFromGoldStandard(goldStandardConfiguration);

        ClassificationMetricsCalculator cmc = ClassificationMetricsCalculator.getInstance();
        var classification = cmc.calculateMetrics(traceLinks, validTraceLinks, null);
        classification.prettyPrint();

        // Store information to one file (config and results)
        var resultFile = new File("results-" + configurationIdentifier + ".md");
        StringBuilder result = new StringBuilder();
        result.append("## Configuration (")
                .append(new SimpleDateFormat("yyyy-MM-dd_HH-mmZZZ").format(new Date()))
                .append(" -- ")
                .append(configurationIdentifier)
                .append(")\n```json\n")
                .append(configurationSummary)
                .append("\n```\n\n");
        result.append("## Stats\n");
        result.append("* #TraceLinks (GS): ").append(validTraceLinks.size()).append("\n");
        result.append("* #Source Artifacts: ").append(sourceArtifacts).append("\n");
        result.append("* #Target Artifacts: ").append(targetArtifacts).append("\n");
        result.append("## Results\n");
        result.append("* True Positives: ")
                .append(classification.getTruePositives().size())
                .append("\n");
        result.append("* False Positives: ")
                .append(classification.getFalsePositives().size())
                .append("\n");
        result.append("* False Negatives: ")
                .append(classification.getFalseNegatives().size())
                .append("\n");
        result.append("* Precision: ").append(classification.getPrecision()).append("\n");
        result.append("* Recall: ").append(classification.getRecall()).append("\n");
        result.append("* F1: ").append(classification.getF1()).append("\n");

        logger.info("Storing results to {}", resultFile.getName());
        try {
            Files.writeString(resultFile.toPath(), result.toString(), StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Loads trace links from a gold standard file.
     * This method:
     * <ol>
     *     <li>Reads the gold standard file</li>
     *     <li>Skips header if configured</li>
     *     <li>Parses each line into a trace link</li>
     *     <li>Handles column swapping if configured</li>
     * </ol>
     *
     * @param goldStandardConfiguration Configuration for the gold standard file
     * @return Set of valid trace links from the gold standard
     * @throws UncheckedIOException If there are issues reading the gold standard file
     */
    @NotNull
    private static Set<TraceLink> getTraceLinksFromGoldStandard(GoldStandardConfiguration goldStandardConfiguration) {
        File groundTruth = new File(goldStandardConfiguration.path());
        boolean header = goldStandardConfiguration.hasHeader();
        logger.info("Skipping header: {}", header);
        Set<TraceLink> validTraceLinks;
        try {
            validTraceLinks = Files.readAllLines(groundTruth.toPath()).stream()
                    .skip(header ? 1 : 0)
                    .map(l -> l.split(","))
                    .map(it -> goldStandardConfiguration.swapColumns()
                            ? new TraceLink(it[1], it[0])
                            : new TraceLink(it[0], it[1]))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return validTraceLinks;
    }

    /**
     * Saves trace links to a CSV file based on configuration.
     * This method:
     * <ol>
     *     <li>Generates a filename based on the configuration</li>
     *     <li>Delegates to the main save method</li>
     * </ol>
     *
     * @param traceLinks Set of trace links to save
     * @param configFile Configuration file used for the analysis
     * @param configuration Configuration object used for the analysis
     * @throws UncheckedIOException If there are issues writing the trace links file
     */
    public static void saveTraceLinks(Set<TraceLink> traceLinks, File configFile, Configuration configuration)
            throws UncheckedIOException {
        var fileName = "traceLinks-" + configuration.getConfigurationIdentifierForFile(configFile.getName()) + ".csv";
        saveTraceLinks(traceLinks, fileName);
    }

    /**
     * Saves trace links to a CSV file.
     * This method:
     * <ol>
     *     <li>Sorts trace links by source and target IDs</li>
     *     <li>Converts trace links to CSV format</li>
     *     <li>Writes the result to the specified file</li>
     * </ol>
     *
     * @param traceLinks Set of trace links to save
     * @param destination Path to the output file
     * @throws UncheckedIOException If there are issues writing the trace links file
     */
    public static void saveTraceLinks(Set<TraceLink> traceLinks, String destination) throws UncheckedIOException {
        logger.info("Storing trace links to {}", destination);

        List<TraceLink> orderedTraceLinks = new ArrayList<>(traceLinks);
        orderedTraceLinks.sort(Comparator.comparing(TraceLink::sourceId).thenComparing(TraceLink::targetId));

        String csvResult = orderedTraceLinks.stream()
                .map(it -> it.sourceId() + "," + it.targetId())
                .collect(Collectors.joining("\n"));
        try {
            Files.writeString(new File(destination).toPath(), csvResult, StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
