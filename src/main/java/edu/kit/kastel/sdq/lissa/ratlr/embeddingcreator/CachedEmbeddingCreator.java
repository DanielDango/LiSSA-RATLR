package edu.kit.kastel.sdq.lissa.ratlr.embeddingcreator;

import dev.langchain4j.model.embedding.EmbeddingModel;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

abstract class CachedEmbeddingCreator extends EmbeddingCreator {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Cache cache;
    private final EmbeddingModel embeddingModel;

    protected CachedEmbeddingCreator(String model) {
        this.cache = CacheManager.getDefaultInstance().getCache(this.getClass().getSimpleName() + "_" + Objects.requireNonNull(model));
        this.embeddingModel = Objects.requireNonNull(createEmbeddingModel(model));
    }

    protected abstract EmbeddingModel createEmbeddingModel(String model);

    @Override
    public final List<float[]> calculateEmbeddings(List<Element> elements) {
        List<float[]> embeddings = new ArrayList<>();
        for (Element element : elements) {
            String key = UUID.nameUUIDFromBytes(element.getContent().getBytes()).toString();
            float[] cachedEmbedding = cache.get(key, float[].class);
            if (cachedEmbedding != null) {
                embeddings.add(cachedEmbedding);
            } else {
                logger.info("Calculating embedding for: {}", element.getIdentifier());
                float[] embedding = embeddingModel.embed(element.getContent()).content().vector();
                cache.put(key, embedding);
                embeddings.add(embedding);
            }
        }
        return embeddings;
    }
}
