package io.github.b077as.queryexe.connection;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import io.github.b077as.queryexe.connection.ConnectionProfile;
import io.github.b077as.queryexe.connection.ConnectionTypes;
import io.github.b077as.queryexe.connection.driver.DriverInfo;
import io.github.b077as.queryexe.connection.driver.DynamicDriverLoader;

@Slf4j
public class DatabaseConnection {

    private static DatabaseConnection instance;
    private DynamicDriverLoader driverLoader;
    private Map<String, ConnectionProfile> connectionObjects;
    private Map<String, Connection> connections;
    // Extra connections handed to query tabs, per connection id; closed together with the shared one.
    private Map<String, List<Connection>> sessions;

    private DatabaseConnection() {
        this.driverLoader = DynamicDriverLoader.getInstance();
        this.connectionObjects = new ConcurrentHashMap<>();
        this.connections = new ConcurrentHashMap<>();
        this.sessions = new ConcurrentHashMap<>();
    }

    public static DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    public void initialize(ConnectionProfile connectionObject) throws SQLException {
        initialize(connectionObject, false);
    }

    public void initialize(ConnectionProfile connectionObject, boolean forceCustomDriver) throws SQLException {
        String connectionId = connectionObject.getId();

        // Check if connection already exists
        if (connections.containsKey(connectionId)) {
            return;
        }

        Connection connection = open(connectionObject, forceCustomDriver);

        connectionObjects.put(connectionId, connectionObject);
        connections.put(connectionId, connection);

    }

    /** Opens a connection to check that the definition works, then closes it again. */
    public void test(ConnectionProfile connectionObject) throws SQLException {
        try (Connection connection = open(connectionObject, false)) {
            connection.isValid(5);
        }
    }

    private Connection open(ConnectionProfile connectionObject, boolean forceCustomDriver) throws SQLException {
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
    public Connection openSession(ConnectionProfile connectionObject) throws SQLException {
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

    /** How many extra connections (one per query tab that has run something) are open for this connection. */
    public int sessionCount(String connectionId) {
        List<Connection> open = sessions.get(connectionId);
        return open == null ? 0 : open.size();
    }

    /** Whether the shared connection still answers (a quick check; false when it is closed or the server went away). */
    public boolean isAlive(String connectionId) {
        Connection connection = connections.get(connectionId);
        try {
            return connection != null && !connection.isClosed() && connection.isValid(3);
        } catch (SQLException | RuntimeException e) {
            return false;
        }
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

    private boolean shouldUseCustomDriver(ConnectionProfile connectionObject) {
        if (connectionObject.getDriverInfo() == null) {
            return false;
        }

        ConnectionTypes type = ConnectionTypes.valueOf(connectionObject.getDbType());
        DriverInfo defaultDriver = type.getDefaultDriverInfo();
        DriverInfo connectionDriver = connectionObject.getDriverInfo();

        return !connectionDriver.equals(defaultDriver);
    }

    public boolean hasConnection(String connectionId) {
        return connectionObjects.containsKey(connectionId);
    }

    public java.util.Set<String> getAllConnectionIds() {
        return connectionObjects.keySet();
    }

    public void closeAll() {
        for (String connectionId : new java.util.HashSet<>(connections.keySet())) {
            close(connectionId);
        }
    }

    public void shutdown() {
        closeAll();
        driverLoader.cleanup();
    }

    /** The saved definition of an open connection, or null when it isn't open. */
    public ConnectionProfile getProfile(String connectionId) {
        return connectionObjects.get(connectionId);
    }

    /** Closes the connection and all its sessions, then opens it again with the same definition. */
    public void reconnect(String connectionId) throws SQLException {
        ConnectionProfile profile = connectionObjects.get(connectionId);
        if (profile == null) {
            throw new SQLException("No connection object available for reconnection: " + connectionId);
        }
        List<Connection> openSessions = sessions.remove(connectionId);
        if (openSessions != null) {
            openSessions.forEach(DatabaseConnection::closeQuietly);
        }
        Connection connection = connections.remove(connectionId);
        if (connection != null) {
            closeQuietly(connection);
        }
        initialize(profile, true);
    }

    public void close(String connectionId) {
        List<Connection> openSessions = sessions.remove(connectionId);
        if (openSessions != null) {
            openSessions.forEach(DatabaseConnection::closeQuietly);
        }
        Connection connection = connections.remove(connectionId);
        if (connection != null) {
            closeQuietly(connection);
        }
        connectionObjects.remove(connectionId);
    }
}
