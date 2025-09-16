/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.PromptOptimizationUtils.getClassificationTasks;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.PromptOptimizationUtils.parseTaggedText;
import static edu.kit.kastel.sdq.lissa.ratlr.utils.ChatLanguageModelUtils.nCachedRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.SourceElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.TargetElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.evaluator.AbstractEvaluator;
import edu.kit.kastel.sdq.lissa.ratlr.evaluator.BruteForceEvaluator;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.promptmetric.Metric;
import edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.samplestrategy.SampleStrategy;
import edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.samplestrategy.ShuffledFirstSampler;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

/**
 * This class implements an automatic prompt optimizer based on the approach by Pryzant et al. (2023).
 * It is based on gradient descent to optimize prompts for large language models.
 * This is largely a transcription of the Python code provided by the authors adapted into the LiSSA framework.
 *
 * @author Daniel Schwab
 *
 */
public class AutomaticPromptOptimizer extends IterativeOptimizer {

    private static final String START_TAG = "<START>";
    private static final String END_TAG = "<END>";
    private static final String SECTION_HEADER_PREFIX = "# ";
    private static final String TASK_SECTION = "task";
    // TODO add to config
    private static final int MAX_ERROR_SAMPLES_TODO = 16;

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomaticPromptOptimizer.class);

    private final GradientOptimizerConfig config;
    private final AbstractEvaluator evaluator;
    private final SampleStrategy sampleStrategy;

    public AutomaticPromptOptimizer(
            ModuleConfiguration configuration,
            Set<TraceLink> goldStandard,
            Metric metric,
            AbstractEvaluator evaluator) {
        super(configuration, goldStandard, metric);
        this.config = new GradientOptimizerConfig(configuration);
        this.evaluator = evaluator;
        this.sampleStrategy = new ShuffledFirstSampler(config.random());
    }

    /**
     * Parses a sectioned prompt into a map of section headers to their corresponding content.
     * Sections are identified by lines starting with "# " as in the Markdown syntax.
     * If no sections are found, the entire prompt is treated as a single {@value TASK_SECTION} section.
     *
     * @param prompt The sectioned prompt string
     * @return A map where keys are section headers (in lowercase, without punctuation) and values are the section content
     */
    private static Map<String, String> parseSectionedPrompt(String prompt) {
        Map<String, String> sections = new HashMap<>();
        String currentHeader = "";
        StringBuilder currentSection = new StringBuilder();
        for (String line : prompt.split(System.lineSeparator())) {
            line = line.strip();
            if (line.startsWith(SECTION_HEADER_PREFIX)) {
                // save previous section
                if (!currentHeader.isEmpty()) {
                    sections.put(currentHeader, currentSection.toString().trim());
                    currentSection = new StringBuilder();
                }
                // first word without punctuation as new header
                currentHeader = line.substring(SECTION_HEADER_PREFIX.length())
                        .strip()
                        .toLowerCase()
                        .split(" ")[0]
                        .replaceAll("\\p{Punct}", "");
            } else {
                currentSection.append(line).append(System.lineSeparator());
            }
        }
        if (sections.isEmpty()) {
            sections.put(TASK_SECTION, prompt);
        }
        return sections;
    }

    @Override
    public String optimize(SourceElementStore sourceStore, TargetElementStore targetStore) {
        List<ClassificationTask> tasks = getClassificationTasks(sourceStore, targetStore, validTraceLinks);
        return optimizeInternal(tasks);
    }
    /**
     */
    private String optimizeInternal(List<ClassificationTask> tasks) {
        List<String> candidatePrompts = new ArrayList<>(Collections.singleton(optimizationPrompt));
        for (int round = 0; round < maximumIterations; round++) {
            LOGGER.info("Starting apo iteration {}/{}", round + 1, maximumIterations);
            // expand candidates
            if (round > 0) {
                candidatePrompts = expandCandidates(candidatePrompts, tasks);
                LOGGER.info("Expanded to {} candidates", candidatePrompts.size());
            }
            // score candidates
            List<Double> scores = scoreCandidates(candidatePrompts, tasks);
            List<Pair<Double, String>> scorePromptPairs = new ArrayList<>();
            for (int i = 0; i < scores.size(); i++) {
                scorePromptPairs.add(new Pair<>(scores.get(i), candidatePrompts.get(i)));
            }
            // sort by score descending and select top beam size
            scorePromptPairs = scorePromptPairs.stream()
                    .sorted((a, b) -> Double.compare(b.first(), a.first()))
                    .toList();
            candidatePrompts = scorePromptPairs.stream()
                    .map(Pair::second)
                    .limit(config.beamSize())
                    .toList();
            scores = scorePromptPairs.stream()
                    .map(Pair::first)
                    .limit(config.beamSize())
                    .toList();
            // record candidates, estimated scores, and true scores
            LOGGER.info("Scores: {}", scores);
        }
        return candidatePrompts.getFirst();
    }

    /**
     * Score a list of prompts
     */
    private List<Double> scoreCandidates(List<String> prompts, List<ClassificationTask> tasks) {
        if (prompts.size() == 1) {
            return List.of(1.0);
        }
        return evaluator.call(prompts, tasks, metric);
    }

    /**
     * Get textual "gradients" for a prompt based on an error string that indicate why the prompt might yielded the error.
     *
     * @param prompt The prompt section to improve
     * @param errorString The error string indicating the errors made by the prompt
     * @param numberOfResponses The number of gradient responses to generate
     * @return A list of textual gradients suggesting improvements to the prompt
     */
    private List<String> getTextualGradients(String prompt, String errorString, int numberOfResponses) {
        String formattedGradientPrompt =
                String.format(config.gradientPrompt(), prompt, errorString, config.numberOfGradientsPerError());
        return cachedSanitizedPromptRequest(numberOfResponses, formattedGradientPrompt);
    }

    /**
     * Get "gradients" for a prompt based on sampled error strings.
     * @return Pairs of (feedback, error string)
     */
    private List<Pair<String, String>> getGradients(String taskSection, List<EvaluationResult<Boolean>> evaluation) {
        List<Pair<String, String>> feedbacks = new ArrayList<>();
        for (int i = 0; i < config.numberOfGradients(); i++) {
            String errorString = sampleErrorString(evaluation);
            List<String> gradients = getTextualGradients(taskSection, errorString, 1);
            feedbacks.addAll(
                    gradients.stream().map(x -> new Pair<>(x, errorString)).collect(Collectors.toSet()));
        }
        return feedbacks;
    }

    /**
     * Sample n error strings from the given texts, labels, and preds
     */
    private String sampleErrorString(List<EvaluationResult<Boolean>> evaluationResults) {
        List<Integer> errorIDxs = new ArrayList<>();
        for (EvaluationResult<Boolean> result : evaluationResults) {
            if (!result.isCorrect()) {
                errorIDxs.add(evaluationResults.indexOf(result));
            }
        }
        int[] sampleIdxs = errorIDxs.stream()
                .sorted()
                .mapToInt(Integer::intValue)
                .limit(config.numberOfErrors())
                .toArray();
        StringBuilder errorString = new StringBuilder();
        for (int i : sampleIdxs) {
            errorString.append("## Example ").append(i + 1).append(System.lineSeparator());
            errorString.append(generateErrorString(evaluationResults.get(i)));
            errorString.append(System.lineSeparator());
        }

        return errorString.toString();
    }

    private static String generateErrorString(EvaluationResult<Boolean> evaluationResult) {
        return "Text: \""
                + evaluationResult.getTextualRepresentation().strip()
                + "\""
                + System.lineSeparator()
                + "Ground Truth: " + evaluationResult.groundTruth() + System.lineSeparator()
                + "Classification Result: "
                + evaluationResult.classification()
                + System.lineSeparator();
    }

    /**
     * Expand a list of prompts by generating gradient-based successors and synonyms for each section.
     */
    private List<String> expandCandidates(List<String> prompts, List<ClassificationTask> tasks) {
        // minibatch
        List<ClassificationTask> reducedTasks = sampleStrategy.sample(tasks, config.minibatchSize());

        List<String> candidatePrompts = new ArrayList<>();
        for (String prompt : prompts) {
            candidatePrompts.addAll(expandCandidates(prompt, reducedTasks));
        }
        candidatePrompts.addAll(prompts);
        return candidatePrompts.stream().distinct().toList();
    }

    private List<String> expandCandidates(String prompt, List<ClassificationTask> tasks) {
        String taskSection = parseSectionedPrompt(prompt).get(TASK_SECTION).strip();
        // evaluate prompt on minibatch
        List<EvaluationResult<Boolean>> evaluation = evaluatePrompt(tasks, prompt);
        // get gradients
        List<Pair<String, String>> gradients = getGradients(taskSection, evaluation);
        List<String> newTaskSections = new ArrayList<>();
        for (Pair<String, String> feedbackAndError : gradients) {
            newTaskSections.addAll(applyGradient(taskSection, feedbackAndError.second(), feedbackAndError.first()));
        }
        // generate synonyms
        // TODO Find out what MC is
        List<String> mcSampledTaskSections = new ArrayList<>();
        if (config.mcSamplesPerStep() > 0) {
            for (String section : Stream.concat(newTaskSections.stream(), Stream.of(taskSection))
                    .toList()) {
                mcSampledTaskSections.addAll(generateSynonyms(section, config.mcSamplesPerStep()));
            }
        }
        // combine
        List<String> combinedTaskSections = Stream.concat(newTaskSections.stream(), mcSampledTaskSections.stream())
                .distinct()
                .toList();
        List<String> tempNewPrompts = new ArrayList<>();
        for (String section : combinedTaskSections) {
            tempNewPrompts.add(prompt.replace(taskSection, section));
        }
        // filter a little
        if (combinedTaskSections.size() > config.maxExpansionFactor()) {
            if (config.rejectOnErrors()) {
                List<ClassificationTask> errorExamples = new ArrayList<>();
                for (EvaluationResult<Boolean> result : evaluation) {
                    if (!result.isCorrect()) {
                        errorExamples.add(
                                new ClassificationTask(result.source(), result.target(), result.groundTruth()));
                    }
                }
                errorExamples =
                        errorExamples.stream().limit(MAX_ERROR_SAMPLES_TODO).toList();
                // speed up a little
                tempNewPrompts = tempNewPrompts.stream()
                        .limit(config.maxExpansionFactor() * 2)
                        .toList();
                AbstractEvaluator bruteForceEvaluator = new BruteForceEvaluator(config.evaluationBudget());
                List<Double> errorScores = bruteForceEvaluator.call(tempNewPrompts, errorExamples, metric);
                List<Integer> sortedIdxs = errorScores.stream()
                        .sorted()
                        // TODO Check if this is correct
                        .mapToInt(errorScores::indexOf)
                        .boxed()
                        .toList();
                tempNewPrompts = sortedIdxs.stream()
                        .skip(Math.max(0, sortedIdxs.size() - config.maxExpansionFactor()))
                        .map(tempNewPrompts::get)
                        .toList();
            } else {
                tempNewPrompts = tempNewPrompts.stream()
                        .limit(config.maxExpansionFactor())
                        .toList();
            }
        }
        return tempNewPrompts;
    }

    /**
     * Evaluate a prompt on a list of classification tasks, returning detailed results.
     */
    private List<EvaluationResult<Boolean>> evaluatePrompt(
            List<ClassificationTask> classificationTasks, String prompt) {
        List<EvaluationResult<Boolean>> evaluation = new ArrayList<>();
        for (ClassificationTask task : classificationTasks) {
            if (isClassifiedCorrectly(prompt, task)) {
                evaluation.add(new EvaluationResult<>(task.source(), task.target(), task.label(), task.label()));
            } else {
                evaluation.add(new EvaluationResult<>(task.source(), task.target(), task.label(), !task.label()));
            }
        }
        return evaluation;
    }

    /**
     * Incorporate feedback gradient into a prompt.
     */
    private List<String> applyGradient(String prompt, String errorString, String feedbackString) {
        String formattedTransformationPrompt = String.format(
                config.transformationPrompt(),
                prompt,
                errorString,
                feedbackString,
                config.stepsPerGradient(),
                config.stepsPerGradient());
        return cachedSanitizedPromptRequest(config.stepsPerGradient(), formattedTransformationPrompt);
    }

    /**
     * Generate synonyms for a prompt section.
     */
    private List<String> generateSynonyms(String promptSection, int n) {
        String formattedSynonymPrompt = String.format(config.synonymPrompt(), promptSection);
        return cachedSanitizedPromptRequest(n, formattedSynonymPrompt);
    }

    private List<String> cachedSanitizedPromptRequest(int n, String prompt) {
        prompt = String.join("\n", prompt.lines().map(String::stripLeading).toList());
        List<String> responses = nCachedRequest(prompt, provider, llm, cache, n);
        List<String> newPrompts = new ArrayList<>();
        for (String result : responses) {
            newPrompts.addAll(parseTaggedText(result, START_TAG, END_TAG));
        }
        return sanitizePrompts(newPrompts);
    }

    /**
     * Apply the {@link PromptOptimizationUtils#sanitizePrompt(String)} method to a list of prompts.
     *
     * @param prompts the list of prompts to sanitize
     * @return        a list of sanitized prompts
     */
    private static List<String> sanitizePrompts(List<String> prompts) {
        return prompts.stream().map(PromptOptimizationUtils::sanitizePrompt).toList();
    }
}
