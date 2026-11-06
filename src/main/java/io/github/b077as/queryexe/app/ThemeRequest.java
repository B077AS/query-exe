package io.github.b077as.queryexe.app;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.b077as.jolt.ColorScheme;

import java.util.regex.Pattern;

/**
 * What the page sends to restyle the native window: {@code {preference, accent, scheme, background, text, accentColor, onAccent}}.
 *
 * <p>The page is untrusted input, so every field is checked; a bad value fails the request instead of reaching the
 * operating system or the settings file.
 */
@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class ThemeRequest {

    private String preference;
    private String accent;
    private ColorScheme scheme;
    private int background;
    private int text;
    private int accentColor;
    private int onAccent;

    public static final java.util.Set<String> PREFERENCES = java.util.Set.of("system", "light", "dark");
    public static final java.util.Set<String> ACCENTS = java.util.Set.of("blue", "teal", "green", "orange", "red", "pink", "rose", "violet", "graphite");
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    public static ThemeRequest parse(JsonObject params) {
        String preference = string(params, "preference");
        if (!PREFERENCES.contains(preference)) {
            throw new IllegalArgumentException("preference must be system, light or dark: " + preference);
        }
        String accent = string(params, "accent");
        if (!ACCENTS.contains(accent)) {
            throw new IllegalArgumentException("unknown accent: " + accent);
        }
        ColorScheme scheme = switch (string(params, "scheme")) {
            case "light" -> ColorScheme.LIGHT;
            case "dark" -> ColorScheme.DARK;
            default -> throw new IllegalArgumentException("scheme must be light or dark");
        };
        return new ThemeRequest(preference, accent, scheme, color(params, "background"), color(params, "text"),
                color(params, "accentColor"), color(params, "onAccent"));
    }

    public static String string(JsonObject params, String key) {
        JsonElement value = params.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " is required and must be text");
        }
        return value.getAsString();
    }

    private static int color(JsonObject params, String key) {
        String hex = string(params, key);
        if (!HEX_COLOR.matcher(hex).matches()) {
            throw new IllegalArgumentException(key + " must look like #1a2b3c: " + hex);
        }
        return Integer.parseInt(hex.substring(1), 16);
    }
}
