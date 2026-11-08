package io.github.b077as.queryexe.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class ConnectionBackupTest {

    private static JsonObject sample() {
        JsonObject connection = new JsonObject();
        connection.addProperty("connectionName", "prod");
        connection.addProperty("password", "s3cret-pw");
        JsonObject all = new JsonObject();
        all.add("id-1", connection);
        return ConnectionBackup.payload(all);
    }

    @Test
    void encryptedRoundTripKeepsPasswordsOutOfTheFile() throws Exception {
        String file = ConnectionBackup.write(sample(), "correct horse");
        assertFalse(file.contains("s3cret-pw"));
        JsonObject back = ConnectionBackup.read(file, "correct horse");
        assertEquals("s3cret-pw", back.getAsJsonObject("connections").getAsJsonObject("id-1").get("password").getAsString());
    }

    @Test
    void wrongPassphraseAndMissingPassphraseAreRefused() throws Exception {
        String file = ConnectionBackup.write(sample(), "correct horse");
        assertThrows(IllegalArgumentException.class, () -> ConnectionBackup.read(file, "wrong one"));
        assertThrows(IllegalArgumentException.class, () -> ConnectionBackup.read(file, ""));
    }

    @Test
    void plainFileAndForeignFile() throws Exception {
        JsonObject back = ConnectionBackup.read(ConnectionBackup.write(sample(), ""), "");
        assertEquals("prod", back.getAsJsonObject("connections").getAsJsonObject("id-1").get("connectionName").getAsString());
        assertThrows(IllegalArgumentException.class, () -> ConnectionBackup.read("{\"a\":1}", ""));
        assertThrows(IllegalArgumentException.class, () -> ConnectionBackup.read("not json", ""));
    }
}
