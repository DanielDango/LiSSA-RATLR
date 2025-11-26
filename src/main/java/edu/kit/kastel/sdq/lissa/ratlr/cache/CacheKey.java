/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Represents a key for caching operations in the LiSSA framework.
 */
public interface CacheKey {
    /**
     * Converts this cache key to a JSON string representation.
     * The resulting string can be used as a unique identifier for the cached value.
     *
     * @return A JSON string representation of this cache key
     */
    default String toJsonKey() {
        ObjectMapper mapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize key", e);
        }
    }

    /**
     * A local key for additional identification, not included in JSON serialization.
     *
     * @return A string representing the local key
     */
    //TODO: Need more details in an interface.
    String localKey();
}
