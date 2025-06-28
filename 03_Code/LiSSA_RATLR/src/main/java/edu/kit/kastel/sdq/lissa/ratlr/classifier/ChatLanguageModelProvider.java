/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Environment;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Provides chat language model instances for different platforms.
 * This class supports multiple language model platforms (OpenAI, Ollama, Blablador)
 * and handles their configuration, including authentication and model settings.
 *
 * Required environment variables for each platform:
 * <ul>
 *   <li>OpenAI:
 *     <ul>
 *       <li>{@code OPENAI_ORGANIZATION_ID}: Your OpenAI organization ID</li>
 *       <li>{@code OPENAI_API_KEY}: Your OpenAI API key</li>
 *     </ul>
 *   </li>
 *   <li>Ollama:
 *     <ul>
 *       <li>{@code OLLAMA_HOST}: The host URL for the Ollama server (optional)</li>
 *       <li>{@code OLLAMA_USER}: Username for Ollama authentication (optional)</li>
 *       <li>{@code OLLAMA_PASSWORD}: Password for Ollama authentication (optional)</li>
 *     </ul>
 *   </li>
 *   <li>Blablador:
 *     <ul>
 *       <li>{@code BLABLADOR_API_KEY}: Your Blablador API key</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class ChatLanguageModelProvider {
    /**
     * Identifier for the OpenAI platform.
     */
    public static final String OPENAI = "openai";

    /**
     * Identifier for the Ollama platform.
     */
    public static final String OLLAMA = "ollama";

    /**
     * Identifier for the Blablador platform.
     */
    public static final String BLABLADOR = "blablador";

    /**
     * Identifier for the DeepSeek platform.
     */
    public static final String DEEPSEEK = "deepseek";

    /**
     * Default seed value for model randomization.
     */
    public static final int DEFAULT_SEED = 133742243;

    /**
     * The platform to use for the language model.
     */
    private final String platform;

    /**
     * The name of the model to use.
     */
    private String modelName;

    /**
     * The seed value for model randomization.
     */
    private int seed;

    /**
     * Creates a new chat language model provider with the specified configuration.
     * The configuration name should be in the format "mode_platform" (e.g., "simple_openai").
     *
     * @param configuration The module configuration containing model settings
     */
    public ChatLanguageModelProvider(ModuleConfiguration configuration) {
        String[] modeXplatform = configuration.name().split(Classifier.CONFIG_NAME_SEPARATOR, 2);
        if (modeXplatform.length == 1) {
            this.platform = null;
            return;
        }
        this.platform = modeXplatform[1];
        initModelPlatform(configuration);
    }

    /**
     * Creates a chat model instance based on the configured platform.
     *
     * @return A chat model instance for the configured platform
     * @throws IllegalArgumentException If the platform is not supported
     */
    public ChatModel createChatModel() {
        return switch (platform) {
            case OPENAI -> createOpenAiChatModel(modelName, seed);
            case OLLAMA -> createOllamaChatModel(modelName, seed);
            case BLABLADOR -> createBlabladorChatModel(modelName, seed);
            case DEEPSEEK -> createDeepSeekChatModel(modelName, seed);
            default -> throw new IllegalArgumentException("Unsupported platform: " + platform);
        };
    }

    /**
     * Initializes the model platform settings from the configuration.
     * Sets the model name and seed value based on the platform.
     *
     * @param configuration The module configuration containing model settings
     * @throws IllegalArgumentException If the platform is not supported
     */
    private void initModelPlatform(ModuleConfiguration configuration) {
        final String modelKey = "model";
        this.modelName = switch (platform) {
            case OPENAI -> configuration.argumentAsString(modelKey, "gpt-4o-mini");
            case OLLAMA -> configuration.argumentAsString(modelKey, "llama3:8b");
            case BLABLADOR -> configuration.argumentAsString(modelKey, "2 - Llama 3.3 70B instruct");
            default -> throw new IllegalArgumentException("Unsupported platform: " + platform);
        };
        this.seed = configuration.argumentAsInt("seed", DEFAULT_SEED);
    }

    /**
     * Gets the name of the configured model.
     *
     * @return The model name
     */
    public String modelName() {
        return modelName;
    }

    /**
     * Gets the seed value used for model randomization.
     *
     * @return The seed value
     */
    public int seed() {
        return seed;
    }

    /**
     * Determines the number of threads to use based on the platform.
     * OpenAI and Blablador platforms use 100 threads, while others use 1.
     *
     * @param configuration The module configuration
     * @return The number of threads to use
     */
    public static int threads(ModuleConfiguration configuration) {
        return configuration.name().contains(OPENAI) || configuration.name().contains(BLABLADOR) ? 100 : 1;
    }

    /**
     * Creates an Ollama chat model instance.
     * The model is configured with authentication if credentials are provided.
     *
     * @param model The name of the model to use
     * @param seed The seed value for randomization
     * @return A configured Ollama chat model instance
     */
    private static OllamaChatModel createOllamaChatModel(String model, int seed) {
        String host = Environment.getenv("OLLAMA_HOST");
        String user = Environment.getenv("OLLAMA_USER");
        String password = Environment.getenv("OLLAMA_PASSWORD");

        var ollama = OllamaChatModel.builder()
                .baseUrl(host)
                .modelName(model)
                .timeout(Duration.ofMinutes(15))
                .temperature(0.0)
                .seed(seed);
        if (user != null && password != null && !user.isEmpty() && !password.isEmpty()) {
            ollama.customHeaders(Map.of(
                    "Authorization",
                    "Basic "
                            + Base64.getEncoder()
                                    .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8))));
        }
        return ollama.build();
    }

    /**
     * Creates an OpenAI chat model instance.
     * Requires OpenAI organization ID and API key to be set in environment variables.
     *
     * @param model The name of the model to use
     * @param seed The seed value for randomization
     * @return A configured OpenAI chat model instance
     * @throws IllegalStateException If required environment variables are not set
     */
    private static OpenAiChatModel createOpenAiChatModel(String model, int seed) {
        String openAiOrganizationId = Environment.getenv("OPENAI_ORGANIZATION_ID");
        String openAiApiKey = Environment.getenv("OPENAI_API_KEY");
        if (openAiOrganizationId == null || openAiApiKey == null) {
            throw new IllegalStateException("OPENAI_ORGANIZATION_ID or OPENAI_API_KEY environment variable not set");
        }
        return new OpenAiChatModel.OpenAiChatModelBuilder()
                .modelName(model)
                .organizationId(openAiOrganizationId)
                .apiKey(openAiApiKey)
                .temperature(0.0)
                .seed(seed)
                .build();
    }

    /**
     * Creates a Blablador chat model instance.
     * Requires Blablador API key to be set in environment variables.
     *
     * @param model The name of the model to use
     * @param seed The seed value for randomization
     * @return A configured Blablador chat model instance
     * @throws IllegalStateException If required environment variables are not set
     */
    private static OpenAiChatModel createBlabladorChatModel(String model, int seed) {
        String blabladorApiKey = Environment.getenv("BLABLADOR_API_KEY");
        if (blabladorApiKey == null) {
            throw new IllegalStateException("BLABLADOR_API_KEY environment variable not set");
        }
        return new OpenAiChatModel.OpenAiChatModelBuilder()
                .baseUrl("https://api.helmholtz-blablador.fz-juelich.de/v1")
                .modelName(model)
                .apiKey(blabladorApiKey)
                .temperature(0.0)
                .seed(seed)
                .build();
    }

    /**
     * Creates a DeepSeek chat model instance.
     * Requires DeepSeek API key to be set in environment variables.
     *
     * @param model The name of the model to use
     * @param seed The seed value for randomization
     * @return A configured DeepSeek chat model instance
     * @throws IllegalStateException If required environment variables are not set
     */
    private static OpenAiChatModel createDeepSeekChatModel(String model, int seed) {
        String deepseekApiKey = Environment.getenv("DEEPSEEK_API_KEY");
        if (deepseekApiKey == null) {
            throw new IllegalStateException("DEEPSEEK_API_KEY environment variable not set");
        }
        return new OpenAiChatModel.OpenAiChatModelBuilder()
                .baseUrl("https://api.deepseek.com/v1")
                .modelName(model)
                .apiKey(deepseekApiKey)
                .temperature(0.0)
                .seed(seed)
                .build();
    }
}
