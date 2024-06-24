package edu.kit.kastel.sdq.lissa.ratlr.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Cache {
    private static final int MAX_DIRTY = 10;
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
                throw new IllegalArgumentException("Could not read cache file (" + file.getName() + ")", e);
            }
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (dirty > 0) {
                write();
            }
        }));
    }

    private void write() {
        try {
            File tempFile = new File(file.getAbsolutePath() + ".tmp.json");
            mapper.writeValue(tempFile, data);
            Files.copy(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.delete(tempFile.toPath());
            dirty = 0;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not write cache file", e);
        }
    }

    public void put(String key, String value) {
        String old = data.put(key, value);
        if (old == null || !old.equals(value)) {
            dirty++;
        }

        if (dirty > MAX_DIRTY) {
            write();
        }
    }

    public String get(String key) {
        return data.get(key);
    }

    public <T> T get(String key, Class<T> clazz) {
        try {
            var jsonData = this.data.get(key);
            if (jsonData == null) {
                return null;
            }

            if (clazz == String.class) {
                return (T) jsonData;
            }

            return mapper.readValue(jsonData, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not deserialize object", e);
        }
    }

    public <T> void put(String key, T value) {
        try {
            put(key, mapper.writeValueAsString(Objects.requireNonNull(value)));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize object", e);
        }
    }
}
