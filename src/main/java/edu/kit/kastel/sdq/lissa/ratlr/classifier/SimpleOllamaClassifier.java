package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.utils.OllamaUtils;

import java.time.Duration;

public class SimpleOllamaClassifier extends SimpleClassifier {

    public SimpleOllamaClassifier(RatlrConfiguration.ModuleConfiguration configuration) {
        super(configuration.arguments().getOrDefault("model", "llama3:8b"));
    }

    @Override
    protected ChatLanguageModel createChatModel(String model) {
        String host = System.getenv("OLLAMA_HOST");
        String user = System.getenv("OLLAMA_USER");
        String password = System.getenv("OLLAMA_PASSWORD");

        var ollama = OllamaChatModel.builder().baseUrl(host).modelName(model).timeout(Duration.ofMinutes(5)).temperature(0.0).build();
        if (user != null && password != null) {
            OllamaUtils.setAuthForOllama(ollama, host, Duration.ofMinutes(5), user, password);
        }
        return ollama;
    }
}
