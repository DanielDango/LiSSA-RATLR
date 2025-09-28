/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.cache;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import edu.kit.kastel.sdq.lissa.ratlr.utils.KeyGenerator;

public record ScorerCacheKey(String prompt, String content, @JsonIgnore String localKey) implements CacheKey {

    /**
     * ObjectMapper instance configured for JSON serialization with indentation.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

    public static ScorerCacheKey of(String prompt, String content) {
        return new ScorerCacheKey(prompt, content, KeyGenerator.generateKey(prompt + content));
    }

    @Override
    public String toJsonKey() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize key", e);
        }
    }
}
