/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Represents a key for caching operations in the LiSSA framework.
 * This record is used to uniquely identify cached values based on various parameters
 * such as the model used, seed value, operation mode, and content.
 *
 * The key can be serialized to JSON for storage and retrieval from the cache.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CacheKey(
        /**
         * The identifier of the model used for the cached operation.
         */
        String model,

        /**
         * The seed value used for randomization in the cached operation.
         */
        int seed,

        /**
         * The mode of operation that was cached (e.g., embedding generation or chat).
         */
        Mode mode,

        /**
         * The content that was processed in the cached operation.
         */
        String content,

        /**
         * A local key for additional identification, not included in JSON serialization.
         */
        @JsonIgnore String localKey) {
    /**
     * Defines the possible modes of operation that can be cached.
     */
    public enum Mode {
        /**
         * Mode for caching embedding generation operations.
         */
        EMBEDDING,

        /**
         * Mode for caching chat-based operations.
         */
        CHAT
    }

    /**
     * ObjectMapper instance configured for JSON serialization with indentation.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

    /**
     * Converts this cache key to a JSON string representation.
     * The resulting string can be used as a unique identifier for the cached value.
     *
     * @return A JSON string representation of this cache key
     * @throws IllegalArgumentException If the key cannot be serialized to JSON
     */
    public String toRawKey() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize key", e);
        }
    }
}
