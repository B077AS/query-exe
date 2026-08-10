package com.queryexe.service;

import com.google.gson.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.layout.StackPane;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.modals.InsertPasswordModal;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.model.connections.ConnectionObjectDeserializer;
import com.queryexe.queryexe.App;
import com.queryexe.queryexe.Launcher;
import com.microsoft.credentialstorage.SecretStore;
import com.microsoft.credentialstorage.StorageProvider;
import com.microsoft.credentialstorage.StorageProvider.SecureOption;
import com.microsoft.credentialstorage.model.StoredCredential;

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
public class ConnectionService {

    private static volatile ConnectionService instance;
    private Task<Void> connectTask;
    private final Path connectionsPath;
    private final Gson gson;
    private final SecretStore<StoredCredential> credentialStore;
    private static final String CREDENTIAL_KEY_PREFIX = "QueryExe_Connection_";

    private ConnectionService() {
        this.connectionsPath = Launcher.getDataDirectory().resolve("connections.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        this.credentialStore = StorageProvider.getCredentialStorage(true, SecureOption.REQUIRED);

        if (credentialStore == null) {
            log.warn("No secure credential storage available!");
        } else {
            log.info("Secure credential storage initialized successfully");
        }

        migratePasswordsIfNeeded();
    }

    public static ConnectionService getInstance() {
        if (instance == null) {
            synchronized (ConnectionService.class) {
                if (instance == null) {
                    instance = new ConnectionService();
                }
            }
        }
        return instance;
    }

    private void migratePasswordsIfNeeded() {
        if (credentialStore == null || !Files.exists(connectionsPath)) {
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
        if (credentialStore == null) {
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
                    storePassword(connectionId, username, password);

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

    private void storePassword(String connectionId, String username, String password) {
        if (credentialStore == null || password == null || password.isEmpty()) {
            return;
        }

        try {
            StoredCredential credential = new StoredCredential(username, password.toCharArray());
            credentialStore.add(getCredentialKey(connectionId), credential);
        } catch (Exception e) {
            log.error("Error storing password for connection {}", connectionId, e);
        }
    }

    private String retrievePassword(String connectionId, String username) {
        if (credentialStore == null) {
            return null;
        }

        try {
            StoredCredential credential = credentialStore.get(getCredentialKey(connectionId));
            if (credential != null) {
                char[] passwordChars = credential.getPassword();
                return passwordChars != null ? new String(passwordChars) : null;
            }
        } catch (Exception e) {
            log.error("Error retrieving password for connection {}", connectionId, e);
        }

        return null;
    }

    private void deletePassword(String connectionId) {
        if (credentialStore == null) {
            return;
        }

        try {
            credentialStore.delete(getCredentialKey(connectionId));
        } catch (Exception e) {
            log.error("Error deleting password for connection {}", connectionId, e);
        }
    }


    private String getCredentialKey(String connectionId) {
        return CREDENTIAL_KEY_PREFIX + connectionId;
    }

    public void connect(ConnectionObject connection, Runnable onConnectionStart, Runnable onConnectionEnd, Runnable onSuccess, Consumer<Exception> onError) {

        if (connection.getPassword() == null) {
            InsertPasswordModal insertPasswordModal = new InsertPasswordModal(
                    connection,
                    () -> attemptConnection(connection, onConnectionStart, onConnectionEnd, onSuccess, onError, true)
            );
            insertPasswordModal.setTranslateY(10);
            Platform.runLater(() -> {
                App.showModalOnTop(insertPasswordModal);
            });
            return;
        }
        attemptConnection(connection, onConnectionStart, onConnectionEnd, onSuccess, onError, false);
    }

    private void attemptConnection(ConnectionObject connection, Runnable onConnectionStart, Runnable onConnectionEnd, Runnable onSuccess, Consumer<Exception> onError, boolean isUserInserted) {

        // Only one connection attempt at a time; it can be relaunched once it finished.
        if (connectTask != null && connectTask.isRunning()) {
            return;
        }

        if (onConnectionStart != null) {
            Platform.runLater(onConnectionStart);
        }

        connectTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                DatabaseConnection.getInstance().initialize(connection);
                // The success callback stays on the background thread: callers build
                // the database tree here, which performs blocking JDBC metadata calls.
                if (onSuccess != null) {
                    onSuccess.run();
                }
                return null;
            }
        };

        connectTask.setOnFailed(event -> {
            Throwable e = connectTask.getException();
            handleConnectionError(e instanceof Exception ex ? ex : new Exception(e),
                    connection, isUserInserted, onConnectionStart, onConnectionEnd, onSuccess, onError);
            if (onConnectionEnd != null) {
                onConnectionEnd.run();
            }
        });
        connectTask.setOnSucceeded(event -> {
            if (onConnectionEnd != null) {
                onConnectionEnd.run();
            }
        });

        Async.run(connectTask);
    }

    private void handleConnectionError(Exception e, ConnectionObject connection, boolean isUserInserted, Runnable onConnectionStart, Runnable onConnectionEnd, Runnable onSuccess, Consumer<Exception> onError) {
        log.error("Connection attempt failed for '{}'", connection.getConnectionName(), e);

        Platform.runLater(() -> {

            InsertPasswordModal insertPasswordModal = null;
            if (isUserInserted) {
                connection.setPassword(null);
                insertPasswordModal = new InsertPasswordModal(
                        connection,
                        () -> attemptConnection(connection, onConnectionStart, onConnectionEnd, onSuccess, onError, true)
                );
                insertPasswordModal.setTranslateY(10);
                App.showModalOnTop(insertPasswordModal);
            } else {
                if (onError != null) {
                    onError.accept(e);
                }
            }

            CustomNotification customNotification = new CustomNotification("Connection Failed", e.getMessage(), new FontIcon(MaterialDesignL.LAN_DISCONNECT));

            if (insertPasswordModal != null) {
                customNotification.showNotificationOnCustomPane((StackPane) insertPasswordModal.getParent());
            } else {
                customNotification.showNotification();
            }
        });
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

    public List<ConnectionObject> loadConnections() {
        List<ConnectionObject> connections = new ArrayList<>();

        if (!Files.exists(connectionsPath)) {
            log.debug("No connections file found at: {}", connectionsPath);
            return connections;
        }

        try (BufferedReader reader = Files.newBufferedReader(connectionsPath)) {
            Gson gsonWithDeserializer = new GsonBuilder()
                    .registerTypeAdapter(ConnectionObject.class, new ConnectionObjectDeserializer())
                    .create();

            JsonObject connectionsJson = JsonParser.parseReader(reader).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : connectionsJson.entrySet()) {
                String connectionId = entry.getKey();
                JsonObject element = entry.getValue().getAsJsonObject();
                element.addProperty("id", connectionId);

                String username = element.has("username") ? element.get("username").getAsString() : "";
                String password = retrievePassword(connectionId, username);

                if (password != null) {
                    element.addProperty("password", password);
                }

                ConnectionObject connection = gsonWithDeserializer.fromJson(element, ConnectionObject.class);
                connections.add(connection);
            }
        } catch (Exception e) {
            log.error("Error loading connections", e);
        }

        return connections;
    }

    public ConnectionObject loadConnection(String connectionId) {
        JsonObject element = getConnection(connectionId);
        if (element == null) {
            return null;
        }

        element.addProperty("id", connectionId);

        try {
            Gson gsonWithDeserializer = new GsonBuilder()
                    .registerTypeAdapter(ConnectionObject.class, new ConnectionObjectDeserializer())
                    .create();
            return gsonWithDeserializer.fromJson(element, ConnectionObject.class);
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

        modifyConnections(connections -> {
            connections.add(connectionId, connectionData);
        });

        if (password != null && !password.isEmpty()) {
            storePassword(connectionId, username, password);
        }
    }

    public JsonObject getConnection(String connectionId) {
        JsonObject connections = readConnections();
        if (connections.has(connectionId)) {
            JsonObject connection = connections.getAsJsonObject(connectionId).deepCopy();

            String username = connection.has("username") ? connection.get("username").getAsString() : "";
            String password = retrievePassword(connectionId, username);

            if (password != null) {
                connection.addProperty("password", password);
            }

            return connection;
        }
        return null;
    }

    public void updateConnectionProperty(String connectionId, String property, String value) {
        if ("password".equals(property)) {
            JsonObject connections = readConnections();
            JsonObject connection = connections.getAsJsonObject(connectionId);
            if (connection != null) {
                String username = connection.has("username") ? connection.get("username").getAsString() : "";
                storePassword(connectionId, username, value);
            }
            return;
        }

        modifyConnections(connections -> {
            JsonObject connection = connections.getAsJsonObject(connectionId);
            if (connection != null) {
                connection.addProperty(property, value);
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

            modifyConnections(connections -> {
                JsonObject newConnection = originalConnection.deepCopy();
                newConnection.addProperty("connectionName", newName);
                connections.add(newId, newConnection);
            });

            if (password != null && !password.isEmpty()) {
                String username = originalConnection.has("username") ?
                        originalConnection.get("username").getAsString() : "";
                storePassword(newId, username, password);
            }
        }

        return newId;
    }

    public boolean deleteConnection(String connectionId) {
        try {
            deletePassword(connectionId);

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

    public void forceMigration() {
        migratePasswordsToSecureStorage();
    }
}