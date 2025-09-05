/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import static edu.kit.kastel.sdq.lissa.ratlr.utils.ChatLanguageModelUtils.nCachedRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.evaluator.AbstractEvaluator;
import edu.kit.kastel.sdq.lissa.ratlr.evaluator.BruteForceEvaluator;
import edu.kit.kastel.sdq.lissa.ratlr.evaluator.UCBanditEvaluator;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import edu.kit.kastel.sdq.lissa.ratlr.postprocessor.TraceLinkIdPostprocessor;
import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;
import edu.kit.kastel.sdq.lissa.ratlr.scorer.AbstractScorer;
import edu.kit.kastel.sdq.lissa.ratlr.scorer.BinaryScorer;
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
    private static final String TASK_SECTION = "task";

    private static final String NUMBER_OF_GRADIENTS_KEY = "number_of_gradients";
    private static final int DEFAULT_NUMBER_OF_GRADIENTS = 4;
    private static final String NUMBER_OF_ERRORS_KEY = "number_of_errors";
    private static final int DEFAULT_NUMBER_OF_ERRORS = 1;
    private static final String NUMBER_OF_GRADIENTS_PER_ERROR_KEY = "gradients_per_error";
    private static final int DEFAULT_NUMBER_OF_GRADIENTS_PER_ERROR = 1;
    // parser.add_argument('--steps_per_gradient', default=1, type=int)
    private static final String STEPS_PER_GRADIENT_KEY = "steps_per_gradient";
    private static final int STEPS_PER_GRADIENT = 1;
    // parser.add_argument('--mc_samples_per_step', default=2, type=int)
    private static final String MC_SAMPLES_PER_STEP_KEY = "mc_samples_per_step";
    private static final int MC_SAMPLES_PER_STEP = 2;
    // parser.add_argument('--max_expansion_factor', default=8, type=int)
    private static final String MAX_EXPANSION_FACTOR_KEY = "max_expansion_factor";
    private static final int MAX_EXPANSION_FACTOR = 8;
    // parser.add_argument('--reject_on_errors', action='store_true')
    private static final String REJECT_ON_ERRORS_KEY = "reject_on_errors";
    private static final boolean REJECT_ON_ERRORS = true;

    public static final String DEFAULT_TRANSFORMATION_PROMPT =
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
    public static final String DEFAULT_SYNONYM_PROMPT =
            "Generate a variation of the following instruction while keeping the semantic meaning.%n%nInput: %s%n%nOutput:";
    public static final int MAX_ERROR_SAMPLES_TODO = 16;

    // parser.add_argument('--samples_per_eval', default=32, type=int)
    private static final String SAMPLES_PER_EVAL_KEY = "samples_per_eval";
    private static final int SAMPLES_PER_EVAL = 32;
    // parser.add_argument('--eval_rounds', default=8, type=int)
    private static final String EVAL_ROUNDS_KEY = "eval_rounds";
    private static final int EVAL_ROUNDS = 8;
    // parser.add_argument('--eval_prompts_per_round', default=8, type=int)
    private static final String EVAL_PROMPTS_PER_ROUND_KEY = "eval_prompts_per_round";
    private static final int EVAL_PROMPTS_PER_ROUND = 8;
    // config['eval_budget'] = config['samples_per_eval'] * config['eval_rounds'] * config['eval_prompts_per_round']
    private static final int EVALUATION_BUDGET = SAMPLES_PER_EVAL * EVAL_ROUNDS * EVAL_PROMPTS_PER_ROUND;
    // parser.add_argument('--minibatch_size', default=64, type=int)
    private static final String MINIBATCH_SIZE_KEY = "minibatch_size";
    private static final int MINIBATCH_SIZE = 64;
    // parser.add_argument('--beam_size', default=4, type=int)
    private static final String BEAM_SIZE_KEY = "beam_size";
    private static final int BEAM_SIZE = 4;

    private final String feedbackPrompt;
    private final int feedbackSize;
    private final int numberOfGradients;
    private final int numberOfErrors;
    private final int numberOfGradientsPerError;
    private final AbstractEvaluator evaluator;
    private final AbstractScorer scorer;

    public AutomaticPromptOptimizer(
            ModuleConfiguration configuration,
            Set<TraceLink> goldStandard,
            ResultAggregator aggregator,
            TraceLinkIdPostprocessor traceLinkIdPostProcessor,
            Classifier classifier) {
        super(configuration, goldStandard, aggregator, traceLinkIdPostProcessor, classifier);
        this.feedbackPrompt = configuration.argumentAsString(FEEDBACK_PROMPT_KEY, FEEDBACK_PROMPT_TEMPLATE);
        this.feedbackSize = configuration.argumentAsInt(FEEDBACK_SIZE_KEY, FEEDBACK_SIZE);
        // Todo: Remember to add temperature parameter
        this.numberOfGradients = configuration.argumentAsInt(NUMBER_OF_GRADIENTS_KEY, DEFAULT_NUMBER_OF_GRADIENTS);
        this.numberOfErrors = configuration.argumentAsInt(NUMBER_OF_ERRORS_KEY, DEFAULT_NUMBER_OF_ERRORS);
        this.numberOfGradientsPerError =
                configuration.argumentAsInt(NUMBER_OF_GRADIENTS_PER_ERROR_KEY, DEFAULT_NUMBER_OF_GRADIENTS_PER_ERROR);
        // TODO: Get through config by factory
        this.evaluator = new UCBanditEvaluator();
        this.scorer = new BinaryScorer();
    }

    /**
     *  Parse text that is tagged with start and end tags.
     */
    private static List<String> parseTaggedText(String text, String startTag, String endTag) {
        List<String> texts = new ArrayList<>();
        while (true) {
            int startIndex = text.indexOf(startTag);
            if (startIndex == -1) {
                break;
            }
            int endIndex = text.indexOf(endTag, startIndex);
            if (endIndex == -1) {
                break;
            }
            startIndex += startTag.length();
            texts.add(text.substring(startIndex, endIndex).strip());
            text = text.substring(endIndex + endTag.length());
        }
        return texts;
    }

    /**
     * Parses a sectioned prompt into a map of section headers to their corresponding content.
     * Sections are identified by lines starting with "# ".
     *
     * @param prompt The sectioned prompt string
     * @return A map where keys are section headers (in lowercase, without punctuation) and values are the section content
     */
    private static Map<String, String> parseSectionedPrompt(String prompt) {
        Map<String, String> result = new HashMap<>();
        String currentHeader = "";
        for (String line : prompt.split(System.lineSeparator())) {
            line = line.strip();
            if (line.startsWith(SECTION_HEADER_PREFIX)) {
                // first word without punctuation
                currentHeader = line.substring(2).strip().toLowerCase().split(" ")[0];
                currentHeader = currentHeader.replaceAll("\\p{Punct}", "");
                result.put(currentHeader, "");
            } else if (!currentHeader.isEmpty()) {
                result.put(currentHeader, result.get(currentHeader) + line + System.lineSeparator());
            }
        }
        return result;
    }

    @Override
    public String optimize(ElementStore sourceStore, ElementStore targetStore) {
        List<ClassificationTask> tasks = new ArrayList<>();
        Set<TraceLink> possibleTraceLinks = getAllTraceLinks(sourceStore, targetStore);
        for (TraceLink link : possibleTraceLinks) {
            tasks.add(new ClassificationTask(
                    sourceStore.getById(link.sourceId()).first(),
                    targetStore.getById(link.targetId()).first(),
                    validTraceLinks.contains(link)));
        }
        return optimizeInternal(sourceStore, targetStore, tasks);
    }
    /**
     */
    private String optimizeInternal(
            ElementStore sourceStore, ElementStore targetStore, List<ClassificationTask> tasks) {
        List<String> candidatePrompts = new ArrayList<>(Collections.singleton(optimizationPrompt));
        for (int round = 0; round < maximumIterations; round++) {
            logger.info("Starting apo iteration {}/{}", round + 1, maximumIterations);
            // expand candidates
            if (round > 0) {
                candidatePrompts = expandCandidates(candidatePrompts, sourceStore, targetStore);
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
                    scorePromptPairs.stream().map(Pair::second).limit(BEAM_SIZE).toList();
            scores = scorePromptPairs.stream().map(Pair::first).limit(BEAM_SIZE).toList();
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
     *  Get "gradients" for a prompt based on the error string.
     */
    private List<String> getGradientsInternal(String prompt, String errorString, int numberOfResponses) {
        String gradientPrompt = String.format(DEFAULT_GRADIENT_PROMPT, prompt, errorString, numberOfGradientsPerError);
        return cachedSanitizedPromptRequest(numberOfResponses, gradientPrompt);
    }

    /**
     * Get "gradients" for a prompt based on sampled error strings.
     * @return Pairs of (feedback, error string)
     */
    private List<Pair<String, String>> getGradients(String taskSection, List<EvaluationResult<Boolean>> evaluation) {
        List<Pair<String, String>> feedbacks = new ArrayList<>();
        for (int i = 0; i < numberOfGradients; i++) {
            String errorString = sampleErrorString(evaluation);
            List<String> gradients = getGradientsInternal(taskSection, errorString, 1);
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
    private List<String> expandCandidates(List<String> prompts, ElementStore sourceStore, ElementStore targetStore) {
        // minibatch
        ElementStore trainingSourceStore = ElementStore.reduceSourceElementStore(sourceStore, MINIBATCH_SIZE);
        ElementStore trainingTargetStore = ElementStore.reduceTargetStore(targetStore, trainingSourceStore);

        List<String> newPrompts = new ArrayList<>();
        for (String prompt : prompts) {
            String taskSection = parseSectionedPrompt(prompt).get(TASK_SECTION).strip();
            // evaluate prompt on minibatch
            List<EvaluationResult<Boolean>> evaluation =
                    evaluatePrompt(prompt, trainingSourceStore, trainingTargetStore);
            // get gradients
            List<Pair<String, String>> gradients = getGradients(taskSection, evaluation);
            List<String> newTaskSections = new ArrayList<>();
            for (Pair<String, String> feedbackAndError : gradients) {
                newTaskSections.addAll(
                        applyGradient(taskSection, feedbackAndError.second(), feedbackAndError.first(), 1));
            }
            // generate synonyms
            // TODO Find out what MC is
            ArrayList<String> mcSampledTaskSections = new ArrayList<>();
            if (MC_SAMPLES_PER_STEP > 0) {
                for (String section : Stream.concat(newTaskSections.stream(), Stream.of(taskSection))
                        .toList()) {
                    mcSampledTaskSections.addAll(generateSynonyms(section, MC_SAMPLES_PER_STEP));
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
            if (combinedTaskSections.size() > MAX_EXPANSION_FACTOR) {
                if (REJECT_ON_ERRORS) {
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
                            .limit(MAX_EXPANSION_FACTOR * 2)
                            .toList();
                    AbstractScorer scorer = new BinaryScorer();
                    AbstractEvaluator evaluator = new BruteForceEvaluator(EVALUATION_BUDGET);
                    List<Double> errorScores = evaluator.call(tempNewPrompts, errorExamples, classifier, scorer);
                    List<Integer> sortedIdxs = errorScores.stream()
                            .sorted()
                            // TODO Check if this is correct
                            .mapToInt(errorScores::indexOf)
                            .boxed()
                            .toList();
                    tempNewPrompts = sortedIdxs.stream()
                            .skip(Math.max(0, sortedIdxs.size() - MAX_EXPANSION_FACTOR))
                            .map(tempNewPrompts::get)
                            .toList();
                } else {
                    tempNewPrompts =
                            tempNewPrompts.stream().limit(MAX_EXPANSION_FACTOR).toList();
                }
            }
            newPrompts.addAll(tempNewPrompts);
        }
        newPrompts.addAll(prompts);
        return newPrompts.stream().distinct().toList();
    }

    /**
     * Incorporate feedback gradient into a prompt.
     * TODO: numberOfResponses
     */
    private List<String> applyGradient(
            String prompt, String errorString, String feedbackString, int numberOfResponses) {
        String transformationPrompt = String.format(
                DEFAULT_TRANSFORMATION_PROMPT,
                prompt,
                errorString,
                feedbackString,
                STEPS_PER_GRADIENT,
                STEPS_PER_GRADIENT);
        return cachedSanitizedPromptRequest(numberOfResponses, transformationPrompt);
    }

    private List<String> cachedSanitizedPromptRequest(int n, String prompt) {
        prompt = String.join("\n", prompt.lines().map(String::stripLeading).toList());
        List<String> responses = nCachedRequest(prompt, provider, llm, cache, n);
        List<String> results = responses.stream()
                .map(IterativeOptimizer::extractPromptFromResponse)
                .toList();
        List<String> newPrompts = new ArrayList<>();
        for (String result : results) {
            newPrompts.addAll(parseTaggedText(result, START_TAG, END_TAG));
        }
        return newPrompts;
    }

    /**
     * Generate synonyms for a prompt section.
     */
    private List<String> generateSynonyms(String promptSection, int n) {
        String rewriterPrompt = String.format(DEFAULT_SYNONYM_PROMPT, promptSection);
        return nCachedRequest(rewriterPrompt, provider, llm, cache, n);
    }

    /**
     * Evaluate a prompt amd return a list of evaluation results, containing the source and target elements, the
     * ground truth and classification result.
     *
     *
     * @param prompt
     * @param sourceStore
     * @param targetStore
     * @return
     */
    private List<EvaluationResult<Boolean>> evaluatePrompt(
            String prompt, ElementStore sourceStore, ElementStore targetStore) {
        Set<TraceLink> possibleTraceLinks = getAllTraceLinks(sourceStore, targetStore);
        Set<TraceLink> classifiedTraceLinks = super.getTraceLinks(sourceStore, targetStore, prompt);
        List<EvaluationResult<Boolean>> results = new ArrayList<>();
        for (TraceLink link : possibleTraceLinks) {
            results.add(new EvaluationResult<>(
                    sourceStore.getById(link.sourceId()).first(),
                    targetStore.getById(link.targetId()).first(),
                    validTraceLinks.contains(link),
                    classifiedTraceLinks.contains(link)));
        }
        return results;
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
            for (var target : targetStore.getAllElements(true)) {
                allLinks.add(TraceLink.of(
                        source.first().getIdentifier(), target.first().getIdentifier()));
            }
        }
        return allLinks;
    }
}
