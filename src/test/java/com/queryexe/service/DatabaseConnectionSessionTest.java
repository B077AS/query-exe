package com.queryexe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.queryexe.model.connections.H2Connection;
import com.queryexe.model.connections.SQLiteConnection;

/** A query tab's own connection must not share transactions with other tabs, and must follow the tree's schema. */
class DatabaseConnectionSessionTest {

    private String connectionId;

    @AfterEach
    void disconnect() {
        if (connectionId != null) {
            DatabaseConnection.getInstance().close(connectionId);
        }
    }

    private static long count(Connection connection, String table) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void sessionsHaveSeparateTransactions(@TempDir Path dir) throws Exception {
        var object = new SQLiteConnection("session-tx", "s", "SQLite", "jdbc:sqlite:" + dir.resolve("a.db"), "", "", null);
        connectionId = object.getId();
        DatabaseConnection databases = DatabaseConnection.getInstance();
        databases.initialize(object);
        try (Statement s = databases.getConnection(connectionId).createStatement()) {
            s.execute("CREATE TABLE t(id INTEGER PRIMARY KEY)");
        }

        Connection tabA = databases.openSession(object);
        Connection tabB = databases.openSession(object);
        assertNotSame(tabA, tabB);
        assertNotSame(databases.getConnection(connectionId), tabA);

        tabA.setAutoCommit(false);
        try (Statement s = tabA.createStatement()) {
            s.execute("INSERT INTO t VALUES (1)");
        }
        assertEquals(0, count(tabB, "t"), "another tab must not see the uncommitted row");
        tabA.commit();
        assertEquals(1, count(tabB, "t"));

        databases.closeSession(connectionId, tabA);
        assertTrue(tabA.isClosed());
        assertTrue(!tabB.isClosed());

        databases.close(connectionId);
        assertTrue(tabB.isClosed(), "closing the connection closes the tabs' connections too");
        connectionId = null;
    }

    @Test
    void sessionFollowsTheSchemaTheTreeSelected() throws Exception {
        var object = new H2Connection("session-schema", "h2", "H2", "jdbc:h2:mem:sessionschema;DB_CLOSE_DELAY=-1", "sa", "", null);
        connectionId = object.getId();
        DatabaseConnection databases = DatabaseConnection.getInstance();
        databases.initialize(object);
        Connection shared = databases.getConnection(connectionId);
        try (Statement s = shared.createStatement()) {
            s.execute("CREATE SCHEMA OTHER");
        }

        Connection tab = databases.openSession(object);
        String state = databases.copyDefaultSchema(shared, tab, null);
        assertEquals("PUBLIC", tab.getSchema());

        // the tree selects another schema on the shared connection
        shared.setSchema("OTHER");
        state = databases.copyDefaultSchema(shared, tab, state);
        assertEquals("OTHER", tab.getSchema());

        // a SET SCHEMA typed in the tab is kept as long as the tree didn't move
        try (Statement s = tab.createStatement()) {
            s.execute("SET SCHEMA PUBLIC");
        }
        databases.copyDefaultSchema(shared, tab, state);
        assertEquals("PUBLIC", tab.getSchema());
    }

    @Test
    void unnamedInMemoryDatabasesKeepUsingTheSharedConnection() throws Exception {
        var object = new SQLiteConnection("session-mem", "m", "SQLite", "jdbc:sqlite::memory:", "", "", null);
        connectionId = object.getId();
        DatabaseConnection databases = DatabaseConnection.getInstance();
        databases.initialize(object);

        assertSame(databases.getConnection(connectionId), databases.openSession(object));
    }
}
