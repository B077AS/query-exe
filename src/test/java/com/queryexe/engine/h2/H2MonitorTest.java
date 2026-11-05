package com.queryexe.engine.h2;

import com.queryexe.monitor.DataTable;
import com.queryexe.monitor.MonitorSpec;
import com.queryexe.monitor.Reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

/** The H2 monitor against a real in-memory database, including a session that is really blocked by another. */
class H2MonitorTest {

    private static final String URL = "jdbc:h2:mem:monitor;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000";

    @Test
    void everyMetricOfTheSpecHasAnImplementation() {
        MonitorSpec spec = new H2Monitor().spec();
        for (MonitorSpec.Metric metric : spec.getMetrics()) {
            if (metric.getChart() != null) {
                assertTrue(spec.getCharts().stream().anyMatch(c -> c.getId().equals(metric.getChart())), metric.getKey());
            }
        }
    }

    @Test
    void countsSessionsAndListsThem() throws Exception {
        H2Monitor monitor = new H2Monitor();
        try (Connection watcher = DriverManager.getConnection(URL);
             Connection other = DriverManager.getConnection(URL)) {
            Reading reading = monitor.read(watcher);
            assertEquals(1.0, reading.getValues().get("sessions"), "the other connection, not the watcher itself");
            assertEquals(0.0, reading.getValues().get("blocked"));

            DataTable sessions = monitor.sessions(watcher);
            assertNull(sessions.getNote());
            assertEquals(1, sessions.getRows().size());
            assertTrue(sessions.getColumns().contains("user_name"));
            assertTrue(monitor.locks(watcher).getRows().isEmpty());
        }
    }

    @Test
    void showsWhoBlocksWhom() throws Exception {
        H2Monitor monitor = new H2Monitor();
        try (Connection watcher = DriverManager.getConnection(URL);
             Connection holder = DriverManager.getConnection(URL);
             Connection waiter = DriverManager.getConnection(URL)) {
            try (Statement s = holder.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS locked(id INT PRIMARY KEY, v INT)");
                s.execute("MERGE INTO locked KEY(id) VALUES (1, 0)");
            }
            holder.setAutoCommit(false);
            try (Statement s = holder.createStatement()) {
                s.execute("UPDATE locked SET v = 1 WHERE id = 1"); // holds the row, never commits
            }
            Thread blocked = new Thread(() -> {
                try (Statement s = waiter.createStatement()) {
                    s.execute("UPDATE locked SET v = 2 WHERE id = 1");
                } catch (Exception ignored) {
                    // released by the rollback below
                }
            });
            blocked.start();

            DataTable locks = DataTable.empty(null);
            for (int i = 0; i < 100 && locks.getRows().isEmpty(); i++) {
                Thread.sleep(50);
                locks = monitor.locks(watcher);
            }
            assertEquals(1, locks.getRows().size(), "one session is waiting");
            assertEquals(1.0, monitor.read(watcher).getValues().get("blocked"));

            holder.rollback();
            blocked.join(5000);
        }
    }
}
