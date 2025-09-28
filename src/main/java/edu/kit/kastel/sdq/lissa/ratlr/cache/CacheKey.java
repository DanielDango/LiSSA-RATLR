/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.cache;

public interface CacheKey {
    String toJsonKey();

    String localKey();
}
