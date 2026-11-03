package com.queryexe.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import com.queryexe.grid.GridModel;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.utils.SqlInspector;
import com.queryexe.utils.SqlSplitter;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * The query tabs of the window as far as Java is concerned: each editor tab has its own JDBC connection (so
 * transactions and {@code USE}/{@code SET} state don't leak between tabs), runs one script at a time, and keeps the
 * result grids of its last run so the page can edit them.
 */
@Slf4j
public final class Workspace {

    /** Rows kept per result set. */
    public static final int MAX_ROWS = 10_000;

    private final Map<String, Tab> tabs = new ConcurrentHashMap<>();
    private final Map<String, GridModel> grids = new ConcurrentHashMap<>();
    private final AtomicLong gridCounter = new AtomicLong();
    private final BiConsumer<String, Object> events;

    public Workspace(BiConsumer<String, Object> events) {
        this.events = events;
    }

    @Value
    public static class StatementRange {
        String text;
        int from;
        int to;
    }

    private static final class Tab {
        final String id;
        final ConnectionObject connectionObject;
        Connection session;
        String schemaState;
        volatile Statement running;
        volatile boolean cancelled;
        volatile boolean busy;
        final List<String> gridIds = new ArrayList<>();

        Tab(String id, ConnectionObject connectionObject) {
            this.id = id;
            this.connectionObject = connectionObject;
        }

        /** The tab's own connection, opened on first use and again if it was lost. Blocking: background threads only. */
        synchronized Connection connection() throws SQLException {
            DatabaseConnection databases = DatabaseConnection.getInstance();
            if (session == null || session.isClosed()) {
                session = databases.openSession(connectionObject);
                schemaState = null;
            }
            // the tree selects its database/schema on the shared connection; follow it unless a USE in this tab came later
            schemaState = databases.copyDefaultSchema(databases.sharedConnection(connectionObject.getId()), session, schemaState);
            return session;
        }

        synchronized void close() {
            if (session != null) {
                DatabaseConnection.getInstance().closeSession(connectionObject.getId(), session);
                session = null;
            }
        }
    }

    // ------------------------------------------------------------------------------------ tabs

    private Tab tab(String tabId, String connectionId) {
        ConnectionObject object = DatabaseConnection.getInstance().getConnectionObject(connectionId);
        if (object == null) {
            throw new IllegalStateException("This connection is not open. Connect first.");
        }
        Tab existing = tabs.get(tabId);
        if (existing != null && existing.connectionObject.getId().equals(connectionId)) {
            return existing;
        }
        if (existing != null) {
            closeTab(tabId);
        }
        Tab created = new Tab(tabId, object);
        tabs.put(tabId, created);
        return created;
    }

    public void closeTab(String tabId) {
        Tab tab = tabs.remove(tabId);
        if (tab == null) {
            return;
        }
        cancel(tab);
        dropGrids(tab);
        Async.run(tab::close);
    }

    /** Called when a connection goes away: its sessions are already closed by DatabaseConnection. */
    public void closeConnection(String connectionId) {
        for (Tab tab : new ArrayList<>(tabs.values())) {
            if (tab.connectionObject.getId().equals(connectionId)) {
                tabs.remove(tab.id);
                cancel(tab);
                dropGrids(tab);
            }
        }
    }

    private void dropGrids(Tab tab) {
        synchronized (tab.gridIds) {
            tab.gridIds.forEach(grids::remove);
            tab.gridIds.clear();
        }
    }

    public void cancel(String tabId) {
        Tab tab = tabs.get(tabId);
        if (tab != null) {
            cancel(tab);
        }
    }

    private void cancel(Tab tab) {
        tab.cancelled = true;
        Statement running = tab.running;
        if (running != null) {
            try {
                running.cancel();
            } catch (SQLException | RuntimeException e) {
                log.debug("Cancel failed: {}", e.getMessage());
            }
        }
    }

    public GridModel grid(String gridId) {
        GridModel grid = grids.get(gridId);
        if (grid == null) {
            throw new IllegalStateException("This result is no longer available. Run the query again.");
        }
        return grid;
    }

    /** The tab that owns a grid, for running its changes on the tab's own connection. */
    public Connection connectionForGrid(String gridId) throws SQLException {
        for (Tab tab : tabs.values()) {
            synchronized (tab.gridIds) {
                if (tab.gridIds.contains(gridId)) {
                    return tab.connection();
                }
            }
        }
        throw new SQLException("This result is no longer available. Run the query again.");
    }

    // ------------------------------------------------------------------------------------ running

    /** Splits editor text the way a run would, so the page can tell which statement the caret is in. */
    public static List<StatementRange> split(String text, ConnectionObject connection) {
        List<StatementRange> out = new ArrayList<>();
        for (SqlSplitter.Statement statement : SqlSplitter.split(text, connection.getSqlSyntax())) {
            out.add(new StatementRange(statement.getText(), statement.getFrom(), statement.getTo()));
        }
        return out;
    }

    /**
     * Starts running the statements on the tab's connection in the background and reports through events:
     * {@code query.started}, one {@code query.result} per result or error, {@code query.finished}.
     */
    public void run(String tabId, String connectionId, String runId, List<StatementRange> statements) {
        Tab tab = tab(tabId, connectionId);
        if (tab.busy) {
            throw new IllegalStateException("This tab is already running a script.");
        }
        tab.busy = true;
        tab.cancelled = false;
        dropGrids(tab);

        Async.run(() -> execute(tab, runId, statements));
    }

    private void execute(Tab tab, String runId, List<StatementRange> statements) {
        ConnectionObject connectionObject = tab.connectionObject;
        String schemaToSelect = null;
        boolean structureChanged = false;
        boolean databasesChanged = false;
        boolean ok = true;
        long started = System.currentTimeMillis();
        int[] index = {0};
        Map<String, Integer> positions = new IdentityHashMap<>();
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < statements.size(); i++) {
            String text = statements.get(i).getText();
            positions.put(text, i);
            texts.add(text);
        }

        try {
            events.accept("query.started", Map.of("tabId", tab.id, "runId", runId, "count", statements.size()));
            Connection connection = tab.connection();
            String[] effects = new String[3];

            ok = ScriptRunner.run(connection, texts, MAX_ROWS, new ScriptRunner.Listener() {
                @Override
                public void onStatementStarted(String sql, Statement statement) {
                    tab.running = statement;
                    index[0] = positions.getOrDefault(sql, 0);
                }

                @Override
                public void onResultSet(String sql, Statement statement, long millis) throws SQLException {
                    String gridId = "g" + gridCounter.incrementAndGet();
                    GridModel grid = new GridModel(gridId, statement, millis, MAX_ROWS, connectionObject);
                    grids.put(gridId, grid);
                    synchronized (tab.gridIds) {
                        tab.gridIds.add(gridId);
                    }
                    events.accept("query.result", Map.of("tabId", tab.id, "runId", runId, "index", index[0], "sql", sql,
                            "kind", "grid", "grid", grid.snapshot()));
                }

                @Override
                public void onUpdateCount(String sql, long count, long millis) {
                    events.accept("query.result", Map.of("tabId", tab.id, "runId", runId, "index", index[0], "sql", sql,
                            "kind", "count", "count", count, "millis", millis));
                }

                @Override
                public void onStatementSucceeded(String sql) {
                    tab.running = null;
                    SqlInspector.detectSchemaSwitch(sql, connectionObject.getDbType()).ifPresent(name -> effects[0] = name);
                    if (SqlInspector.changesDatabaseList(sql)) {
                        effects[1] = "databases";
                    } else if (SqlInspector.isDdl(sql)) {
                        effects[2] = "structure";
                    }
                }

                @Override
                public void onError(String sql, SQLException error) {
                    tab.running = null;
                    StatementRange range = statements.get(positions.getOrDefault(sql, 0));
                    String message = tab.cancelled ? "Cancelled by user." : message(error);
                    events.accept("query.result", Map.of("tabId", tab.id, "runId", runId, "index", index[0], "sql", sql,
                            "kind", "error", "message", message, "from", range.getFrom(), "to", range.getTo()));
                }
            });
            schemaToSelect = effects[0];
            databasesChanged = effects[1] != null;
            structureChanged = effects[2] != null;
        } catch (SQLException e) {
            ok = false;
            events.accept("query.result", Map.of("tabId", tab.id, "runId", runId, "index", 0, "sql", "",
                    "kind", "error", "message", "Could not open a connection: " + message(e), "from", 0, "to", 0));
        } catch (RuntimeException e) {
            ok = false;
            log.error("Script failed unexpectedly", e);
            events.accept("query.result", Map.of("tabId", tab.id, "runId", runId, "index", 0, "sql", "",
                    "kind", "error", "message", "Execution failed: " + e, "from", 0, "to", 0));
        } finally {
            tab.running = null;
            tab.busy = false;
        }

        Map<String, Object> done = new java.util.HashMap<>();
        done.put("tabId", tab.id);
        done.put("runId", runId);
        done.put("ok", ok);
        done.put("millis", System.currentTimeMillis() - started);
        done.put("connectionId", connectionObject.getId());
        done.put("schema", schemaToSelect);
        done.put("databasesChanged", databasesChanged);
        done.put("structureChanged", structureChanged);
        events.accept("query.finished", done);
    }

    static String message(SQLException error) {
        String text = error.getMessage() == null ? error.toString() : error.getMessage();
        return text.strip();
    }

    /** Applies a grid's pending changes on the connection of the tab that owns it. Blocking. */
    public GridModel.Snapshot applyGrid(String gridId) throws SQLException {
        GridModel grid = grid(gridId);
        return grid.apply(connectionForGrid(gridId));
    }
}
