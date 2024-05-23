package edu.kit.kastel.sdq.lissa.ratlr.embeddingcreator;

import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.OllamaUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class OllamaEmbeddingCreator extends EmbeddingCreator {
    private final String model;
    private final Cache cache;
    private final OllamaEmbeddingModel embeddingModel;

    public OllamaEmbeddingCreator(RatlrConfiguration.ModuleConfiguration configuration) {

        this.model = configuration.arguments().getOrDefault("model", "nomic-embed-text:v1.5");
        this.cache = CacheManager.getInstance().getCache(this.getClass().getSimpleName() + "_" + model);

        String host = System.getenv("OLLAMA_EMBEDDING_HOST");
        String user = System.getenv("OLLAMA_EMBEDDING_USER");
        String password = System.getenv("OLLAMA_EMBEDDING_PASSWORD");

        OllamaEmbeddingModel ollamaEmbedding = new OllamaEmbeddingModel(host, model, Duration.ofMinutes(5), 3);
        if (user != null && password != null) {
            OllamaUtils.setAuthForOllama(ollamaEmbedding, host, Duration.ofMinutes(5), user, password);
        }

        this.embeddingModel = ollamaEmbedding;
    }

    @Override
    public List<float[]> calculateEmbeddings(List<Element> elements) {
        List<float[]> embeddings = new ArrayList<>();
        for (Element element : elements) {
            String key = element.getIdentifier();
            float[] cachedEmbedding = cache.get(key, float[].class);
            if (cachedEmbedding != null) {
                embeddings.add(cachedEmbedding);
            } else {
                float[] embedding = embeddingModel.embed(element.getContent()).content().vector();
                cache.put(key, embedding);
                embeddings.add(embedding);
            }
        }
        return embeddings;
    }
}
