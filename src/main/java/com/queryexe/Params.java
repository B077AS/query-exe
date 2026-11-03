package com.queryexe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Reads the parameters of an RPC request. The page is untrusted input: a missing or mistyped field is an error with a clear message. */
public final class Params {

    private Params() {}

    public static String string(JsonObject params, String key) {
        JsonElement value = params.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " is required and must be text");
        }
        return value.getAsString();
    }

    /** Text that may be absent or empty. */
    public static String optionalString(JsonObject params, String key) {
        JsonElement value = params.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }

    /** A list of text that may be absent (then empty). */
    public static List<String> optionalStrings(JsonObject params, String key) {
        JsonElement value = params.get(key);
        if (value == null || value.isJsonNull()) {
            return List.of();
        }
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException(key + " must be a list of text");
        }
        List<String> result = new ArrayList<>();
        for (JsonElement item : value.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(key + " must be a list of text");
            }
            result.add(item.getAsString());
        }
        return result;
    }

    public static int integer(JsonObject params, String key) {
        JsonElement value = params.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException(key + " is required and must be a number");
        }
        try {
            return value.getAsInt();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a whole number");
        }
    }

    public static boolean bool(JsonObject params, String key) {
        JsonElement value = params.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " is required and must be true or false");
        }
        return value.getAsBoolean();
    }
}
