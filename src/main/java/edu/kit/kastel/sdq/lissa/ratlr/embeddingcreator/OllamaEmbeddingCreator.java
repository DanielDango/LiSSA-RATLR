package edu.kit.kastel.sdq.lissa.ratlr.embeddingcreator;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.utils.OllamaUtils;

import java.time.Duration;

public class OllamaEmbeddingCreator extends CachedEmbeddingCreator {

    public OllamaEmbeddingCreator(RatlrConfiguration.ModuleConfiguration configuration) {
        super(configuration.arguments().getOrDefault("model", "nomic-embed-text:v1.5"));
    }

    @Override
    protected EmbeddingModel createEmbeddingModel(String model) {
        String host = System.getenv("OLLAMA_EMBEDDING_HOST");
        String user = System.getenv("OLLAMA_EMBEDDING_USER");
        String password = System.getenv("OLLAMA_EMBEDDING_PASSWORD");

        OllamaEmbeddingModel ollamaEmbedding = new OllamaEmbeddingModel(host, model, Duration.ofMinutes(5), 3);
        if (user != null && password != null) {
            OllamaUtils.setAuthForOllama(ollamaEmbedding, host, Duration.ofMinutes(5), user, password);
        }

        return ollamaEmbedding;
    }
}
