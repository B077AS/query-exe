package com.queryexe.service;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    // Extra connections handed to query tabs, per connection id; closed together with the shared one.
    private final Map<String, List<Connection>> sessions;
    private String currentConnectionId;

    private DatabaseConnection() {
        this.driverLoader = DynamicDriverLoader.getInstance();
        this.connectionObjects = new ConcurrentHashMap<>();
        this.connections = new ConcurrentHashMap<>();
        this.sessions = new ConcurrentHashMap<>();
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

        Connection connection = open(connectionObject, forceCustomDriver);

        connectionObjects.put(connectionId, connectionObject);
        connections.put(connectionId, connection);

        this.currentConnectionId = connectionId;
    }

    private Connection open(ConnectionObject connectionObject, boolean forceCustomDriver) throws SQLException {
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

        return DriverManager.getConnection(
                connectionObject.getFullUrl(),
                connectionObject.getUsername(),
                connectionObject.getPassword()
        );
    }

    /**
     * A connection of its own for one query tab, so its transactions and {@code USE}/{@code SET} state don't leak
     * into other tabs or the schema tree. The shared connection must already be open. Databases that only exist
     * inside a single connection (unnamed in-memory H2/SQLite) get the shared one back. Release it with
     * {@link #closeSession}.
     */
    public Connection openSession(ConnectionObject connectionObject) throws SQLException {
        Connection shared = connections.get(connectionObject.getId());
        if (shared == null) {
            throw new SQLException("Connection is not open: " + connectionObject.getConnectionName());
        }
        if (isPrivateToConnection(connectionObject.getFullUrl())) {
            return shared;
        }

        Connection session = open(connectionObject, false);
        sessions.computeIfAbsent(connectionObject.getId(), id -> new CopyOnWriteArrayList<>()).add(session);
        return session;
    }

    public void closeSession(String connectionId, Connection session) {
        List<Connection> open = sessions.get(connectionId);
        if (open == null || session == null || !open.remove(session)) {
            return; // not a session (e.g. the shared connection handed out for in-memory databases)
        }
        closeQuietly(session);
    }

    /**
     * Makes a session use the database/schema the shared connection is currently on, which is where the schema tree
     * selects: the tree issues its {@code USE} on the shared connection only.
     */
    public String copyDefaultSchema(Connection shared, Connection session, String previousState) {
        if (shared == null || session == null || shared == session) {
            return previousState;
        }
        String catalog = null;
        String schema = null;
        try {
            catalog = shared.getCatalog();
        } catch (SQLException | RuntimeException e) {
            log.debug("Could not read the catalog: {}", e.getMessage());
        }
        try {
            schema = shared.getSchema();
        } catch (SQLException | RuntimeException e) {
            log.debug("Could not read the schema: {}", e.getMessage());
        }

        // Only act when the tree moved since the last copy, so a USE typed in the tab isn't overwritten on every run.
        String state = catalog + "\u0000" + schema;
        if (state.equals(previousState)) {
            return previousState;
        }
        try {
            if (catalog != null && !catalog.equals(session.getCatalog())) {
                session.setCatalog(catalog);
            }
        } catch (SQLException | RuntimeException e) {
            log.debug("Could not copy the catalog to a session: {}", e.getMessage());
        }
        try {
            if (schema != null && !schema.equals(session.getSchema())) {
                session.setSchema(schema);
            }
        } catch (SQLException | RuntimeException e) {
            log.debug("Could not copy the schema to a session: {}", e.getMessage());
        }
        return state;
    }

    /** The shared connection of a connection id, for {@link #copyDefaultSchema}. */
    public Connection sharedConnection(String connectionId) {
        return connections.get(connectionId);
    }

    private static boolean isPrivateToConnection(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:sqlite:")) {
            String path = lower.substring("jdbc:sqlite:".length());
            return path.isEmpty() || path.startsWith(":memory:") || path.startsWith("file::memory:");
        }
        if (lower.startsWith("jdbc:h2:mem:")) {
            // a named in-memory database is shared by every connection; an unnamed one is private
            String rest = lower.substring("jdbc:h2:mem:".length());
            return rest.isEmpty() || rest.startsWith(";");
        }
        return false;
    }

    private static void closeQuietly(Connection connection) {
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.debug("Error closing session: {}", e.getMessage());
        }
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

        List<Connection> openSessions = sessions.remove(connectionId);
        if (openSessions != null) {
            openSessions.forEach(DatabaseConnection::closeQuietly);
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
        List<Connection> openSessions = sessions.remove(connectionId);
        if (openSessions != null) {
            openSessions.forEach(DatabaseConnection::closeQuietly);
        }
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