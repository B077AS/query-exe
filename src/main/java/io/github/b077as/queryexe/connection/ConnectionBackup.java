package io.github.b077as.queryexe.connection;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * The file connections are exported to. It is JSON; when it carries passwords (or whenever a passphrase is given) the
 * payload is encrypted with AES-256-GCM under a key derived from the passphrase (PBKDF2-HMAC-SHA256), so a backup
 * never holds a password in the clear.
 */
public final class ConnectionBackup {

    private static final String KIND = "queryexe-connections";
    private static final int VERSION = 1;
    private static final int ITERATIONS = 210_000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConnectionBackup() {
    }

    /** What was in a backup file: the connections by id (secrets included when the export had them) and their folders. */
    public static JsonObject payload(JsonObject connections) {
        JsonObject out = new JsonObject();
        out.add("connections", connections);
        return out;
    }

    /** The text of the backup file for a payload; a blank passphrase leaves it readable. */
    public static String write(JsonObject payload, String passphrase) throws GeneralSecurityException {
        JsonObject file = new JsonObject();
        file.addProperty("kind", KIND);
        file.addProperty("version", VERSION);
        if (passphrase == null || passphrase.isEmpty()) {
            file.addProperty("encrypted", false);
            file.add("payload", payload);
            return GSON.toJson(file);
        }
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        random.nextBytes(salt);
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), new GCMParameterSpec(128, iv));
        byte[] data = cipher.doFinal(GSON.toJson(payload).getBytes(StandardCharsets.UTF_8));
        Base64.Encoder b64 = Base64.getEncoder();
        file.addProperty("encrypted", true);
        file.addProperty("kdf", "PBKDF2WithHmacSHA256");
        file.addProperty("iterations", ITERATIONS);
        file.addProperty("salt", b64.encodeToString(salt));
        file.addProperty("iv", b64.encodeToString(iv));
        file.addProperty("data", b64.encodeToString(data));
        return GSON.toJson(file);
    }

    /** Reads a backup file's text back into its payload. */
    public static JsonObject read(String text, String passphrase) throws GeneralSecurityException {
        JsonObject file;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            file = parsed.getAsJsonObject();
            if (!file.has("kind") || !KIND.equals(file.get("kind").getAsString())) {
                throw new IllegalArgumentException("This is not a QueryExe connections file.");
            }
            if (file.get("version").getAsInt() > VERSION) {
                throw new IllegalArgumentException("This file was made by a newer version of QueryExe.");
            }
            if (!file.get("encrypted").getAsBoolean()) {
                return file.getAsJsonObject("payload");
            }
            if (passphrase == null || passphrase.isEmpty()) {
                throw new IllegalArgumentException("This file is encrypted. Enter its passphrase first.");
            }
            Base64.Decoder b64 = Base64.getDecoder();
            int iterations = file.get("iterations").getAsInt();
            if (iterations < 1 || iterations > 5_000_000) {
                throw new IllegalArgumentException("This connections file is damaged.");
            }
            byte[] salt = b64.decode(file.get("salt").getAsString());
            byte[] iv = b64.decode(file.get("iv").getAsString());
            byte[] data = b64.decode(file.get("data").getAsString());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt, iterations), new GCMParameterSpec(128, iv));
            String json = new String(cipher.doFinal(data), StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (AEADBadTagException e) {
            throw new IllegalArgumentException("Wrong passphrase, or the file is damaged.");
        } catch (JsonParseException | IllegalStateException | NullPointerException | ClassCastException e) {
            throw new IllegalArgumentException("This is not a QueryExe connections file.");
        }
    }

    private static SecretKeySpec key(String passphrase, byte[] salt) throws GeneralSecurityException {
        return key(passphrase, salt, ITERATIONS);
    }

    private static SecretKeySpec key(String passphrase, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256);
        try {
            byte[] raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(raw, "AES");
        } finally {
            spec.clearPassword();
        }
    }
}
