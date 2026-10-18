package com.queryexe.components.results;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.model.connections.H2Connection;
import com.queryexe.model.connections.SQLiteConnection;
import com.queryexe.model.data.QueryData;
import com.queryexe.model.data.TableRowData;
import com.queryexe.service.ChangeApplier;
import com.queryexe.service.DatabaseConnection;

import javafx.application.Platform;

/**
 * Edits made in the result grid must be turned into correctly targeted, correctly quoted statements.
 * Needs a JavaFX toolkit (TableView); the tests are skipped where none can be started.
 */
class ResultTableEditTest {

    private static boolean toolkitAvailable;

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> {
            });
            toolkitAvailable = true;
        } catch (IllegalStateException alreadyStarted) {
            toolkitAvailable = true;
        } catch (Throwable noToolkit) {
            toolkitAvailable = false;
        }
        Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit not available");
    }

    @AfterAll
    static void noop() {
        // the toolkit stays up for the rest of the JVM
    }

    // ------------------------------------------------------------------------------------ helpers

    private Connection connection;
    private String connectionId;

    private void connect(ConnectionObject object) throws SQLException {
        connectionId = object.getId();
        DatabaseConnection.getInstance().initialize(object);
        connection = DatabaseConnection.getInstance().getConnection();
    }

    @AfterEach
    void disconnect() {
        if (connectionId != null) {
            DatabaseConnection.getInstance().close(connectionId);
            connectionId = null;
        }
    }

    private static <T> T onFx(Callable<T> body) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(body.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private ResultTable grid(String sql) throws Exception {
        return onFx(() -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.execute();
            return new ResultTable(ps, 1);
        });
    }

    private void execute(String... statements) throws SQLException {
        try (Statement s = connection.createStatement()) {
            for (String statement : statements) {
                s.execute(statement);
            }
        }
    }

    private String scalar(String sql) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /** Runs the grid's pending statements the way the Apply button does, then resets its tracking. */
    private void apply(ResultTable table) throws Exception {
        List<QueryData> queries = new ArrayList<>(table.getUpdateQueries());
        Map<QueryData, Map<String, Object>> keys =
                ChangeApplier.apply(connection, queries, table.pendingInsertQueries());
        List<ResultTable.InsertedRow> inserted = onFx(() -> table.commitApplied(keys));
        // like the Apply button: read back what the database filled in
        table.reloadInserted(connection, inserted);
        onFx(() -> null); // lets the reload's FX update run
    }

    private void edit(ResultTable table, int row, int column, String oldValue, String newValue) throws Exception {
        onFx(() -> {
            table.updateDatabaseRow(table.getItems().get(row), column, oldValue, newValue);
            return null;
        });
    }

    private int addRow(ResultTable table) throws Exception {
        return onFx(() -> {
            table.addDatabaseRow();
            return table.getItems().size() - 1;
        });
    }

    // ------------------------------------------------------------------------------------ H2

    @Nested
    class OnH2 {

        @BeforeEach
        void fixture() throws SQLException {
            connect(new H2Connection("h2-edit", "h2", "H2", "jdbc:h2:mem:edit;DB_CLOSE_DELAY=-1", "sa", "", null));
            execute("DROP ALL OBJECTS",
                    "CREATE SCHEMA OTHER",
                    "CREATE TABLE PUBLIC.DUP(ID INT PRIMARY KEY, V VARCHAR(10))",
                    "CREATE TABLE OTHER.DUP(ID INT PRIMARY KEY, V VARCHAR(10))",
                    "INSERT INTO PUBLIC.DUP VALUES (1,'public'),(2,'p2')",
                    "INSERT INTO OTHER.DUP VALUES (1,'other'),(2,'o2')",
                    "CREATE TABLE PUBLIC.\"lower case\"(\"Id\" INT PRIMARY KEY, \"weird col\" VARCHAR(10))",
                    "INSERT INTO PUBLIC.\"lower case\" VALUES (1,'x')",
                    "CREATE TABLE PUBLIC.CK(B INT, A INT, TXT VARCHAR(10), PRIMARY KEY(B,A))",
                    "INSERT INTO PUBLIC.CK VALUES (1,2,'t'),(1,3,'u')",
                    "CREATE TABLE PUBLIC.IDENT(ID INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, NAME VARCHAR(10), CREATED TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }

        @Test
        void editTargetsTheSchemaTheRowsCameFrom() throws Exception {
            ResultTable table = grid("SELECT * FROM OTHER.DUP ORDER BY ID");
            assertTrue(table.isEditable());

            edit(table, 0, 1, "other", "EDITED");
            assertEquals("UPDATE \"OTHER\".\"DUP\" SET \"V\" = ? WHERE \"ID\" = ?", table.getUpdateQueries().get(0).getQuery());
            apply(table);

            assertEquals("EDITED", scalar("SELECT V FROM OTHER.DUP WHERE ID = 1"));
            assertEquals("public", scalar("SELECT V FROM PUBLIC.DUP WHERE ID = 1"));
        }

        @Test
        void quotedMixedCaseAndSpacedNamesWork() throws Exception {
            ResultTable table = grid("SELECT * FROM PUBLIC.\"lower case\"");
            edit(table, 0, 1, "x", "y");
            apply(table);
            assertEquals("y", scalar("SELECT \"weird col\" FROM PUBLIC.\"lower case\""));

            int row = addRow(table);
            edit(table, row, 0, "", "2");
            apply(table);
            assertEquals("2", scalar("SELECT COUNT(*) FROM PUBLIC.\"lower case\""));
        }

        @Test
        void newRowOmitsUntouchedColumnsSoIdentityAndDefaultsApply() throws Exception {
            ResultTable table = grid("SELECT * FROM PUBLIC.IDENT");
            int row = addRow(table);
            edit(table, row, 1, "", "zzz");

            assertEquals("INSERT INTO \"PUBLIC\".\"IDENT\" (\"NAME\") VALUES (?)", table.getUpdateQueries().get(0).getQuery());
            apply(table);

            assertEquals("zzz", scalar("SELECT NAME FROM PUBLIC.IDENT"));
            assertNotNull(scalar("SELECT CREATED FROM PUBLIC.IDENT"));
        }

        @Test
        void aRowInsertedFromTheGridShowsTheValuesTheDatabaseDefaulted() throws Exception {
            ResultTable table = grid("SELECT * FROM PUBLIC.IDENT");
            int row = addRow(table);
            edit(table, row, 1, "", "zzz");
            apply(table);

            TableRowData inserted = onFx(() -> table.getItems().get(row));
            assertNotNull(inserted.getStringData().get(2), "CREATED is filled in by the database, not left empty");
            assertFalse(inserted.getStringData().get(2).isEmpty());
            assertEquals(scalar("SELECT CREATED FROM PUBLIC.IDENT"), inserted.getStringData().get(2));
        }

        @Test
        void revertRestoresEditedAndDeletedRowsAndDropsNewOnes() throws Exception {
            ResultTable table = grid("SELECT * FROM PUBLIC.DUP ORDER BY ID");
            edit(table, 0, 1, "public", "changed");
            onFx(() -> {
                table.deleteDatabaseRow(1);
                return null;
            });
            addRow(table);

            onFx(() -> {
                table.restoreOriginalData();
                return null;
            });
            onFx(() -> null); // restoreOriginalData finishes in a later FX pulse

            assertEquals(2, onFx(() -> table.getItems().size()));
            assertEquals("public", onFx(() -> table.getItems().get(0).getStringData().get(1)));
            assertEquals("p2", onFx(() -> table.getItems().get(1).getStringData().get(1)));
            assertFalse(table.hasPendingChanges());
        }

        @Test
        void aRowInsertedFromTheGridCanBeEditedAfterwards() throws Exception {
            ResultTable table = grid("SELECT * FROM PUBLIC.IDENT");
            int row = addRow(table);
            edit(table, row, 1, "", "first");
            apply(table);

            TableRowData inserted = onFx(() -> table.getItems().get(row));
            assertFalse(inserted.isNewRow());
            assertNotNull(inserted.getOriginalData().get(0), "generated key must be filled in");

            edit(table, row, 1, "first", "second");
            assertTrue(table.getUpdateQueries().get(0).getQuery().startsWith("UPDATE"));
            apply(table);

            assertEquals("second", scalar("SELECT NAME FROM PUBLIC.IDENT"));
            assertEquals("1", scalar("SELECT COUNT(*) FROM PUBLIC.IDENT"));
        }

        @Test
        void editingTheKeyThenOtherCellsMakesOneUpdateWithTheOriginalKey() throws Exception {
            ResultTable table = grid("SELECT * FROM PUBLIC.DUP ORDER BY ID");
            edit(table, 1, 0, "2", "20");
            edit(table, 1, 1, "p2", "v20");
            edit(table, 1, 0, "20", "21");

            assertEquals(1, table.getUpdateQueries().size());
            apply(table);
            assertEquals("v20", scalar("SELECT V FROM PUBLIC.DUP WHERE ID = 21"));
            assertEquals("0", scalar("SELECT COUNT(*) FROM PUBLIC.DUP WHERE ID IN (2, 20)"));
        }

        @Test
        void deletingAnEditedRowDeletesByItsDatabaseKeyAndDropsTheUpdate() throws Exception {
            ResultTable table = grid("SELECT * FROM PUBLIC.DUP ORDER BY ID");
            edit(table, 0, 0, "1", "100");
            onFx(() -> {
                table.deleteDatabaseRow(0);
                return null;
            });

            assertEquals(1, table.getUpdateQueries().size());
            assertTrue(table.getUpdateQueries().get(0).getQuery().startsWith("DELETE"));
            apply(table);
            assertEquals("0", scalar("SELECT COUNT(*) FROM PUBLIC.DUP WHERE ID = 1"));
            assertEquals("1", scalar("SELECT COUNT(*) FROM PUBLIC.DUP"));
        }

        @Test
        void compositeKeysMatchEveryKeyColumn() throws Exception {
            ResultTable table = grid("SELECT * FROM PUBLIC.CK ORDER BY A");
            edit(table, 1, 2, "u", "UU");
            apply(table);
            assertEquals("UU", scalar("SELECT TXT FROM PUBLIC.CK WHERE A = 3"));
            assertEquals("t", scalar("SELECT TXT FROM PUBLIC.CK WHERE A = 2"));
        }

        @Test
        void joinsAndKeylessResultsStayReadOnly() throws Exception {
            assertFalse(grid("SELECT d.ID, o.ID FROM PUBLIC.DUP d JOIN OTHER.DUP o ON d.ID = o.ID").isEditable());
            assertFalse(grid("SELECT 1 AS ONE").isEditable());
        }
    }

    // ------------------------------------------------------------------------------------ SQLite

    @Nested
    class OnSqlite {

        @TempDir
        Path dir;

        @BeforeEach
        void fixture() throws SQLException {
            connect(new SQLiteConnection("lite-edit", "lite", "SQLite", "jdbc:sqlite:" + dir.resolve("main.db"), "", "", null));
            execute("ATTACH DATABASE '" + dir.resolve("aux.db").toString().replace("\\", "/") + "' AS aux",
                    "CREATE TABLE shared(id INTEGER PRIMARY KEY, v TEXT)",
                    "CREATE TABLE aux.shared(id INTEGER PRIMARY KEY, v TEXT)",
                    "CREATE TABLE \"my table\"(\"Id\" INTEGER PRIMARY KEY, \"weird col\" TEXT)",
                    "CREATE TABLE \"order\"(id INTEGER PRIMARY KEY AUTOINCREMENT, \"group\" TEXT)",
                    "INSERT INTO shared VALUES (1,'main')",
                    "INSERT INTO aux.shared VALUES (1,'aux')",
                    "INSERT INTO \"my table\" VALUES (1,'w')",
                    "INSERT INTO \"order\"(\"group\") VALUES ('g')");
        }

        @Test
        void aTableNameInSeveralAttachedDatabasesIsReadOnly() throws Exception {
            assertFalse(grid("SELECT * FROM aux.shared").isEditable());
            assertFalse(grid("SELECT * FROM main.shared").isEditable());
        }

        @Test
        void quotedAndReservedWordNamesWork() throws Exception {
            ResultTable spaced = grid("SELECT * FROM \"my table\"");
            assertTrue(spaced.isEditable());
            edit(spaced, 0, 1, "w", "w2");
            apply(spaced);
            assertEquals("w2", scalar("SELECT \"weird col\" FROM \"my table\""));

            ResultTable reserved = grid("SELECT * FROM \"order\"");
            edit(reserved, 0, 1, "g", "g2");
            apply(reserved);
            assertEquals("g2", scalar("SELECT \"group\" FROM \"order\""));
        }

        @Test
        void autoincrementRowInsertedFromTheGridIsEditableAfterwards() throws Exception {
            ResultTable table = grid("SELECT * FROM \"order\"");
            int row = addRow(table);
            edit(table, row, 1, "", "new");
            apply(table);

            edit(table, row, 1, "new", "newer");
            apply(table);

            assertEquals("newer", scalar("SELECT \"group\" FROM \"order\" WHERE id = 2"));
            assertEquals("2", scalar("SELECT COUNT(*) FROM \"order\""));
        }
    }
}
