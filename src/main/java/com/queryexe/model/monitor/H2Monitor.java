package com.queryexe.model.monitor;

import static com.queryexe.model.monitor.MonitorSpec.chart;
import static com.queryexe.model.monitor.MonitorSpec.gauge;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** H2: INFORMATION_SCHEMA.SESSIONS (what each session runs and who blocks it). An embedded database only shows its own sessions. */
public final class H2Monitor implements ServerMonitor {

    private static final MonitorSpec SPEC = new MonitorSpec(
            List.of(chart("sessions", "Sessions", "")),
            List.of(
                    gauge("sessions", "Sessions", "sessions", true),
                    gauge("running", "Running", "sessions", true),
                    gauge("blocked", "Blocked", "sessions", true),
                    gauge("uncommitted", "With uncommitted changes", "sessions", false)));

    @Override
    public MonitorSpec spec() {
        return SPEC;
    }

    @Override
    public Reading read(Connection connection) throws SQLException {
        Reading.Builder out = Reading.builder();
        Queries.row(connection, """
                SELECT COUNT(*) AS sessions,
                       COUNT(CASE WHEN EXECUTING_STATEMENT IS NOT NULL THEN 1 END) AS running,
                       COUNT(BLOCKER_ID) AS blocked,
                       COUNT(CASE WHEN CONTAINS_UNCOMMITTED THEN 1 END) AS uncommitted
                FROM INFORMATION_SCHEMA.SESSIONS
                WHERE SESSION_ID <> SESSION_ID()""").forEach(out::put);
        return out.build();
    }

    @Override
    public DataTable sessions(Connection connection) {
        return DataTable.query(connection, """
                SELECT SESSION_ID AS id, USER_NAME AS user_name, CLIENT_ADDR AS client, ISOLATION_LEVEL AS isolation,
                       CONTAINS_UNCOMMITTED AS uncommitted, BLOCKER_ID AS blocked_by,
                       DATEDIFF('SECOND', EXECUTING_STATEMENT_START, CURRENT_TIMESTAMP) AS seconds,
                       LEFT(EXECUTING_STATEMENT, 300) AS query
                FROM INFORMATION_SCHEMA.SESSIONS
                WHERE SESSION_ID <> SESSION_ID()
                ORDER BY (EXECUTING_STATEMENT IS NULL), EXECUTING_STATEMENT_START""");
    }

    @Override
    public DataTable locks(Connection connection) {
        return DataTable.query(connection, """
                SELECT s.SESSION_ID AS waiting_id, LEFT(s.EXECUTING_STATEMENT, 200) AS waiting_query,
                       s.BLOCKER_ID AS blocking_id,
                       DATEDIFF('SECOND', s.EXECUTING_STATEMENT_START, CURRENT_TIMESTAMP) AS waiting_seconds
                FROM INFORMATION_SCHEMA.SESSIONS s
                WHERE s.BLOCKER_ID IS NOT NULL
                ORDER BY waiting_seconds DESC""");
    }
}
