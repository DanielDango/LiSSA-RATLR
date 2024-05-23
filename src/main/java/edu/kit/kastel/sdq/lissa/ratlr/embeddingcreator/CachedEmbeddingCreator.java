package edu.kit.kastel.sdq.lissa.ratlr.embeddingcreator;

import dev.langchain4j.model.embedding.EmbeddingModel;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

abstract class CachedEmbeddingCreator extends EmbeddingCreator {
    private final Cache cache;
    private final EmbeddingModel embeddingModel;

    protected CachedEmbeddingCreator(String model) {
        this.cache = CacheManager.getInstance().getCache(this.getClass().getSimpleName() + "_" + Objects.requireNonNull(model));
        this.embeddingModel = Objects.requireNonNull(createEmbeddingModel(model));
    }

    protected abstract EmbeddingModel createEmbeddingModel(String model);

    @Override
    public final List<float[]> calculateEmbeddings(List<Element> elements) {
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
