package com.queryexe.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.List;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes already split statements one after another on a single connection, reporting every result
 * (result sets and update counts, including the extra results of procedures and multi-statement batches)
 * to a {@link Listener}. Blocking JDBC work: call it from a background thread.
 * <p>
 * Statements are run as plain {@link Statement}s, not prepared ones, so a {@code ?} in the SQL (for
 * example PostgreSQL's jsonb operators) is never mistaken for a parameter marker. The script stops at
 * the first statement that fails.
 */
@Slf4j
@UtilityClass
public class ScriptRunner {

    public interface Listener {

        /** The statement is about to run; keep it if it must stay cancellable. */
        default void onStatementStarted(String sql, Statement statement) {
        }

        /**
         * A result set is available on {@code statement.getResultSet()}. Consume it completely here;
         * the runner moves on to the next result afterwards.
         */
        void onResultSet(String sql, Statement statement, long millis) throws SQLException;

        void onUpdateCount(String sql, long count, long millis);

        /** All results of the statement were delivered without error. */
        default void onStatementSucceeded(String sql) {
        }

        void onError(String sql, SQLException error);
    }

    /**
     * @param maxRows maximum rows kept per result set, 0 for no limit
     * @return true when every statement succeeded
     */
    public static boolean run(Connection connection, List<String> statements, int maxRows, Listener listener) {
        for (String sql : statements) {
            if (sql == null || sql.isBlank()) {
                continue;
            }
            if (!runOne(connection, sql, maxRows, listener)) {
                return false;
            }
        }
        return true;
    }

    private static boolean runOne(Connection connection, String sql, int maxRows, Listener listener) {
        try (Statement statement = connection.createStatement()) {
            if (maxRows > 0) {
                statement.setMaxRows(maxRows);
            }
            listener.onStatementStarted(sql, statement);

            long start = System.currentTimeMillis();
            boolean isResultSet = statement.execute(sql);
            long millis = System.currentTimeMillis() - start;

            boolean reportedSomething = false;
            while (true) {
                if (isResultSet) {
                    listener.onResultSet(sql, statement, millis);
                    reportedSomething = true;
                } else {
                    long updateCount = updateCount(statement);
                    if (updateCount == -1) {
                        break;
                    }
                    listener.onUpdateCount(sql, updateCount, millis);
                    reportedSomething = true;
                }
                isResultSet = statement.getMoreResults();
            }

            // statements such as DDL on some drivers produce neither: still tell the user it completed
            if (!reportedSomething) {
                listener.onUpdateCount(sql, 0, millis);
            }
            listener.onStatementSucceeded(sql);
            return true;
        } catch (SQLException e) {
            log.warn("Statement failed: {}", e.getMessage());
            listener.onError(sql, e);
            return false;
        }
    }

    // getLargeUpdateCount is the JDBC 4.2 way (counts above int); older drivers only offer the int variant.
    private static long updateCount(Statement statement) throws SQLException {
        try {
            return statement.getLargeUpdateCount();
        } catch (SQLFeatureNotSupportedException | UnsupportedOperationException | AbstractMethodError e) {
            return statement.getUpdateCount();
        }
    }
}
