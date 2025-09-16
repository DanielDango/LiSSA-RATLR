/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import java.util.Random;
import java.util.random.RandomGenerator;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;

public record GradientOptimizerConfig(
        int numberOfGradients,
        int numberOfErrors,
        int numberOfGradientsPerError,
        int stepsPerGradient,
        int mcSamplesPerStep,
        int maxExpansionFactor,
        boolean rejectOnErrors,
        int evaluationBudget,
        int minibatchSize,
        int beamSize,
        String gradientPrompt,
        String transformationPrompt,
        String synonymPrompt,
        RandomGenerator random) {


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
    private static final int DEFAULT_MINIBATCH_SIZE = 64;
    private static final String BEAM_SIZE_KEY = "beam_size";
    private static final int BEAM_SIZE = 4;
    private static final String SEED_KEY = "seed";
    private static final int DEFAULT_SEED = 133742243;

    public GradientOptimizerConfig(ModuleConfiguration configuration) {
        this(
                configuration.argumentAsInt(NUMBER_OF_GRADIENTS_KEY, DEFAULT_NUMBER_OF_GRADIENTS),
                configuration.argumentAsInt(NUMBER_OF_ERRORS_KEY, DEFAULT_NUMBER_OF_ERRORS),
                configuration.argumentAsInt(NUMBER_OF_GRADIENTS_PER_ERROR_KEY, DEFAULT_NUMBER_OF_GRADIENTS_PER_ERROR),
                configuration.argumentAsInt(STEPS_PER_GRADIENT_KEY, STEPS_PER_GRADIENT),
                configuration.argumentAsInt(MC_SAMPLES_PER_STEP_KEY, MC_SAMPLES_PER_STEP),
                configuration.argumentAsInt(MAX_EXPANSION_FACTOR_KEY, MAX_EXPANSION_FACTOR),
                configuration.argumentAsBoolean(REJECT_ON_ERRORS_KEY, REJECT_ON_ERRORS),
                configuration.argumentAsInt(SAMPLES_PER_EVAL_KEY, SAMPLES_PER_EVAL)
                        * configuration.argumentAsInt(EVAL_ROUNDS_KEY, EVAL_ROUNDS)
                        * configuration.argumentAsInt(EVAL_PROMPTS_PER_ROUND_KEY, EVAL_PROMPTS_PER_ROUND),
                configuration.argumentAsInt(MINIBATCH_SIZE_KEY, DEFAULT_MINIBATCH_SIZE),
                configuration.argumentAsInt(BEAM_SIZE_KEY, BEAM_SIZE),
                configuration.argumentAsString(GRADIENT_PROMPT_KEY, DEFAULT_GRADIENT_PROMPT),
                configuration.argumentAsString(TRANSFORMATION_PROMPT_KEY, DEFAULT_TRANSFORMATION_PROMPT),
                configuration.argumentAsString(SYNONYM_PROMPT_KEY, DEFAULT_SYNONYM_PROMPT),
                new Random(configuration.argumentAsInt(SEED_KEY, DEFAULT_SEED)));
    }
}
