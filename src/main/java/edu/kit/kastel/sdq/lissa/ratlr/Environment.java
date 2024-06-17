package edu.kit.kastel.sdq.lissa.ratlr;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Environment {
    private static final Logger logger = LoggerFactory.getLogger(Environment.class);
    private static final Path dotenvPath = Path.of("./.env");
    private static final Dotenv DOTENV;

    static {
        Dotenv dotenv;
        try {
            dotenv = Dotenv.configure().load();
        } catch (DotenvException e) {
            try {
                Files.createFile(dotenvPath);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            logger.error("empty '{}' created, use 'env-template' to set it up", dotenvPath);
            dotenv = Dotenv.configure().load();
        }
        DOTENV = dotenv;
    }

    private Environment() {
        throw new IllegalAccessError("Utility class");
    }

    public static String getenv(String key) {
        String dotenvValue = DOTENV.get(key);
        if (dotenvValue != null)
            return dotenvValue;
        return System.getenv(key);
    }

    public static String getenvNonNull(String key) {
        String env = getenv(key);
        if (env == null) {
            logger.error("environment variable {} is missing, use '{}' or your system to set it up", key, dotenvPath);
        }
        return env;
    }
}
