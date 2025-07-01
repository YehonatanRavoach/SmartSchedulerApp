package Util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.Instant;

/**
 * Singleton factory for a preconfigured Gson instance with support for java.time.Instant.
 */
public class GsonFactory {
    // TypeAdapter must be in your codebase (as you כבר יצרת).
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
            .create();

    /** Get the singleton Gson instance. */
    public static Gson get() {
        return gson;
    }
}
