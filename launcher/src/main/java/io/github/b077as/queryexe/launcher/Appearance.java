package io.github.b077as.queryexe.launcher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The theme and accent the client saved last ({@code config/settings.json}, key {@code appearance}), so the launcher opens
 * in the same colours and the user never sees a different window before theirs. Anything missing or odd falls back to the
 * client's defaults.
 */
@Slf4j
@Data
@AllArgsConstructor
public class Appearance {

    /** Same defaults as the client's {@code App.DEFAULT_APPEARANCE}. */
    private static final String DEFAULT_ACCENT = "rose";
    private static final int DEFAULT_BACKGROUND = 0x2B2D30;

    /** The client's accents: light colour, dark colour. Keys match {@code frontend/src/accents.ts}. */
    private static final Map<String, String[]> ACCENTS = Map.of(
            "blue", new String[]{"#2a62d9", "#3f7df2"},
            "teal", new String[]{"#0b8575", "#3cc2ad"},
            "green", new String[]{"#2f7d3f", "#6cc17e"},
            "orange", new String[]{"#c2501a", "#f08a55"},
            "red", new String[]{"#d8281e", "#ff5347"},
            "pink", new String[]{"#b8387b", "#ec7fb4"},
            "rose", new String[]{"#e0105c", "#ff4a86"},
            "violet", new String[]{"#6a4bc4", "#a58cf0"},
            "graphite", new String[]{"#44484e", "#c3c7ce"});

    /** "light" or "dark". */
    private String scheme;
    /** The accent as {@code #rrggbb} for that scheme. */
    private String accent;
    /** The window background, {@code 0xRRGGBB}: the client's side-bar colour. */
    private int background;

    public static Appearance defaults() {
        return new Appearance("dark", ACCENTS.get(DEFAULT_ACCENT)[1], DEFAULT_BACKGROUND);
    }

    public static Appearance read(Path configDirectory) {
        Path file = configDirectory.resolve("settings.json");
        if (!Files.isRegularFile(file)) {
            return defaults();
        }
        try (Reader in = Files.newBufferedReader(file)) {
            JsonElement root = JsonParser.parseReader(in);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("appearance")
                    || !root.getAsJsonObject().get("appearance").isJsonObject()) {
                return defaults();
            }
            JsonObject o = root.getAsJsonObject().getAsJsonObject("appearance");
            String scheme = o.has("scheme") && o.get("scheme").isJsonPrimitive() ? o.get("scheme").getAsString() : "dark";
            if (!scheme.equals("light") && !scheme.equals("dark")) {
                scheme = "dark";
            }
            String key = o.has("accent") && o.get("accent").isJsonPrimitive() ? o.get("accent").getAsString() : DEFAULT_ACCENT;
            String[] colours = ACCENTS.getOrDefault(key, ACCENTS.get(DEFAULT_ACCENT));
            int background = o.has("background") && o.get("background").isJsonPrimitive()
                    ? o.get("background").getAsInt() & 0xFFFFFF : DEFAULT_BACKGROUND;
            return new Appearance(scheme, colours[scheme.equals("dark") ? 1 : 0], background);
        } catch (IOException | RuntimeException e) {
            log.debug("Could not read the saved appearance: {}", e.toString());
            return defaults();
        }
    }
}
