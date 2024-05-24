package edu.kit.kastel.sdq.lissa.ratlr.embeddingcreator;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import okhttp3.Credentials;

import java.time.Duration;
import java.util.Map;

public class OllamaEmbeddingCreator extends CachedEmbeddingCreator {

    public OllamaEmbeddingCreator(RatlrConfiguration.ModuleConfiguration configuration) {
        super(configuration.arguments().getOrDefault("model", "nomic-embed-text:v1.5"));
    }

    @Override
    protected EmbeddingModel createEmbeddingModel(String model) {
        String host = System.getenv("OLLAMA_EMBEDDING_HOST");
        String user = System.getenv("OLLAMA_EMBEDDING_USER");
        String password = System.getenv("OLLAMA_EMBEDDING_PASSWORD");

        var ollamaEmbedding = new OllamaEmbeddingModel.OllamaEmbeddingModelBuilder().baseUrl(host).modelName(model).timeout(Duration.ofMinutes(5));
        if (user != null && password != null) {
            ollamaEmbedding.customHeaders(Map.of("Authorization", Credentials.basic(user, password)));
        }
        return ollamaEmbedding.build();
    }
}
