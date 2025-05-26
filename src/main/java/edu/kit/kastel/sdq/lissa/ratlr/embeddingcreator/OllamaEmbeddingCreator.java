/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.embeddingcreator;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Environment;

public class OllamaEmbeddingCreator extends CachedEmbeddingCreator {

    public OllamaEmbeddingCreator(ModuleConfiguration configuration) {
        super(configuration.argumentAsString("model", "nomic-embed-text:v1.5"), 1);
    }

    @Override
    protected EmbeddingModel createEmbeddingModel(String model, String... params) {
        String host = Environment.getenvNonNull("OLLAMA_EMBEDDING_HOST");
        String user = Environment.getenv("OLLAMA_EMBEDDING_USER");
        String password = Environment.getenv("OLLAMA_EMBEDDING_PASSWORD");

        var ollamaEmbedding = new OllamaEmbeddingModel.OllamaEmbeddingModelBuilder()
                .baseUrl(host)
                .modelName(model)
                .timeout(Duration.ofMinutes(5));
        if (user != null && password != null && !user.isEmpty() && !password.isEmpty()) {
            ollamaEmbedding.customHeaders(Map.of(
                    "Authorization",
                    "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8))));
        }
        return ollamaEmbedding.build();
    }
}
