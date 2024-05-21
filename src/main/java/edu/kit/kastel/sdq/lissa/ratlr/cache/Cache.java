package edu.kit.kastel.sdq.lissa.ratlr.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Cache {
    private static final int MAX_DIRTY = 100;
    private final File file;
    private final ObjectMapper mapper;
    private Map<String, String> data = new HashMap<>();
    private int dirty = 0;

    public Cache(String cacheFile) {
        file = new File(cacheFile);
        mapper = new ObjectMapper();
        if (file.exists()) {
            try {
                data = mapper.readValue(file, new TypeReference<Map<String, String>>() {
                });
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read cache file");
            }
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (dirty > 0) {
                try {
                    mapper.writeValue(file, data);
                } catch (IOException e) {
                    throw new IllegalArgumentException("Could not write cache file");
                }
            }
        }));
    }

    public void put(String key, String value) {
        String old = data.put(key, value);
        if (old == null || !old.equals(value)) {
            dirty++;
        }

        if (dirty > MAX_DIRTY) {
            try {
                mapper.writeValue(file, data);
                dirty = 0;
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not write cache file");
            }
        }
    }

    public String get(String key) {
        return data.get(key);
    }

    public <T> T get(String key, Class<T> clazz) {
        try {
            return mapper.readValue(data.get(key), clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not deserialize object", e);
        }
    }

    public <T> void put(String key, T value) {
        try {
            put(key, mapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize object", e);
        }
    }
}
