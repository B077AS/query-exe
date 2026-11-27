package io.github.b077as.queryexe.query;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.b077as.queryexe.query.QueryData;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the pending edits of a result grid against the database. Blocking JDBC work: call it from a
 * background thread, never from the FX thread.
 */
@Slf4j
@UtilityClass
public class ChangeApplier {

    /**
     * All-or-nothing: either every statement is committed, or none of them is. The connection is always returned to
     * the autocommit mode it had before.
     * <p>
     * A connection that is already inside a transaction (a tab in manual commit mode) is left inside it: the edits run
     * under a savepoint, a failure undoes just them, and committing stays the user's call.
     *
     * @param inserts the subset of {@code queries} (by identity) that are INSERTs and should report generated keys
     * @return the generated-key columns reported per INSERT (identity-keyed; possibly empty)
     */
    public static Map<QueryData, Map<String, Object>> apply(Connection connection, List<QueryData> queries,
                                                            Set<QueryData> inserts) throws SQLException {
        Map<QueryData, Map<String, Object>> generatedKeys = new IdentityHashMap<>();
        boolean previousAutoCommit = connection.getAutoCommit();
        Savepoint savepoint = null;
        if (previousAutoCommit) {
            connection.setAutoCommit(false);
        } else {
            savepoint = connection.setSavepoint();
        }

        try {
            for (QueryData queryInfo : queries) {
                boolean isInsert = inserts.contains(queryInfo);
                try (PreparedStatement stmt = isInsert
                        ? connection.prepareStatement(queryInfo.getQuery(), Statement.RETURN_GENERATED_KEYS)
                        : connection.prepareStatement(queryInfo.getQuery())) {
                    bindParameters(stmt, queryInfo.getParameters());
                    stmt.executeUpdate();

                    if (isInsert) {
                        generatedKeys.put(queryInfo, readGeneratedKeys(stmt));
                    }
                }
            }
            if (savepoint == null) {
                connection.commit();
            } else {
                connection.releaseSavepoint(savepoint);
            }
        } catch (SQLException e) {
            try {
                if (savepoint == null) {
                    connection.rollback();
                } else {
                    connection.rollback(savepoint);
                }
            } catch (SQLException rollbackException) {
                log.error("Rollback failed", rollbackException);
            }
            throw e;
        } finally {
            if (previousAutoCommit) {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    log.error("Could not restore autocommit", e);
                }
            }
        }

        return generatedKeys;
    }

    public static void bindParameters(PreparedStatement stmt, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            Object param = parameters.get(i);
            if (param == null) {
                stmt.setNull(i + 1, Types.NULL);
            } else if (param instanceof UntypedText(String value)) {
                stmt.setObject(i + 1, value, Types.OTHER);
            } else {
                stmt.setObject(i + 1, param);
            }
        }
    }

    private static Map<String, Object> readGeneratedKeys(PreparedStatement stmt) {
        Map<String, Object> keys = new LinkedHashMap<>();
        try (ResultSet rs = stmt.getGeneratedKeys()) {
            if (rs != null && rs.next()) {
                ResultSetMetaData metaData = rs.getMetaData();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    keys.put(metaData.getColumnLabel(i), rs.getObject(i));
                }
            }
        } catch (SQLException e) {
            // Not every driver supports this; the row is then simply not editable until the query is re-run.
            log.debug("Generated keys unavailable: {}", e.getMessage());
        }
        return keys;
    }
}
