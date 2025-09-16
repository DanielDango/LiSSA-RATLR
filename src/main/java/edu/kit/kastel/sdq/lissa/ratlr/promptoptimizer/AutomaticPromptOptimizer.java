/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.PromptOptimizationUtils.getClassificationTasks;
import static edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.PromptOptimizationUtils.parseTaggedText;
import static edu.kit.kastel.sdq.lissa.ratlr.utils.ChatLanguageModelUtils.nCachedRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.samplestrategy.OrderedFirstSampler;
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
    private final SampleStrategy orderedSampleStrategy;

    public AutomaticPromptOptimizer(
            ModuleConfiguration configuration,
            Set<TraceLink> goldStandard,
            Metric metric,
            AbstractEvaluator evaluator) {
        super(configuration, goldStandard, metric);
        this.config = new GradientOptimizerConfig(configuration);
        this.evaluator = evaluator;
        this.sampleStrategy = new ShuffledFirstSampler(config.random());
        this.orderedSampleStrategy = new OrderedFirstSampler();
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

    /**
     * Optimize the prompt using the gradient descent based automatic prompt optimization strategy by Pryzant et al.
     * (2023).
     * This method iteratively refines the prompt by generating candidate prompts,
     * scoring them, and selecting the best ones for further refinement.
     * <br>
     * The candidate generation involves:
     * <ul>
     *     <li>Expanding candidates using textual gradients derived from error analysis</li>
     *     <li>Generating synonyms for prompts to explore variations</li>
     * </ul>
     * The scoring of candidates is performed using the provided evaluator and metric.
     *
     * @param sourceStore The source element store
     * @param targetStore The target element store
     * @return The optimized prompt after the specified number of iterations
     */
    @Override
    public String optimize(SourceElementStore sourceStore, TargetElementStore targetStore) {
        List<ClassificationTask> tasks = getClassificationTasks(sourceStore, targetStore, validTraceLinks);
        List<String> candidatePrompts = new ArrayList<>(Collections.singleton(optimizationPrompt));
        for (int round = 0; round < maximumIterations; round++) {
            LOGGER.info("Starting apo iteration {}/{}", round + 1, maximumIterations);
            // expand candidates
            if (round > 0) {
                candidatePrompts = expandCandidates(candidatePrompts, tasks);
                LOGGER.info("Expanded to {} candidates", candidatePrompts.size());
            }
            // score candidates
            var candidatesAndScores = scoreAndFilterCandidates(candidatePrompts, tasks);
            candidatePrompts = candidatesAndScores.first();
            List<Double> scores = candidatesAndScores.second();
            // record candidates, estimated scores, and true scores
            LOGGER.info("Scores: {}", scores);
        }
        return candidatePrompts.getFirst();
    }

    /**
     * Score a list of prompts using the {@link #scorePrompts(List, List)} function and limit them to the
     * {@link GradientOptimizerConfig#beamSize()} size according to their scores in descending order.
     *
     * @return A pair of (filtered prompts, their scores)
     */
    private Pair<List<String>, List<Double>> scoreAndFilterCandidates(
            List<String> prompts, List<ClassificationTask> tasks) {
        List<Double> scores = scorePrompts(prompts, tasks);
        List<Pair<Double, String>> scorePromptPairs = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            scorePromptPairs.add(new Pair<>(scores.get(i), prompts.get(i)));
        }
        // sort by score descending and select top beam size
        scorePromptPairs = scorePromptPairs.stream()
                .sorted((a, b) -> Double.compare(b.first(), a.first()))
                .toList();
        List<String> candidatePrompts = scorePromptPairs.stream()
                .map(Pair::second)
                .limit(config.beamSize())
                .toList();
        scores = scorePromptPairs.stream()
                .map(Pair::first)
                .limit(config.beamSize())
                .toList();
        return new Pair<>(candidatePrompts, scores);
    }

    /**
     * Score a list of prompts using the provided evaluator and metric.
     * If there is only one prompt, it is assigned a perfect score of 1.0.
     *
     * @param prompts The list of prompts to score
     * @param tasks The classification tasks to use for scoring
     * @return A list of scores corresponding to each prompt
     */
    private List<Double> scorePrompts(List<String> prompts, List<ClassificationTask> tasks) {
        if (prompts.size() == 1) {
            return List.of(1.0);
        }
        return evaluator.call(prompts, tasks, metric);
    }

    /**
     * Get textual "gradients" for a prompt based on an error string that indicate why classification with the prompt
     * might have yielded the error.
     *
     * @param prompt The prompt section to improve
     * @param errorString The error string indicating the errors present with the prompt
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
     *
     * @param prompt The prompt to improve
     * @param evaluation The evaluation results to sample errors from
     * @return A list of pairs where each pair contains a feedback string and the corresponding error string
     */
    private List<Pair<String, String>> getGradients(String prompt, List<EvaluationResult<Boolean>> evaluation) {
        List<Pair<String, String>> feedbacks = new ArrayList<>();
        for (int i = 0; i < config.numberOfGradients(); i++) {
            String errorString = sampleErrorString(evaluation);
            List<String> gradients = getTextualGradients(prompt, errorString, 1);
            feedbacks.addAll(gradients.stream()
                    .map(gradient -> new Pair<>(gradient, errorString))
                    .collect(Collectors.toSet()));
        }
        return feedbacks;
    }

    /**
     * Sample n error strings from the given texts, labels, and preds
     */
    /**
     * Sample error strings from evaluation results.
     * This method selects a subset of misclassified examples and formats them into a single error string.
     *
     * @param evaluationResults The list of evaluation results to sample errors from
     * @return A formatted string containing sampled error examples
     */
    private String sampleErrorString(List<EvaluationResult<Boolean>> evaluationResults) {
        List<Integer> errorIDxs = new ArrayList<>();
        for (EvaluationResult<Boolean> result : evaluationResults) {
            if (!result.isCorrect()) {
                errorIDxs.add(evaluationResults.indexOf(result));
            }
        }
        List<Integer> sampleIdxs = orderedSampleStrategy.sample(errorIDxs, config.numberOfErrors());
        StringBuilder errorString = new StringBuilder();
        for (int i : sampleIdxs) {
            errorString.append("## Example ").append(i + 1).append(System.lineSeparator());
            errorString.append(generateErrorString(evaluationResults.get(i)));
            errorString.append(System.lineSeparator());
        }

        return errorString.toString();
    }

    /**
     * Generate an error string for a given evaluation result using the feedback example block template.
     *
     * @param evaluationResult The evaluation result to generate the error string for
     * @return A formatted error string
     */
    private String generateErrorString(EvaluationResult<Boolean> evaluationResult) {
        return config.feedbackExampleBlock()
                .formatted(
                        evaluationResult.getTextualRepresentation().strip(),
                        evaluationResult.groundTruth(),
                        evaluationResult.classification());
    }

    /**
     * Expand a list of prompts into a larger list of candidate prompts using gradient-based modifications and synonym
     * generation on a subset of the tasks.
     * This method processes each prompt individually, by delegating to {@link #expandCandidates(String, List)}.
     *
     * @param prompts The list of prompts to expand
     * @param tasks The classification tasks to use for generating gradients and evaluating prompts
     * @return A distinct list of expanded candidate prompts
     */
    private List<String> expandCandidates(List<String> prompts, List<ClassificationTask> tasks) {
        List<ClassificationTask> reducedTasks = sampleStrategy.sample(tasks, config.minibatchSize());

        List<String> candidatePrompts = new ArrayList<>();
        for (String prompt : prompts) {
            candidatePrompts.addAll(expandCandidates(prompt, reducedTasks));
        }
        candidatePrompts.addAll(prompts);
        return candidatePrompts.stream().distinct().toList();
    }

    /**
     * Expand a single prompt into a larger list of candidate prompts using gradient-based modifications and synonym
     * generation on all provided tasks.
     * This includes the following steps:
     * <ul>
     *     <li>Evaluate the prompt on all tasks to identify misclassifications</li>
     *     <li>Generate textual gradients based on the errors found</li
     *     <li>Apply the gradients to create new prompt variations</li>
     *     <li>Generate synonyms for the task section of the prompt to explore variations</li>
     *     <li>Combine the new task sections with the original prompt to form new candidate prompts</li>
     *     <li>Filter the candidate prompts to limit the total number based on configuration settings</li>
     * </ul>
     *
     * @param prompt The prompt to expand
     * @param tasks The classification tasks to use for generating gradients and evaluating the prompt
     * @return A distinct list of expanded candidate prompts
     */
    private List<String> expandCandidates(String prompt, List<ClassificationTask> tasks) {
        String taskSection = parseSectionedPrompt(prompt).get(TASK_SECTION).strip();

        List<EvaluationResult<Boolean>> evaluation = evaluatePrompt(tasks, prompt);

        Collection<String> promptVariations = applyGradient(taskSection, evaluation);
        Collection<String> synonyms = generateSynonyms(promptVariations, config.mcSamplesPerStep());
        synonyms.addAll(generateSynonyms(taskSection, config.mcSamplesPerStep()));
        // combine
        promptVariations.addAll(synonyms);

        Collection<String> combinedTaskSections = promptVariations;

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
     * Gets and applies a list of textual gradients to a prompt section to generate new prompt variations.
     * This method processes each gradient individually with the {@link #applyGradient(String, String, String)} method
     * and aggregates the resulting prompt variations.
     *
     * @param originalPrompt The original prompt section to modify
     * @param evaluationResults The evaluation results used to derive the gradients
     * @return A distinct collection of new prompt sections generated by applying the gradients
     */
    private Collection<String> applyGradient(String originalPrompt, List<EvaluationResult<Boolean>> evaluationResults) {
        Collection<Pair<String, String>> gradients = getGradients(originalPrompt, evaluationResults);
        Set<String> promptVariations = new HashSet<>();
        for (Pair<String, String> feedbackAndError : gradients) {
            String feedback = feedbackAndError.first();
            String error = feedbackAndError.second();
            promptVariations.addAll(applyGradient(originalPrompt, error, feedback));
        }
        return promptVariations;
    }

    /**
     * Generate synonyms for a list of prompts to explore variations.
     * This method applies the {@link #generateSynonyms(String, int)} method to each prompt in the list
     * and aggregates the results.
     * If the number of synonyms per prompt is less than 1, an empty list is returned.
     *
     * @param prompts The list of prompts to generate synonyms for
     * @param numberOfSynonymsPerPrompt The number of synonym variations to generate per prompt
     * @return A distinct list of synonym variations for the prompt sections
     */
    private Collection<String> generateSynonyms(Collection<String> prompts, int numberOfSynonymsPerPrompt) {
        if (numberOfSynonymsPerPrompt < 1) {
            return List.of();
        }
        List<String> synonyms = new ArrayList<>();
        for (String prompt : prompts) {
            synonyms.addAll(generateSynonyms(prompt, numberOfSynonymsPerPrompt));
        }
        return synonyms;
    }

    /**
     * Generate synonyms for a given prompt to explore variations.
     *
     * @param prompt The prompt to generate synonyms for
     * @param numberOfSynonyms The number of synonym variations to generate
     * @return A list of synonym variations for the prompt section
     */
    private Collection<String> generateSynonyms(String prompt, int numberOfSynonyms) {
        String formattedSynonymPrompt = String.format(config.synonymPrompt(), prompt);
        return cachedSanitizedPromptRequest(numberOfSynonyms, formattedSynonymPrompt);
    }

    /**
     * Evaluate a prompt on a list of classification tasks.
     * This method checks each task to see if it is classified correctly by the prompt using the
     * {@link #isClassifiedCorrectly(String, ClassificationTask)} method.
     *
     * @param classificationTasks The list of classification tasks to evaluate against
     * @param prompt The prompt to evaluate
     * @return A list of evaluation results indicating correctness for each task
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
     * Apply a textual gradient to a prompt section to generate new prompt variations.
     *
     * @param prompt The prompt section to modify
     * @param errorString The error string indicating the errors present with the prompt
     * @param feedbackString The textual gradient suggesting improvements to the prompt
     * @return A list of new prompt sections generated by applying the gradient
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
     * Send a cached request to the language model and parse the response for text between {@value #START_TAG} and
     * {@value #END_TAG} tags.
     * The prompts are sanitized using the {@link PromptOptimizationUtils#sanitizePrompt(String)} method.
     *
     * @param n The number of responses to request from the language model
     * @param prompt The prompt to send to the language model
     * @return A list of sanitized prompts extracted from the language model responses
     */
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
