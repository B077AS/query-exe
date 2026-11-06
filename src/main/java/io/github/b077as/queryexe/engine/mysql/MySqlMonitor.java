package io.github.b077as.queryexe.engine.mysql;

import io.github.b077as.queryexe.monitor.DataTable;
import io.github.b077as.queryexe.monitor.MonitorSpec;
import io.github.b077as.queryexe.monitor.Queries;
import io.github.b077as.queryexe.monitor.Reading;
import io.github.b077as.queryexe.monitor.ServerMonitor;

import static io.github.b077as.queryexe.monitor.MonitorSpec.chart;
import static io.github.b077as.queryexe.monitor.MonitorSpec.gauge;
import static io.github.b077as.queryexe.monitor.MonitorSpec.note;
import static io.github.b077as.queryexe.monitor.MonitorSpec.rate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** MySQL and MariaDB: SHOW GLOBAL STATUS, the process list and the InnoDB lock wait views. */
public class MySqlMonitor implements ServerMonitor {

    private static final MonitorSpec SPEC = new MonitorSpec(
            List.of(
                    chart("connections", "Connections", ""),
                    chart("statements", "Statements", "/s"),
                    chart("network", "Network", "B/s"),
                    chart("locks", "InnoDB row lock waits", ""),
                    chart("buffer", "Buffer pool reads", "/s")),
            List.of(
                    gauge("Threads_connected", "Connected", "connections", true),
                    gauge("Threads_running", "Running", "connections", true),
                    note("max_connections", "Max connections", ""),
                    note("Max_used_connections", "Peak connections", ""),
                    gauge("Innodb_row_lock_current_waits", "Waiting", "locks", true),
                    rate("Com_select", "SELECT", "statements"),
                    rate("Com_insert", "INSERT", "statements"),
                    rate("Com_update", "UPDATE", "statements"),
                    rate("Com_delete", "DELETE", "statements"),
                    rate("Bytes_received", "Received", "network"),
                    rate("Bytes_sent", "Sent", "network"),
                    rate("Innodb_buffer_pool_read_requests", "From memory", "buffer"),
                    rate("Innodb_buffer_pool_reads", "From disk", "buffer")));

    private boolean mariaDb;

    public MySqlMonitor(boolean mariaDb) {
        this.mariaDb = mariaDb;
    }

    @Override
    public MonitorSpec spec() {
        return SPEC;
    }

    @Override
    public Reading read(Connection connection) throws SQLException {
        Reading.Builder out = Reading.builder();
        Queries.pairs(connection, "SHOW GLOBAL STATUS").forEach(out::put);
        Queries.pairs(connection, "SHOW GLOBAL VARIABLES LIKE 'max_connections'").forEach(out::put);
        return out.build();
    }

    @Override
    public DataTable sessions(Connection connection) {
        return DataTable.query(connection, """
                SELECT ID AS id, USER AS user_name, HOST AS host, DB AS db, COMMAND AS command, TIME AS seconds,
                       STATE AS state, LEFT(INFO, 300) AS query
                FROM information_schema.PROCESSLIST
                WHERE ID <> CONNECTION_ID()
                ORDER BY (COMMAND = 'Sleep'), TIME DESC""");
    }

    @Override
    public DataTable locks(Connection connection) {
        if (mariaDb) {
            return DataTable.query(connection, """
                    SELECT r.trx_mysql_thread_id AS waiting_id, LEFT(r.trx_query, 200) AS waiting_query,
                           b.trx_mysql_thread_id AS blocking_id, LEFT(b.trx_query, 200) AS blocking_query,
                           TIMESTAMPDIFF(SECOND, r.trx_wait_started, NOW()) AS waiting_seconds
                    FROM information_schema.INNODB_LOCK_WAITS w
                    JOIN information_schema.INNODB_TRX r ON r.trx_id = w.requesting_trx_id
                    JOIN information_schema.INNODB_TRX b ON b.trx_id = w.blocking_trx_id
                    ORDER BY waiting_seconds DESC""");
        }
        return DataTable.query(connection, """
                SELECT waiting_pid AS waiting_id, LEFT(waiting_query, 200) AS waiting_query,
                       blocking_pid AS blocking_id, LEFT(blocking_query, 200) AS blocking_query,
                       wait_age_secs AS waiting_seconds, locked_table
                FROM sys.innodb_lock_waits
                ORDER BY wait_age_secs DESC""");
    }
}
