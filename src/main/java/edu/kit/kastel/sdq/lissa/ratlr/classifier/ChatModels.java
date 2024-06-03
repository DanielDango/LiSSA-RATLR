package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import okhttp3.Credentials;

import java.time.Duration;
import java.util.Map;

public final class ChatModels {
    public static OllamaChatModel createOllamaChatModel(String model) {
        String host = System.getenv("OLLAMA_HOST");
        String user = System.getenv("OLLAMA_USER");
        String password = System.getenv("OLLAMA_PASSWORD");

        var ollama = OllamaChatModel.builder().baseUrl(host).modelName(model).timeout(Duration.ofMinutes(5)).temperature(0.0);
        if (user != null && password != null && !user.isEmpty() && !password.isEmpty()) {
            ollama.customHeaders(Map.of("Authorization", Credentials.basic(user, password)));
        }
        return ollama.build();
    }

    public static OpenAiChatModel createOpenAiChatModel(String model) {
        String openAiOrganizationId = System.getenv("OPENAI_ORGANIZATION_ID");
        String openAiApiKey = System.getenv("OPENAI_API_KEY");
        if (openAiOrganizationId == null || openAiApiKey == null) {
            throw new IllegalStateException("OPENAI_ORGANIZATION_ID or OPENAI_API_KEY environment variable not set");
        }
        return new OpenAiChatModel.OpenAiChatModelBuilder().modelName(model).organizationId(openAiOrganizationId).apiKey(openAiApiKey).temperature(0.0).build();
    }
}
