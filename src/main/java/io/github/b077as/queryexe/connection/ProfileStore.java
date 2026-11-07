package io.github.b077as.queryexe.connection;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import io.github.b077as.queryexe.connection.ConnectionProfile;
import io.github.b077as.queryexe.connection.ConnectionProfileDeserializer;
import io.github.b077as.queryexe.Launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
public class ProfileStore {

    private static volatile ProfileStore instance;
    private Path connectionsPath;
    private Gson gson;
    @lombok.Getter
    private PasswordVault vault;

    private ProfileStore() {
        this.connectionsPath = Launcher.getDataDirectory().resolve("connections.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        this.vault = new PasswordVault();

        migratePasswordsIfNeeded();
    }

    public static ProfileStore getInstance() {
        if (instance == null) {
            synchronized (ProfileStore.class) {
                if (instance == null) {
                    instance = new ProfileStore();
                }
            }
        }
        return instance;
    }

    private void migratePasswordsIfNeeded() {
        if (!vault.isAvailable() || !Files.exists(connectionsPath)) {
            return;
        }

        try {
            JsonObject connections = readConnections();
            boolean needsMigration = false;

            for (Map.Entry<String, JsonElement> entry : connections.entrySet()) {
                JsonObject conn = entry.getValue().getAsJsonObject();
                if (conn.has("password") && !conn.get("password").isJsonNull()) {
                    needsMigration = true;
                    break;
                }
            }

            if (needsMigration) {
                log.info("Migrating passwords to secure storage...");
                migratePasswordsToSecureStorage();
            }
        } catch (Exception e) {
            log.error("Error checking for password migration", e);
        }
    }

    private void migratePasswordsToSecureStorage() {
        if (!vault.isAvailable()) {
            log.error("Cannot migrate passwords: credential store not available");
            return;
        }

        JsonObject connections = readConnections();
        boolean modified = false;

        for (Map.Entry<String, JsonElement> entry : connections.entrySet()) {
            String connectionId = entry.getKey();
            JsonObject conn = entry.getValue().getAsJsonObject();

            if (conn.has("password") && !conn.get("password").isJsonNull()) {
                String password = conn.get("password").getAsString();
                String username = conn.has("username") ? conn.get("username").getAsString() : "";

                if (password != null && !password.isEmpty()) {
                    vault.store(connectionId, username, password);

                    conn.remove("password");
                    modified = true;

                    log.info("Migrated password for connection: {}", connectionId);
                }
            }
        }

        if (modified) {
            try {
                writeConnections(connections);
                log.info("Password migration completed successfully");
            } catch (IOException e) {
                log.error("Error saving connections after migration", e);
            }
        }
    }

    private static String sshKey(String connectionId) {
        return connectionId + ".ssh";
    }

    /** Puts the SSH secret from the credential store into a connection's JSON, when it has an ssh section. */
    private void addSshSecret(String connectionId, JsonObject json) {
        if (json.has("ssh")) {
            String secret = vault.retrieve(sshKey(connectionId));
            if (secret != null) {
                json.addProperty("sshPassword", secret);
            }
        }
    }

    private JsonObject readConnections() {
        if (!Files.exists(connectionsPath)) {
            return new JsonObject();
        }

        try (Reader reader = Files.newBufferedReader(connectionsPath)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (IOException e) {
            log.error("Error reading connections file", e);
            return new JsonObject();
        }
    }

    private void writeConnections(JsonObject connections) throws IOException {
        Files.createDirectories(connectionsPath.getParent());
        try (Writer writer = Files.newBufferedWriter(connectionsPath)) {
            gson.toJson(connections, writer);
        }
    }

    private void modifyConnections(Consumer<JsonObject> modifier) {
        try {
            JsonObject connections = readConnections();
            modifier.accept(connections);
            writeConnections(connections);
        } catch (IOException e) {
            log.error("Error saving connections", e);
            throw new RuntimeException("Failed to save connections", e);
        }
    }

    public List<ConnectionProfile> loadConnections() {
        List<ConnectionProfile> connections = new ArrayList<>();

        if (!Files.exists(connectionsPath)) {
            log.debug("No connections file found at: {}", connectionsPath);
            return connections;
        }

        try (BufferedReader reader = Files.newBufferedReader(connectionsPath)) {
            Gson gsonWithDeserializer = new GsonBuilder()
                    .registerTypeAdapter(ConnectionProfile.class, new ConnectionProfileDeserializer())
                    .create();

            JsonObject connectionsJson = JsonParser.parseReader(reader).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : connectionsJson.entrySet()) {
                String connectionId = entry.getKey();
                JsonObject element = entry.getValue().getAsJsonObject();
                element.addProperty("id", connectionId);

                String username = element.has("username") ? element.get("username").getAsString() : "";
                String password = vault.retrieve(connectionId);

                if (password != null) {
                    element.addProperty("password", password);
                }
                addSshSecret(connectionId, element);

                ConnectionProfile connection = gsonWithDeserializer.fromJson(element, ConnectionProfile.class);
                connections.add(connection);
            }
        } catch (Exception e) {
            log.error("Error loading connections", e);
        }

        return connections;
    }

    public ConnectionProfile loadConnection(String connectionId) {
        JsonObject element = getConnection(connectionId);
        if (element == null) {
            return null;
        }

        element.addProperty("id", connectionId);
        addSshSecret(connectionId, element);

        try {
            Gson gsonWithDeserializer = new GsonBuilder()
                    .registerTypeAdapter(ConnectionProfile.class, new ConnectionProfileDeserializer())
                    .create();
            return gsonWithDeserializer.fromJson(element, ConnectionProfile.class);
        } catch (Exception e) {
            log.error("Error loading connection {}", connectionId, e);
            return null;
        }
    }

    public void saveConnection(String connectionId, JsonObject connectionData) {
        String password = null;
        if (connectionData.has("password") && !connectionData.get("password").isJsonNull()) {
            password = connectionData.get("password").getAsString();
            connectionData.remove("password");
        }

        String username = connectionData.has("username") ? connectionData.get("username").getAsString() : "";
        String sshSecret = connectionData.has("sshPassword") && !connectionData.get("sshPassword").isJsonNull()
                ? connectionData.get("sshPassword").getAsString() : null;
        connectionData.remove("sshPassword");
        boolean usesSsh = connectionData.has("ssh");

        modifyConnections(connections -> {
            // The edit dialog knows nothing about home page organisation; keep it across edits.
            JsonObject previous = connections.has(connectionId) ? connections.getAsJsonObject(connectionId) : null;
            if (previous != null) {
                for (String key : new String[]{"folder", "pinned"}) {
                    if (!connectionData.has(key) && previous.has(key)) {
                        connectionData.add(key, previous.get(key));
                    }
                }
            }
            connections.add(connectionId, connectionData);
        });

        if (password != null && !password.isEmpty()) {
            vault.store(connectionId, username, password);
        }
        if (!usesSsh) {
            vault.forget(sshKey(connectionId));
        } else if (sshSecret != null && !sshSecret.isEmpty()) {
            vault.store(sshKey(connectionId), "", sshSecret);
        }
    }

    public JsonObject getConnection(String connectionId) {
        JsonObject connections = readConnections();
        if (connections.has(connectionId)) {
            JsonObject connection = connections.getAsJsonObject(connectionId).deepCopy();

            String username = connection.has("username") ? connection.get("username").getAsString() : "";
            String password = vault.retrieve(connectionId);

            if (password != null) {
                connection.addProperty("password", password);
            }
            addSshSecret(connectionId, connection);

            return connection;
        }
        return null;
    }

    /** Files a connection under a folder (null = ungrouped). */
    public void setConnectionFolder(String connectionId, String folder) {
        modifyConnections(connections -> {
            JsonObject connection = connections.has(connectionId) ? connections.getAsJsonObject(connectionId) : null;
            if (connection == null) {
                return;
            }
            if (folder == null || folder.isBlank()) {
                connection.remove("folder");
            } else {
                connection.addProperty("folder", folder);
            }
        });
    }

    public void setConnectionPinned(String connectionId, boolean pinned) {
        modifyConnections(connections -> {
            JsonObject connection = connections.has(connectionId) ? connections.getAsJsonObject(connectionId) : null;
            if (connection == null) {
                return;
            }
            if (pinned) {
                connection.addProperty("pinned", true);
            } else {
                connection.remove("pinned");
            }
        });
    }

    /** Renames a folder on every connection in it, or ungroups them when {@code newName} is null. */
    public void renameFolder(String oldName, String newName) {
        modifyConnections(connections -> {
            for (Map.Entry<String, JsonElement> entry : connections.entrySet()) {
                JsonObject connection = entry.getValue().getAsJsonObject();
                if (connection.has("folder") && oldName.equals(connection.get("folder").getAsString())) {
                    if (newName == null) {
                        connection.remove("folder");
                    } else {
                        connection.addProperty("folder", newName);
                    }
                }
            }
        });
    }

    public String cloneConnection(String connectionId, String newName) {
        String newId = UUID.randomUUID().toString();

        JsonObject originalConnection = getConnection(connectionId);

        if (originalConnection != null) {
            String password = null;
            if (originalConnection.has("password") && !originalConnection.get("password").isJsonNull()) {
                password = originalConnection.get("password").getAsString();
                originalConnection.remove("password");
            }

            String sshSecret = originalConnection.has("sshPassword") ? originalConnection.get("sshPassword").getAsString() : null;
            originalConnection.remove("sshPassword");

            modifyConnections(connections -> {
                JsonObject newConnection = originalConnection.deepCopy();
                newConnection.addProperty("connectionName", newName);
                connections.add(newId, newConnection);
            });

            if (password != null && !password.isEmpty()) {
                String username = originalConnection.has("username") ?
                        originalConnection.get("username").getAsString() : "";
                vault.store(newId, username, password);
            }
            if (sshSecret != null && !sshSecret.isEmpty()) {
                vault.store(sshKey(newId), "", sshSecret);
            }
        }

        return newId;
    }

    public boolean deleteConnection(String connectionId) {
        try {
            vault.forget(connectionId);
            vault.forget(sshKey(connectionId));

            JsonObject connections = readConnections();

            if (connections.has(connectionId)) {
                connections.remove(connectionId);
                writeConnections(connections);
                return true;
            }

            log.warn("Connection ID not found: {}", connectionId);
            return false;
        } catch (IOException e) {
            log.error("Error deleting connection", e);
            return false;
        }
    }
}
