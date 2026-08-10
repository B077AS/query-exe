package com.queryexe.service;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.model.drivers.DriverInfo;
import com.queryexe.model.drivers.DynamicDriverLoader;

@Slf4j
public class DatabaseConnection {

    private static DatabaseConnection instance;
    private final DynamicDriverLoader driverLoader;
    private final Map<String, ConnectionObject> connectionObjects;
    private final Map<String, Connection> connections;
    private String currentConnectionId;

    private DatabaseConnection() {
        this.driverLoader = DynamicDriverLoader.getInstance();
        this.connectionObjects = new ConcurrentHashMap<>();
        this.connections = new ConcurrentHashMap<>();
        this.currentConnectionId = null;
    }

    public static DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    public void initialize(ConnectionObject connectionObject) throws SQLException {
        initialize(connectionObject, false);
    }

    public void initialize(ConnectionObject connectionObject, boolean forceCustomDriver) throws SQLException {
        String connectionId = connectionObject.getId();

        // Check if connection already exists
        if (connections.containsKey(connectionId)) {
            // Connection already initialized, just set as current
            this.currentConnectionId = connectionId;
            return;
        }

        // Check if we need to load a custom driver
        if (forceCustomDriver || shouldUseCustomDriver(connectionObject)) {
            ConnectionTypes type = ConnectionTypes.valueOf(connectionObject.getDbType());
            DriverInfo customDriver = connectionObject.getDriverInfo();

            // Only load custom driver if it's different from the default
            if (!customDriver.equals(type.getDefaultDriverInfo())) {
                boolean loaded = driverLoader.loadCustomDriver(customDriver, type);
                if (!loaded) {
                    log.error("Failed to load custom driver, falling back to default");
                }
            }
        }

        Connection connection = DriverManager.getConnection(
                connectionObject.getFullUrl(),
                connectionObject.getUsername(),
                connectionObject.getPassword()
        );

        connectionObjects.put(connectionId, connectionObject);
        connections.put(connectionId, connection);

        this.currentConnectionId = connectionId;
    }

    private boolean shouldUseCustomDriver(ConnectionObject connectionObject) {
        if (connectionObject.getDriverInfo() == null) {
            return false;
        }

        ConnectionTypes type = ConnectionTypes.valueOf(connectionObject.getDbType());
        DriverInfo defaultDriver = type.getDefaultDriverInfo();
        DriverInfo connectionDriver = connectionObject.getDriverInfo();

        return !connectionDriver.equals(defaultDriver);
    }

    public void setCurrentConnectionId(String connectionId) throws IllegalArgumentException {
        if (!connectionObjects.containsKey(connectionId)) {
            throw new IllegalArgumentException("Connection ID not found: " + connectionId);
        }
        this.currentConnectionId = connectionId;
    }

    public String getCurrentConnectionId() {
        return currentConnectionId;
    }

    public ConnectionObject getCurrentConnectionObject() {
        return connectionObjects.get(currentConnectionId);
    }

    public ConnectionObject getConnectionObject() {
        if (currentConnectionId == null) {
            return null;
        }
        return connectionObjects.get(currentConnectionId);
    }

    public ConnectionObject getConnectionObject(String connectionId) {
        return connectionObjects.get(connectionId);
    }

    public Connection getConnection() {
        if (currentConnectionId == null) {
            return null;
        }
        return connections.get(currentConnectionId);
    }

    public Connection getConnection(String connectionId) {
        return connections.get(connectionId);
    }

    public void setConnectionObject(ConnectionObject connectionObject) {
        if (currentConnectionId == null) {
            throw new IllegalStateException("No current connection set");
        }
        connectionObjects.put(currentConnectionId, connectionObject);
    }

    public void setConnection(Connection connection) {
        if (currentConnectionId == null) {
            throw new IllegalStateException("No current connection set");
        }
        connections.put(currentConnectionId, connection);
    }

    public boolean hasConnection(String connectionId) {
        return connectionObjects.containsKey(connectionId);
    }

    public java.util.Set<String> getAllConnectionIds() {
        return connectionObjects.keySet();
    }

    public boolean switchDriver(DriverInfo driverInfo, ConnectionTypes connectionType) {
        return driverLoader.loadCustomDriver(driverInfo, connectionType);
    }

    public void revertToDefaultDriver(ConnectionTypes connectionType) {
        driverLoader.unloadDriver(connectionType.name());
    }

    public boolean isUsingCustomDriver(ConnectionTypes connectionType) {
        return driverLoader.isCustomDriverLoaded(connectionType);
    }

    public void reconnect() throws SQLException {
        if (currentConnectionId == null) {
            throw new SQLException("No current connection set for reconnection");
        }
        reconnect(currentConnectionId);
    }

    public void reconnect(String connectionId) throws SQLException {
        ConnectionObject connectionObject = connectionObjects.get(connectionId);
        if (connectionObject == null) {
            throw new SQLException("No connection object available for reconnection: " + connectionId);
        }

        Connection connection = connections.get(connectionId);
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
            }
            connections.remove(connectionId);
        }

        String previousConnectionId = currentConnectionId;

        initialize(connectionObject, true);

        if (previousConnectionId != null && !previousConnectionId.equals(connectionId)) {
            currentConnectionId = previousConnectionId;
        }
    }

    public void close() {
        if (currentConnectionId != null) {
            close(currentConnectionId);
        }
    }

    public void close(String connectionId) {
        try {
            Connection connection = connections.get(connectionId);
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            connections.remove(connectionId);
            connectionObjects.remove(connectionId);
            
            // If we closed the current connection, clear the current ID
            if (connectionId.equals(currentConnectionId)) {
                currentConnectionId = null;
            }
        } catch (SQLException e) {
            log.error("Error closing connection: " + e.getMessage());
        }
    }

    public void closeAll() {
        for (String connectionId : new java.util.HashSet<>(connections.keySet())) {
            close(connectionId);
        }
        currentConnectionId = null;
    }

    public void shutdown() {
        closeAll();
        driverLoader.cleanup();
    }
}