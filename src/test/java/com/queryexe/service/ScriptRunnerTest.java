package com.queryexe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScriptRunnerTest {

    /** Records everything the runner reports, consuming result sets like the grid does. */
    private static final class Recorder implements ScriptRunner.Listener {
        final List<String> events = new ArrayList<>();

        @Override
        public void onStatementStarted(String sql, Statement statement) {
            events.add("start:" + sql);
        }

        @Override
        public void onResultSet(String sql, Statement statement, long millis) throws SQLException {
            ResultSet rs = statement.getResultSet();
            int rows = 0;
            while (rs.next()) {
                rows++;
            }
            events.add("rows:" + rows);
        }

        @Override
        public void onUpdateCount(String sql, long count, long millis) {
            events.add("count:" + count);
        }

        @Override
        public void onStatementSucceeded(String sql) {
            events.add("ok:" + sql);
        }

        @Override
        public void onError(String sql, SQLException error) {
            events.add("error:" + sql);
        }
    }

    private Connection connection;

    @BeforeEach
    void open() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:runner;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement s = connection.createStatement()) {
            s.execute("DROP ALL OBJECTS");
            s.execute("CREATE TABLE T(ID INT PRIMARY KEY, V VARCHAR(10))");
            s.execute("INSERT INTO T VALUES (1,'a'),(2,'b'),(3,'c')");
        }
    }

    @AfterEach
    void close() throws SQLException {
        connection.close();
    }

    @Test
    void reportsResultSetsAndUpdateCountsInOrder() {
        Recorder recorder = new Recorder();

        boolean ok = ScriptRunner.run(connection,
                List.of("UPDATE T SET V = 'x' WHERE ID < 3", "SELECT * FROM T", "DELETE FROM T WHERE ID = 3"), 0, recorder);

        assertTrue(ok);
        assertEquals(List.of(
                "start:UPDATE T SET V = 'x' WHERE ID < 3", "count:2", "ok:UPDATE T SET V = 'x' WHERE ID < 3",
                "start:SELECT * FROM T", "rows:3", "ok:SELECT * FROM T",
                "start:DELETE FROM T WHERE ID = 3", "count:1", "ok:DELETE FROM T WHERE ID = 3"), recorder.events);
    }

    @Test
    void ddlStillReportsCompletion() {
        Recorder recorder = new Recorder();
        assertTrue(ScriptRunner.run(connection, List.of("CREATE TABLE X(A INT)"), 0, recorder));
        assertTrue(recorder.events.contains("count:0"));
    }

    @Test
    void stopsAtTheFirstFailure() {
        Recorder recorder = new Recorder();

        boolean ok = ScriptRunner.run(connection, List.of("SELECT 1", "SELECT * FROM NOPE", "SELECT 2"), 0, recorder);

        assertFalse(ok);
        assertTrue(recorder.events.contains("error:SELECT * FROM NOPE"));
        assertFalse(recorder.events.contains("start:SELECT 2"), "statements after the failure must not run");
    }

    @Test
    void blankStatementsAreSkipped() {
        Recorder recorder = new Recorder();
        assertTrue(ScriptRunner.run(connection, Arrays.asList("  ", null, "SELECT 1"), 0, recorder));
        assertEquals(List.of("start:SELECT 1", "rows:1", "ok:SELECT 1"), recorder.events);
    }

    @Test
    void maxRowsLimitsWhatIsDelivered() {
        Recorder recorder = new Recorder();
        ScriptRunner.run(connection, List.of("SELECT * FROM T"), 2, recorder);
        assertTrue(recorder.events.contains("rows:2"));
    }

    @Test
    void aQuestionMarkIsNotTreatedAsAParameterMarkerByTheRunner() throws SQLException {
        // A plain Statement is used, so the driver never sees a bound-parameter statement. H2 reports the
        // bare "?" as a syntax problem of the SQL itself rather than as a missing parameter.
        Recorder recorder = new Recorder();
        ScriptRunner.run(connection, List.of("SELECT 1 WHERE 1 = ?"), 0, recorder);
        assertTrue(recorder.events.contains("error:SELECT 1 WHERE 1 = ?"));
    }

    // -------------------------------------------------------------------- several results per statement

    /** A statement that yields: result set, update count 7, result set, and then nothing more. */
    private static Connection multiResultConnection() {
        List<Object> results = List.of("RS1", 7L, "RS2");
        int[] cursor = {0};
        InvocationHandler statementHandler = (proxy, method, args) -> switch (method.getName()) {
            case "execute" -> results.get(0) instanceof String;
            case "getMoreResults" -> {
                cursor[0]++;
                yield cursor[0] < results.size() && results.get(cursor[0]) instanceof String;
            }
            case "getLargeUpdateCount" -> cursor[0] < results.size() && results.get(cursor[0]) instanceof Long n ? n : -1L;
            case "getResultSet" -> null;
            case "close", "setMaxRows" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        };
        Statement statement = (Statement) Proxy.newProxyInstance(
                ScriptRunnerTest.class.getClassLoader(), new Class<?>[]{Statement.class}, statementHandler);
        return (Connection) Proxy.newProxyInstance(ScriptRunnerTest.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("createStatement")) {
                        return statement;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void deliversEveryResultOfAMultiResultStatement() {
        List<String> seen = new ArrayList<>();
        ScriptRunner.Listener listener = new ScriptRunner.Listener() {
            @Override
            public void onResultSet(String sql, Statement statement, long millis) {
                seen.add("rs");
            }

            @Override
            public void onUpdateCount(String sql, long count, long millis) {
                seen.add("count:" + count);
            }

            @Override
            public void onError(String sql, SQLException error) {
                seen.add("error");
            }
        };

        assertTrue(ScriptRunner.run(multiResultConnection(), List.of("CALL multi()"), 0, listener));

        assertEquals(List.of("rs", "count:7", "rs"), seen);
    }
}
