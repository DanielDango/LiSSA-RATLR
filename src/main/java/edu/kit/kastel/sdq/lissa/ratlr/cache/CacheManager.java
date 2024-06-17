package edu.kit.kastel.sdq.lissa.ratlr.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class CacheManager {
    private static final String CACHE_DIR = "cache";
    private static CacheManager instance = new CacheManager();
    private Map<String, Cache> caches = new HashMap<>();

    static {
        Path cacheDir = Path.of(CACHE_DIR);
        if (Files.notExists(cacheDir)) {
            try {
                Files.createDirectory(cacheDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private CacheManager() {
    }

    public static CacheManager getInstance() {
        if (instance == null) {
            instance = new CacheManager();
        }
        return instance;
    }

    public Cache getCache(String name) {
        name = name.replace(":", "__");

        if (caches.containsKey(name)) {
            return caches.get(name);
        }

        Cache cache = new Cache(CACHE_DIR + "/" + name + ".json");
        caches.put(name, cache);
        return cache;
    }
}
