/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import edu.kit.kastel.sdq.lissa.ratlr.utils.KeyGenerator;

/**
 * Represents a key for embedding caching operations in the LiSSA framework.
 * This record is used to uniquely identify cached values based on various parameters
 * such as the model used, seed value, operation mode, and content.
 * <p>
 * The key can be serialized to JSON for storage and retrieval from the cache.
 * <p>
 * Please always use the {@link #of(String, int, double, String)} method to create a new instance.
 *
 * @param model The identifier of the model used for the cached operation.
 * @param seed The seed value used for randomization in the cached operation.
 * @param temperature The temperature setting used in the cached operation.
 * @param mode The mode of operation that was cached (e.g., embedding generation or chat
 * @param content The content that was processed in the cached operation.
 * @param localKey A local key for additional identification, not included in JSON serialization.
 */
//TODO: Technically, this cache key is for classifiers (chat) or embeddings.
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmbeddingCacheKey(
        String model, int seed, double temperature, LargeLanguageModelCacheMode mode, String content, @JsonIgnore String localKey)
        implements CacheKey {

    public static EmbeddingCacheKey of(String model, int seed, double temperature, String content) {
        return new EmbeddingCacheKey(model, seed, temperature, LargeLanguageModelCacheMode.EMBEDDING, content, KeyGenerator.generateKey(content));
    }

    /**
     * Only use this method if you want to use a custom local key. You mostly do not want to do this. Only for special handling of embeddings.
     * You should always prefer the {@link #of(String, int, double, String)} method.
     * @deprecated please use {@link #of(String, int, double, String)} instead.
     */
    @Deprecated(forRemoval = false)
    public static EmbeddingCacheKey ofRaw(
            String model, int seed, double temperature, String content, String localKey) {
        return new EmbeddingCacheKey(model, seed, temperature, LargeLanguageModelCacheMode.EMBEDDING, content, localKey);
    }
}
