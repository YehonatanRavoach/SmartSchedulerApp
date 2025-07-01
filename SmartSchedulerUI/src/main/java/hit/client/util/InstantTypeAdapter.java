package hit.client.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;

/**
 * Gson TypeAdapter for java.time.Instant.
 * Serializes Instant to ISO-8601 string and parses from string.
 */
public class InstantTypeAdapter extends TypeAdapter<Instant> {
    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toString());
        }
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
        String s = in.nextString();
        return Instant.parse(s);
    }
}

