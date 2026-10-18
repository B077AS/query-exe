package com.queryexe.components.results;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.queryexe.model.connections.H2Connection;
import com.queryexe.service.DatabaseConnection;

import javafx.application.Platform;

/** How result values and headers are shown in the grid. Needs a JavaFX toolkit; skipped without one. */
class ResultTableRenderTest {

    private Connection connection;

    @BeforeAll
    static void startToolkit() {
        boolean available;
        try {
            Platform.startup(() -> {
            });
            available = true;
        } catch (IllegalStateException alreadyStarted) {
            available = true;
        } catch (Throwable noToolkit) {
            available = false;
        }
        Assumptions.assumeTrue(available, "JavaFX toolkit not available");
    }

    @BeforeEach
    void fixture() throws SQLException {
        DatabaseConnection.getInstance().initialize(
                new H2Connection("h2-render", "h2", "H2", "jdbc:h2:mem:render;DB_CLOSE_DELAY=-1", "sa", "", null));
        connection = DatabaseConnection.getInstance().getConnection();
        try (Statement s = connection.createStatement()) {
            s.execute("DROP ALL OBJECTS");
            s.execute("CREATE TABLE TYPES(ID INT PRIMARY KEY, CL CLOB, J JSON, AR INT ARRAY, B BOOLEAN, N VARCHAR(5))");
            s.execute("INSERT INTO TYPES VALUES (1, 'hello clob', JSON '{\"a\":1}', ARRAY[1,2,3], TRUE, NULL)");
            s.execute("INSERT INTO TYPES VALUES (2, NULL, NULL, NULL, FALSE, 'x')");
        }
    }

    @AfterEach
    void disconnect() {
        DatabaseConnection.getInstance().close("h2-render");
    }

    private ResultTable grid(String sql, int maxRows) throws Exception {
        CompletableFuture<ResultTable> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try (Statement statement = connection.createStatement()) {
                if (maxRows > 0) {
                    statement.setMaxRows(maxRows);
                }
                statement.execute(sql);
                result.complete(new ResultTable(statement, 1, maxRows));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void largeObjectsJsonAndArraysShowTheirValue() throws Exception {
        ResultTable table = grid("SELECT * FROM TYPES ORDER BY ID", 0);

        var row = table.getItems().get(0).getStringData();
        assertEquals("hello clob", row.get(1));
        assertEquals("{\"a\":1}", row.get(2));
        assertEquals("[1, 2, 3]", row.get(3));
    }

    @Test
    void nullsStayNull() throws Exception {
        ResultTable table = grid("SELECT * FROM TYPES ORDER BY ID", 0);

        var row = table.getItems().get(1).getStringData();
        assertNull(row.get(1));
        assertNull(row.get(2));
        assertNull(row.get(3));
    }

    @Test
    void booleansShowTrueAndFalseAndNullIsNotFalse() throws Exception {
        try (Statement s = connection.createStatement()) {
            s.execute("INSERT INTO TYPES(ID, B) VALUES (3, NULL)");
        }
        ResultTable table = grid("SELECT ID, B FROM TYPES ORDER BY ID", 0);

        assertEquals("true", table.getItems().get(0).getStringData().get(1));
        assertEquals("false", table.getItems().get(1).getStringData().get(1));
        assertNull(table.getItems().get(2).getStringData().get(1));
        assertNull(table.getItems().get(2).getOriginalData().get(1));
    }

    @Test
    void headerShowsTheAliasWhileEditsStillUseTheRealColumn() throws Exception {
        ResultTable table = grid("SELECT ID AS KEY_ALIAS, N AS NICKNAME FROM TYPES ORDER BY ID", 0);

        assertEquals("KEY_ALIAS", table.getColumns().get(0).getText());
        assertEquals("NICKNAME", table.getColumns().get(1).getText());
        assertTrue(table.isEditable());

        Platform.runLater(() -> table.updateDatabaseRow(table.getItems().get(1), 1, "x", "y"));
        Platform.runLater(() -> {
        });
        Thread.sleep(200);
        assertEquals("UPDATE \"PUBLIC\".\"TYPES\" SET \"N\" = ? WHERE \"ID\" = ?", table.getUpdateQueries().get(0).getQuery());
    }

    @Test
    void anAliasedColumnIsNotMistakenForTheKey() throws Exception {
        ResultTable table = grid("SELECT N AS ID, ID AS OTHER FROM TYPES ORDER BY 2", 0);

        assertFalse(table.isPrimaryKeyColumn(0), "column named ID by alias is really N");
        assertTrue(table.isPrimaryKeyColumn(1), "the real key keeps being recognised under an alias");
    }

    @Test
    void expressionColumnsCannotBeEdited() throws Exception {
        ResultTable table = grid("SELECT ID, UPPER(N) AS SHOUT FROM TYPES", 0);

        assertTrue(table.getColumns().get(0).isEditable());
        assertFalse(table.getColumns().get(1).isEditable());
    }

    @Test
    void copyRowAsInsertUsesRealNamesQualifiedTargetAndTypedValues() throws Exception {
        ResultTable table = grid("SELECT ID AS KEY_ALIAS, N AS NICKNAME, B FROM TYPES WHERE ID = 2", 0);
        CompletableFuture<String> script = new CompletableFuture<>();
        Platform.runLater(() -> {
            javafx.scene.control.TableCell<com.queryexe.model.data.TableRowData, String> cell = new javafx.scene.control.TableCell<>();
            cell.updateTableView(table);
            script.complete(DatabaseConnection.getInstance().getConnectionObject()
                    .generateRowInsertScript(table.getItems().get(0).getStringData(), cell));
        });

        String sql = script.get(30, TimeUnit.SECONDS);

        assertEquals("INSERT INTO \"PUBLIC\".\"TYPES\" (\"ID\", \"N\", \"B\")\nVALUES (2, 'x', FALSE);", sql);
    }

    @Test
    void reachingTheRowLimitMarksTheResultTruncated() throws Exception {
        assertTrue(grid("SELECT * FROM TYPES", 2).isTruncated());
        assertFalse(grid("SELECT * FROM TYPES", 5).isTruncated());
        assertFalse(grid("SELECT * FROM TYPES", 0).isTruncated());
    }
}
