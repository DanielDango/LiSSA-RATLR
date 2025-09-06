/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import static edu.kit.kastel.sdq.lissa.ratlr.utils.ChatLanguageModelUtils.nCachedRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationResult;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.evaluator.AbstractEvaluator;
import edu.kit.kastel.sdq.lissa.ratlr.evaluator.BruteForceEvaluator;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.postprocessor.TraceLinkIdPostprocessor;
import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;
import edu.kit.kastel.sdq.lissa.ratlr.scorer.AbstractScorer;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

/**
 * This class implements an automatic prompt optimizer based on the approach by Pryzant et al. (2023).
 * It is based on gradient descent to optimize prompts for large language models.
 * This is largely a transcription of the Python code provided by the authors adapted into the LiSSA framework.
 *
 * @author Daniel Schwab
 *
 */
public class AutomaticPromptOptimizer extends IterativeFeedbackOptimizer {

    public static final String START_TAG = "<START>";
    public static final String END_TAG = "<END>";
    public static final String SECTION_HEADER_PREFIX = "# ";
    private static final String TASK_SECTION = "task";

    // Default prompts from the original implementation

    private static final String GRADIENT_PROMPT_KEY = "gradient_prompt";
    private static final String DEFAULT_GRADIENT_PROMPT =
            """
                I'm trying to write a zero-shot classifier prompt.

                My current prompt is:
                "%s"

                But this prompt gets the following examples wrong:
                %s

                give %d reasons why the prompt could have gotten these examples wrong.
                Wrap each reason with <START> and <END>
                """;

    private static final String TRANSFORMATION_PROMPT_KEY = "transformation_prompt";
    private static final String DEFAULT_TRANSFORMATION_PROMPT =
            """
            I'm trying to write a zero-shot classifier.

            My current prompt is:
            "%s"

            But it gets the following examples wrong:
            %s

            Based on these examples the problem with this prompt is that %s

            Based on the above information, I wrote %d different improved prompts.
            Each prompt is wrapped with <START> and <END>.

            The %d new prompts are:
            """;

    private static final String SYNONYM_PROMPT_KEY = "synonym_prompt";
    private static final String DEFAULT_SYNONYM_PROMPT =
            "Generate a variation of the following instruction while keeping the semantic meaning.%n%nInput: %s%n%nOutput:";

    // TODO add to config
    private static final int MAX_ERROR_SAMPLES_TODO = 16;

    private static final String NUMBER_OF_GRADIENTS_KEY = "number_of_gradients";
    private static final int DEFAULT_NUMBER_OF_GRADIENTS = 4;
    private static final String NUMBER_OF_ERRORS_KEY = "number_of_errors";
    private static final int DEFAULT_NUMBER_OF_ERRORS = 1;
    private static final String NUMBER_OF_GRADIENTS_PER_ERROR_KEY = "gradients_per_error";
    private static final int DEFAULT_NUMBER_OF_GRADIENTS_PER_ERROR = 1;
    private static final String STEPS_PER_GRADIENT_KEY = "steps_per_gradient";
    private static final int STEPS_PER_GRADIENT = 1;
    private static final String MC_SAMPLES_PER_STEP_KEY = "mc_samples_per_step";
    private static final int MC_SAMPLES_PER_STEP = 2;
    private static final String MAX_EXPANSION_FACTOR_KEY = "max_expansion_factor";
    private static final int MAX_EXPANSION_FACTOR = 8;
    private static final String REJECT_ON_ERRORS_KEY = "reject_on_errors";
    private static final boolean REJECT_ON_ERRORS = true;
    private static final String SAMPLES_PER_EVAL_KEY = "samples_per_eval";
    private static final int SAMPLES_PER_EVAL = 32;
    private static final String EVAL_ROUNDS_KEY = "eval_rounds";
    private static final int EVAL_ROUNDS = 8;
    private static final String EVAL_PROMPTS_PER_ROUND_KEY = "eval_prompts_per_round";
    private static final int EVAL_PROMPTS_PER_ROUND = 8;
    private static final String MINIBATCH_SIZE_KEY = "minibatch_size";
    private static final int MINIBATCH_SIZE = 64;
    private static final String BEAM_SIZE_KEY = "beam_size";
    private static final int BEAM_SIZE = 4;
    private static final String SEED_KEY = "seed";
    private static final int DEFAULT_SEED = 133742243;

    private final int numberOfGradients;
    private final int numberOfErrors;
    private final int numberOfGradientsPerError;
    private final int stepsPerGradient;
    private final int mcSamplesPerStep;
    private final int maxExpansionFactor;
    private final boolean rejectOnErrors;
    private final int samplesPerEval;
    private final int evalRounds;
    private final int evalPromptsPerRound;
    private final int evaluationBudget;
    private final int minibatchSize;
    private final int beamSize;

    private final String gradientPrompt;
    private final String transformationPrompt;
    private final String synonymPrompt;

    private final AbstractEvaluator evaluator;
    private final AbstractScorer scorer;
    private final RandomGenerator random;

    public AutomaticPromptOptimizer(
            ModuleConfiguration configuration,
            Set<TraceLink> goldStandard,
            ResultAggregator aggregator,
            TraceLinkIdPostprocessor traceLinkIdPostProcessor,
            Classifier classifier,
            AbstractScorer scorer,
            AbstractEvaluator evaluator) {
        super(configuration, goldStandard, aggregator, traceLinkIdPostProcessor, classifier);
        this.numberOfGradients = configuration.argumentAsInt(NUMBER_OF_GRADIENTS_KEY, DEFAULT_NUMBER_OF_GRADIENTS);
        this.numberOfErrors = configuration.argumentAsInt(NUMBER_OF_ERRORS_KEY, DEFAULT_NUMBER_OF_ERRORS);
        this.numberOfGradientsPerError =
                configuration.argumentAsInt(NUMBER_OF_GRADIENTS_PER_ERROR_KEY, DEFAULT_NUMBER_OF_GRADIENTS_PER_ERROR);
        this.stepsPerGradient = configuration.argumentAsInt(STEPS_PER_GRADIENT_KEY, STEPS_PER_GRADIENT);
        this.mcSamplesPerStep = configuration.argumentAsInt(MC_SAMPLES_PER_STEP_KEY, MC_SAMPLES_PER_STEP);
        this.maxExpansionFactor = configuration.argumentAsInt(MAX_EXPANSION_FACTOR_KEY, MAX_EXPANSION_FACTOR);
        this.rejectOnErrors = configuration.argumentAsBoolean(REJECT_ON_ERRORS_KEY, REJECT_ON_ERRORS);
        this.samplesPerEval = configuration.argumentAsInt(SAMPLES_PER_EVAL_KEY, SAMPLES_PER_EVAL);
        this.evalRounds = configuration.argumentAsInt(EVAL_ROUNDS_KEY, EVAL_ROUNDS);
        this.evalPromptsPerRound = configuration.argumentAsInt(EVAL_PROMPTS_PER_ROUND_KEY, EVAL_PROMPTS_PER_ROUND);
        this.evaluationBudget = this.samplesPerEval * this.evalRounds * this.evalPromptsPerRound;
        this.minibatchSize = configuration.argumentAsInt(MINIBATCH_SIZE_KEY, MINIBATCH_SIZE);
        this.beamSize = configuration.argumentAsInt(BEAM_SIZE_KEY, BEAM_SIZE);

        this.gradientPrompt = configuration.argumentAsString(GRADIENT_PROMPT_KEY, DEFAULT_GRADIENT_PROMPT);
        this.transformationPrompt =
                configuration.argumentAsString(TRANSFORMATION_PROMPT_KEY, DEFAULT_TRANSFORMATION_PROMPT);
        this.synonymPrompt = configuration.argumentAsString(SYNONYM_PROMPT_KEY, DEFAULT_SYNONYM_PROMPT);

        this.evaluator = evaluator;
        this.scorer = scorer;
        this.random = new Random(configuration.argumentAsInt(SEED_KEY, DEFAULT_SEED));
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
    public String optimize(ElementStore sourceStore, ElementStore targetStore) {
        List<ClassificationTask> tasks = getAllClassificationTasks(sourceStore, targetStore);
        return optimizeInternal(tasks);
    }
    /**
     */
    private String optimizeInternal(List<ClassificationTask> tasks) {
        List<String> candidatePrompts = new ArrayList<>(Collections.singleton(optimizationPrompt));
        for (int round = 0; round < maximumIterations; round++) {
            logger.info("Starting apo iteration {}/{}", round + 1, maximumIterations);
            // expand candidates
            if (round > 0) {
                candidatePrompts = expandCandidates(candidatePrompts, tasks);
                logger.info("Expanded to {} candidates", candidatePrompts.size());
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
            candidatePrompts =
                    scorePromptPairs.stream().map(Pair::second).limit(beamSize).toList();
            scores = scorePromptPairs.stream().map(Pair::first).limit(beamSize).toList();
            // record candidates, estimated scores, and true scores
            logger.info("Scores: {}", scores);
        }
        return candidatePrompts.getFirst();
    }

    @Override
    protected AbstractPromptOptimizer copyOf(AbstractPromptOptimizer original) {
        return null;
    }

    /**
     * Score a list of prompts
     */
    private List<Double> scoreCandidates(List<String> prompts, List<ClassificationTask> tasks) {
        if (prompts.size() == 1) {
            return List.of(1.0);
        }
        return evaluator.call(prompts, tasks, classifier, scorer);
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
        String formattedGradientPrompt = String.format(gradientPrompt, prompt, errorString, numberOfGradientsPerError);
        return cachedSanitizedPromptRequest(numberOfResponses, formattedGradientPrompt);
    }

    /**
     * Get "gradients" for a prompt based on sampled error strings.
     * @return Pairs of (feedback, error string)
     */
    private List<Pair<String, String>> getGradients(String taskSection, List<EvaluationResult<Boolean>> evaluation) {
        List<Pair<String, String>> feedbacks = new ArrayList<>();
        for (int i = 0; i < numberOfGradients; i++) {
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
                .limit(numberOfErrors)
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
        List<ClassificationTask> reducedTasks = new ArrayList<>(tasks);
        Collections.shuffle(reducedTasks, random);
        reducedTasks = reducedTasks.stream().limit(minibatchSize).toList();

        List<String> newPrompts = new ArrayList<>();
        for (String prompt : prompts) {
            String taskSection = parseSectionedPrompt(prompt).get(TASK_SECTION).strip();
            // evaluate prompt on minibatch
            List<EvaluationResult<Boolean>> evaluation = evaluatePrompt(reducedTasks, prompt);
            // get gradients
            List<Pair<String, String>> gradients = getGradients(taskSection, evaluation);
            List<String> newTaskSections = new ArrayList<>();
            for (Pair<String, String> feedbackAndError : gradients) {
                newTaskSections.addAll(applyGradient(taskSection, feedbackAndError.second(), feedbackAndError.first()));
            }
            // generate synonyms
            // TODO Find out what MC is
            ArrayList<String> mcSampledTaskSections = new ArrayList<>();
            if (mcSamplesPerStep > 0) {
                for (String section : Stream.concat(newTaskSections.stream(), Stream.of(taskSection))
                        .toList()) {
                    mcSampledTaskSections.addAll(generateSynonyms(section, mcSamplesPerStep));
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
            if (combinedTaskSections.size() > maxExpansionFactor) {
                if (rejectOnErrors) {
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
                            .limit(maxExpansionFactor * 2)
                            .toList();
                    AbstractEvaluator bruteForceEvaluator = new BruteForceEvaluator(evaluationBudget);
                    List<Double> errorScores =
                            bruteForceEvaluator.call(tempNewPrompts, errorExamples, classifier, scorer);
                    List<Integer> sortedIdxs = errorScores.stream()
                            .sorted()
                            // TODO Check if this is correct
                            .mapToInt(errorScores::indexOf)
                            .boxed()
                            .toList();
                    tempNewPrompts = sortedIdxs.stream()
                            .skip(Math.max(0, sortedIdxs.size() - maxExpansionFactor))
                            .map(tempNewPrompts::get)
                            .toList();
                } else {
                    tempNewPrompts =
                            tempNewPrompts.stream().limit(maxExpansionFactor).toList();
                }
            }
            newPrompts.addAll(tempNewPrompts);
        }
        newPrompts.addAll(prompts);
        return newPrompts.stream().distinct().toList();
    }

    // TODO: This looks like bad practice
    private List<EvaluationResult<Boolean>> evaluatePrompt(
            List<ClassificationTask> classificationTasks, String prompt) {
        List<EvaluationResult<Boolean>> evaluation2 = new ArrayList<>();
        classifier.setClassificationPrompt(prompt);
        for (ClassificationTask task : classificationTasks) {
            Optional<ClassificationResult> result = classifier.classify(task);
            if (result.isPresent()) {
                evaluation2.add(new EvaluationResult<>(
                        task.source(), task.target(), task.label(), result.get().confidence() > 0.0));
            } else {
                evaluation2.add(new EvaluationResult<>(task.source(), task.target(), task.label(), false));
            }
        }
        return evaluation2;
    }

    /**
     * Incorporate feedback gradient into a prompt.
     */
    private List<String> applyGradient(String prompt, String errorString, String feedbackString) {
        String formattedTransformationPrompt = String.format(
                transformationPrompt, prompt, errorString, feedbackString, stepsPerGradient, stepsPerGradient);
        return cachedSanitizedPromptRequest(stepsPerGradient, formattedTransformationPrompt);
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
     * Generate synonyms for a prompt section.
     */
    private List<String> generateSynonyms(String promptSection, int n) {
        String formattedSynonymPrompt = String.format(synonymPrompt, promptSection);
        return cachedSanitizedPromptRequest(n, formattedSynonymPrompt);
    }

    /**
     * Get all possible combinations the source and target store. This includes trace links that are not in the gold standard.
     *
     * @param sourceStore the source element store
     * @param targetStore the target element store
     * @return a set of all possible trace links between the source and target store, of which the gold standard is a subset
     */
    private static Set<TraceLink> getAllTraceLinks(ElementStore sourceStore, ElementStore targetStore) {
        Set<TraceLink> allLinks = new HashSet<>();
        for (var source : sourceStore.getAllElements(true)) {
            for (Element target : targetStore.findSimilar(source)) {
                allLinks.add(TraceLink.of(source.first().getIdentifier(), target.getIdentifier()));
            }
        }
        return allLinks;
    }

    private List<ClassificationTask> getAllClassificationTasks(ElementStore sourceStore, ElementStore targetStore) {
        List<ClassificationTask> tasks = new ArrayList<>();
        for (Pair<Element, float[]> source : sourceStore.getAllElements(true)) {
            for (Element target : targetStore.findSimilar(source)) {
                tasks.add(new ClassificationTask(
                        source.first(),
                        target,
                        validTraceLinks.contains(
                                TraceLink.of(source.first().getIdentifier(), target.getIdentifier()))));
            }
        }
        return tasks;
    }

    /**
     * Apply the {@link AbstractPromptOptimizer#sanitizePrompt(String)} method to a list of prompts.
     *
     * @param prompts the list of prompts to sanitize
     * @return        a list of sanitized prompts
     */
    private static List<String> sanitizePrompts(List<String> prompts) {
        return prompts.stream().map(AbstractPromptOptimizer::sanitizePrompt).toList();
    }
}
