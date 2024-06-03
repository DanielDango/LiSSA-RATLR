package edu.kit.kastel.sdq.lissa.ratlr;

import io.github.cdimascio.dotenv.Dotenv;

public final class Environment {
    private static final Dotenv DOTENV = Dotenv.configure().load();

    public static String getenv(String key) {
        String dotenvValue = DOTENV.get(key);
        if (dotenvValue != null)
            return dotenvValue;
        return System.getenv(key);
    }
}
