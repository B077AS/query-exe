package com.queryexe.engine.sqlserver;

import com.queryexe.monitor.DataTable;
import com.queryexe.monitor.MonitorSpec;
import com.queryexe.monitor.Queries;
import com.queryexe.monitor.Reading;
import com.queryexe.monitor.ServerMonitor;

import static com.queryexe.monitor.MonitorSpec.chart;
import static com.queryexe.monitor.MonitorSpec.gauge;
import static com.queryexe.monitor.MonitorSpec.rate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** SQL Server: the dynamic management views. Needs VIEW SERVER STATE; without it the page gets the server's message. */
public class SqlServerMonitor implements ServerMonitor {

    private static final MonitorSpec SPEC = new MonitorSpec(
            List.of(
                    chart("connections", "Sessions", ""),
                    chart("batches", "Batches", "/s"),
                    chart("transactions", "Transactions", "/s"),
                    chart("locks", "Lock waits and deadlocks", "/s"),
                    chart("memory", "Page life expectancy", "s")),
            List.of(
                    gauge("sessions", "Sessions", "connections", true),
                    gauge("running", "Running", "connections", true),
                    gauge("blocked", "Blocked", "connections", true),
                    rate("Batch Requests/sec", "Batch requests", "batches"),
                    rate("SQL Compilations/sec", "Compilations", "batches"),
                    rate("Transactions/sec", "Transactions", "transactions"),
                    rate("Lock Waits/sec", "Lock waits", "locks"),
                    rate("Number of Deadlocks/sec", "Deadlocks", "locks"),
                    gauge("Page life expectancy", "Page life expectancy", "memory", false)));

    @Override
    public MonitorSpec spec() {
        return SPEC;
    }

    @Override
    public Reading read(Connection connection) throws SQLException {
        Reading.Builder out = Reading.builder();
        Queries.row(connection, """
                SELECT (SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE is_user_process = 1 AND session_id <> @@SPID) AS sessions,
                       (SELECT COUNT(*) FROM sys.dm_exec_requests r
                          JOIN sys.dm_exec_sessions s ON s.session_id = r.session_id
                         WHERE s.is_user_process = 1 AND r.session_id <> @@SPID) AS running,
                       (SELECT COUNT(*) FROM sys.dm_exec_requests WHERE blocking_session_id <> 0) AS blocked""").forEach(out::put);
        // the "/sec" counters are running totals despite their names
        Queries.pairs(connection, """
                SELECT RTRIM(counter_name) AS name, SUM(cntr_value) AS value
                FROM sys.dm_os_performance_counters
                WHERE RTRIM(counter_name) IN ('Batch Requests/sec', 'SQL Compilations/sec')
                   OR (RTRIM(instance_name) = '_Total' AND RTRIM(counter_name) IN ('Transactions/sec', 'Number of Deadlocks/sec', 'Lock Waits/sec'))
                   OR (RTRIM(counter_name) = 'Page life expectancy' AND object_name LIKE '%Buffer Manager%')
                GROUP BY RTRIM(counter_name)""").forEach(out::put);
        return out.build();
    }

    @Override
    public DataTable sessions(Connection connection) {
        return DataTable.query(connection, """
                SELECT s.session_id, s.login_name AS user_name, s.host_name AS host, s.program_name AS application,
                       DB_NAME(s.database_id) AS db, s.status, r.command, r.wait_type AS wait,
                       r.blocking_session_id AS blocked_by, r.total_elapsed_time / 1000 AS seconds,
                       LEFT(t.text, 300) AS query
                FROM sys.dm_exec_sessions s
                LEFT JOIN sys.dm_exec_requests r ON r.session_id = s.session_id
                OUTER APPLY sys.dm_exec_sql_text(r.sql_handle) t
                WHERE s.is_user_process = 1 AND s.session_id <> @@SPID
                ORDER BY CASE WHEN r.session_id IS NULL THEN 1 ELSE 0 END, r.total_elapsed_time DESC""");
    }

    @Override
    public DataTable locks(Connection connection) {
        return DataTable.query(connection, """
                SELECT r.session_id AS waiting_id, LEFT(t.text, 200) AS waiting_query,
                       r.blocking_session_id AS blocking_id, r.wait_type, r.wait_time / 1000 AS waiting_seconds,
                       DB_NAME(r.database_id) AS db
                FROM sys.dm_exec_requests r
                OUTER APPLY sys.dm_exec_sql_text(r.sql_handle) t
                WHERE r.blocking_session_id <> 0
                ORDER BY r.wait_time DESC""");
    }
}
