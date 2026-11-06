package io.github.b077as.queryexe.monitor;

import io.github.b077as.queryexe.connection.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.b077as.queryexe.connection.ConnectionProfile;
import io.github.b077as.queryexe.monitor.DataTable;
import io.github.b077as.queryexe.monitor.MonitorSpec;
import io.github.b077as.queryexe.monitor.Reading;
import io.github.b077as.queryexe.monitor.ServerMonitor;

import lombok.extern.slf4j.Slf4j;

/**
 * Drives the server monitor: one connection of its own per monitored database (so polling never queues behind the
 * schema tree or a query tab), and the arithmetic that turns running totals into per-second rates. Blocking JDBC:
 * call off the UI thread.
 */
@Slf4j
public class MonitorService {

    private static final MonitorService INSTANCE = new MonitorService();

    public static MonitorService get() {
        return INSTANCE;
    }

    private static final class Handle {
        ConnectionProfile object;
        ServerMonitor monitor;
        Connection session;
        Map<String, Double> previous = Map.of();
        long previousAt;

        Handle(ConnectionProfile object, ServerMonitor monitor, Connection session) {
            this.object = object;
            this.monitor = monitor;
            this.session = session;
        }
    }

    private Map<String, Handle> handles = new ConcurrentHashMap<>();

    private MonitorService() {
    }

    /** Starts monitoring a connection: opens its monitor session and takes a first reading, so the first sample already has rates. */
    public Map<String, Object> open(String connectionId) throws SQLException {
        close(connectionId);
        DatabaseConnection dc = DatabaseConnection.getInstance();
        ConnectionProfile object = dc.getProfile(connectionId);
        if (object == null) {
            throw new SQLException("This connection is not open.");
        }
        ServerMonitor monitor = object.type().createMonitor();
        Map<String, Object> out = new LinkedHashMap<>();
        if (monitor == null) {
            out.put("supported", false);
            out.put("reason", object.getDbType() + " has no server to monitor.");
            return out;
        }
        Connection session = dc.openSession(object);
        Handle handle = new Handle(object, monitor, session);
        try {
            Reading first = monitor.read(session);
            handle.previous = first.getValues();
            handle.previousAt = System.nanoTime();
        } catch (SQLException | RuntimeException e) {
            dc.closeSession(connectionId, session);
            throw e;
        }
        handles.put(connectionId, handle);
        MonitorSpec spec = monitor.spec();
        out.put("supported", true);
        out.put("charts", spec.getCharts());
        out.put("metrics", spec.getMetrics());
        return out;
    }

    /** One look: every metric of the engine (rates since the previous look) and, on request, the session and lock lists. */
    public Map<String, Object> sample(String connectionId, boolean tables) throws SQLException {
        Handle handle = handles.get(connectionId);
        if (handle == null) {
            throw new SQLException("The monitor is not open.");
        }
        synchronized (handle) {
            DatabaseConnection dc = DatabaseConnection.getInstance();
            if (handle.session == null || handle.session.isClosed()) {
                handle.session = dc.openSession(handle.object);
            }
            Reading reading = handle.monitor.read(handle.session);
            long now = System.nanoTime();
            double seconds = Math.max(0.001, (now - handle.previousAt) / 1e9);

            Map<String, Double> values = new HashMap<>();
            for (MonitorSpec.Metric metric : handle.monitor.spec().getMetrics()) {
                Double current = reading.getValues().get(metric.getKey());
                if (current == null) {
                    continue;
                }
                if (metric.getKind() == MonitorSpec.Kind.GAUGE) {
                    values.put(metric.getKey(), current);
                    continue;
                }
                Double before = handle.previous.get(metric.getKey());
                if (before != null && current >= before) { // smaller means the server restarted or the counters were reset
                    values.put(metric.getKey(), (current - before) / seconds);
                }
            }
            handle.previous = reading.getValues();
            handle.previousAt = now;

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("values", values);
            if (tables) {
                DataTable sessions = handle.monitor.sessions(handle.session);
                DataTable locks = handle.monitor.locks(handle.session);
                out.put("sessions", sessions);
                out.put("locks", locks);
            }
            return out;
        }
    }

    public void close(String connectionId) {
        Handle handle = handles.remove(connectionId);
        if (handle != null) {
            synchronized (handle) {
                DatabaseConnection.getInstance().closeSession(connectionId, handle.session);
            }
        }
    }
}
