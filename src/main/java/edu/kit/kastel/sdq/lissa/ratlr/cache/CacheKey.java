/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.cache;

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
    String toJsonKey();

    /**
     * A local key for additional identification
     *
     * @return A string representing the local key
     */
    String localKey();
}
