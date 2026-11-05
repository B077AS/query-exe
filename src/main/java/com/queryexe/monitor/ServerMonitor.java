package com.queryexe.monitor;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Reads the live state of a database server for the monitor: a handful of numbers (connections, throughput, locks)
 * to chart, plus the list of sessions and of blocked sessions. One implementation per engine; they work on a plain
 * connection and hold no state, so they can be tested without the app.
 */
public interface ServerMonitor {

    /** What this engine reports: the charts and the metrics drawn on them. */
    MonitorSpec spec();

    /** Gauges (as they are now) and counters (running totals since the server started; the service turns them into rates). */
    Reading read(Connection connection) throws SQLException;

    /** Who is connected and what they are running. */
    DataTable sessions(Connection connection) throws SQLException;

    /** Who is waiting for whom. Empty when nothing is blocked. */
    DataTable locks(Connection connection) throws SQLException;
}
