package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import okhttp3.Credentials;

import java.time.Duration;
import java.util.Map;

public class ReasoningOllamaClassifier extends ReasoningClassifier {
    public ReasoningOllamaClassifier(RatlrConfiguration.ModuleConfiguration configuration) {
        super(configuration.arguments().getOrDefault("model", "llama3:8b"), Integer.parseInt(configuration.arguments().getOrDefault("prompt_id", "0")), Boolean
                .parseBoolean(configuration.arguments().getOrDefault("use_original_artifacts", "false")), Boolean.parseBoolean(configuration.arguments()
                        .getOrDefault("use_system_message", "true")));
    }

    @Override
    protected ChatLanguageModel createChatModel(String model) {
        String host = System.getenv("OLLAMA_HOST");
        String user = System.getenv("OLLAMA_USER");
        String password = System.getenv("OLLAMA_PASSWORD");

        var ollama = OllamaChatModel.builder().baseUrl(host).modelName(model).timeout(Duration.ofMinutes(5)).temperature(0.0);
        if (user != null && password != null) {
            ollama.customHeaders(Map.of("Authorization", Credentials.basic(user, password)));
        }
        return ollama.build();
    }
}
