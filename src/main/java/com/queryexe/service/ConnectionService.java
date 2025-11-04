package com.queryexe.service;

import com.google.gson.*;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.modals.InsertPasswordModal;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.model.connections.ConnectionObjectDeserializer;
import com.queryexe.queryexe.App;
import com.queryexe.queryexe.Launcher;

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

public class ConnectionService {

    private static volatile ConnectionService instance;
    private final Path connectionsPath;
    private final Gson gson;

    private ConnectionService() {
        this.connectionsPath = Launcher.getDataDirectory().resolve("connections.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
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

        if (!SingleExecutorService.tryStartRunning()) {
            return;
        }

        if (onConnectionStart != null) {
            Platform.runLater(onConnectionStart);
        }

        SingleExecutorService.getExecutor().execute(() -> {
            try {
                DatabaseConnection.getInstance().initialize(connection);
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                handleConnectionError(e, connection, isUserInserted, onConnectionStart, onConnectionEnd, onSuccess, onError);
            } finally {
                SingleExecutorService.finishRunning();
                if (onConnectionEnd != null) {
                    Platform.runLater(onConnectionEnd);
                }
            }
        });
    }

    private void handleConnectionError(Exception e, ConnectionObject connection, boolean isUserInserted, Runnable onConnectionStart, Runnable onConnectionEnd, Runnable onSuccess, Consumer<Exception> onError) {
        e.printStackTrace();

        Platform.runLater(() -> {

            InsertPasswordModal insertPasswordModal=null;
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

            CustomNotification customNotification = new CustomNotification("Connection failed!\n" + e.getMessage(), new FontIcon(MaterialDesignL.LAN_DISCONNECT));

            if(insertPasswordModal!=null){
                customNotification.showNotificationOnCustomPane((StackPane) insertPasswordModal.getParent());
            }else{
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
            System.err.println("Error reading connections file: " + e.getMessage());
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
            System.err.println("Error saving connections: " + e.getMessage());
            throw new RuntimeException("Failed to save connections", e);
        }
    }

    public List<ConnectionObject> loadConnections() {
        List<ConnectionObject> connections = new ArrayList<>();

        if (!Files.exists(connectionsPath)) {
            System.out.println("No connections file found at: " + connectionsPath);
            return connections;
        }

        try (BufferedReader reader = Files.newBufferedReader(connectionsPath)) {
            Gson gsonWithDeserializer = new GsonBuilder()
                    .registerTypeAdapter(ConnectionObject.class, new ConnectionObjectDeserializer())
                    .create();

            JsonObject connectionsJson = JsonParser.parseReader(reader).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : connectionsJson.entrySet()) {
                JsonObject element = entry.getValue().getAsJsonObject();
                element.addProperty("id", entry.getKey());

                ConnectionObject connection = gsonWithDeserializer.fromJson(element, ConnectionObject.class);
                connections.add(connection);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return connections;
    }

    public void saveConnection(String connectionId, JsonObject connectionData) {
        modifyConnections(connections -> {
            connections.add(connectionId, connectionData);
        });
    }

    public JsonObject getConnection(String connectionId) {
        JsonObject connections = readConnections();
        return connections.has(connectionId) ? connections.getAsJsonObject(connectionId) : null;
    }

    public void updateConnectionProperty(String connectionId, String property, String value) {
        modifyConnections(connections -> {
            JsonObject connection = connections.getAsJsonObject(connectionId);
            if (connection != null) {
                connection.addProperty(property, value);
            }
        });
    }

    public String cloneConnection(String connectionId, String newName) {
        String newId = UUID.randomUUID().toString();

        modifyConnections(connections -> {
            JsonObject existingConnection = connections.getAsJsonObject(connectionId);
            if (existingConnection != null) {
                JsonObject newConnection = existingConnection.deepCopy();
                newConnection.addProperty("connectionName", newName);
                connections.add(newId, newConnection);
            }
        });

        return newId;
    }

    public boolean deleteConnection(String connectionId) {
        try {
            JsonObject connections = readConnections();

            if (connections.has(connectionId)) {
                connections.remove(connectionId);
                writeConnections(connections);
                return true;
            }

            System.out.println("Connection ID not found: " + connectionId);
            return false;
        } catch (IOException e) {
            System.err.println("Error deleting connection: " + e.getMessage());
            return false;
        }
    }
}