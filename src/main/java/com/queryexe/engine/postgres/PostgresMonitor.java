package com.queryexe.engine.postgres;

import com.queryexe.monitor.DataTable;
import com.queryexe.monitor.MonitorSpec;
import com.queryexe.monitor.Queries;
import com.queryexe.monitor.Reading;
import com.queryexe.monitor.ServerMonitor;

import static com.queryexe.monitor.MonitorSpec.chart;
import static com.queryexe.monitor.MonitorSpec.gauge;
import static com.queryexe.monitor.MonitorSpec.note;
import static com.queryexe.monitor.MonitorSpec.rate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** PostgreSQL: pg_stat_activity, pg_stat_database and pg_blocking_pids (9.6+). */
public class PostgresMonitor implements ServerMonitor {

    private static final MonitorSpec SPEC = new MonitorSpec(
            List.of(
                    chart("connections", "Connections", ""),
                    chart("transactions", "Transactions", "/s"),
                    chart("read", "Rows read", "/s"),
                    chart("written", "Rows written", "/s"),
                    chart("cache", "Buffer reads", "/s")),
            List.of(
                    gauge("total", "Total", "connections", true),
                    gauge("active", "Active", "connections", true),
                    gauge("idle_tx", "Idle in transaction", "connections", false),
                    gauge("waiting", "Waiting for a lock", "connections", true),
                    note("max_connections", "Max connections", ""),
                    note("deadlocks", "Deadlocks since reset", ""),
                    rate("xact_commit", "Commits", "transactions"),
                    rate("xact_rollback", "Rollbacks", "transactions"),
                    rate("tup_returned", "Scanned", "read"),
                    rate("tup_fetched", "Fetched", "read"),
                    rate("tup_inserted", "Inserted", "written"),
                    rate("tup_updated", "Updated", "written"),
                    rate("tup_deleted", "Deleted", "written"),
                    rate("blks_hit", "From cache", "cache"),
                    rate("blks_read", "From disk", "cache")));

    @Override
    public MonitorSpec spec() {
        return SPEC;
    }

    @Override
    public Reading read(Connection connection) throws SQLException {
        Reading.Builder out = Reading.builder();
        Queries.row(connection, """
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE state = 'active') AS active,
                       count(*) FILTER (WHERE state LIKE 'idle in transaction%') AS idle_tx,
                       count(*) FILTER (WHERE wait_event_type = 'Lock') AS waiting
                FROM pg_stat_activity
                WHERE backend_type = 'client backend' AND pid <> pg_backend_pid()""").forEach(out::put);
        Queries.row(connection, """
                SELECT xact_commit, xact_rollback, tup_returned, tup_fetched, tup_inserted, tup_updated, tup_deleted,
                       blks_read, blks_hit, deadlocks
                FROM pg_stat_database WHERE datname = current_database()""").forEach(out::put);
        Queries.row(connection, "SELECT setting::int AS max_connections FROM pg_settings WHERE name = 'max_connections'").forEach(out::put);
        return out.build();
    }

    @Override
    public DataTable sessions(Connection connection) {
        return DataTable.query(connection, """
                SELECT pid, usename AS user_name, datname AS database, application_name AS application,
                       client_addr::text AS client, state,
                       concat_ws(':', wait_event_type, wait_event) AS wait,
                       round(extract(epoch FROM (now() - query_start)))::int AS seconds,
                       left(query, 300) AS query
                FROM pg_stat_activity
                WHERE backend_type = 'client backend' AND pid <> pg_backend_pid()
                ORDER BY (state = 'active') DESC, query_start""");
    }

    @Override
    public DataTable locks(Connection connection) {
        return DataTable.query(connection, """
                SELECT blocked.pid AS waiting_pid, blocked.usename AS waiting_user, left(blocked.query, 200) AS waiting_query,
                       blocking.pid AS blocking_pid, blocking.usename AS blocking_user, left(blocking.query, 200) AS blocking_query,
                       round(extract(epoch FROM (now() - blocked.query_start)))::int AS waiting_seconds
                FROM pg_stat_activity blocked
                JOIN LATERAL unnest(pg_blocking_pids(blocked.pid)) AS b(pid) ON true
                JOIN pg_stat_activity blocking ON blocking.pid = b.pid
                ORDER BY waiting_seconds DESC""");
    }
}
