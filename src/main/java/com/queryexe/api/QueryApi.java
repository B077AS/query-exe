package com.queryexe.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.queryexe.app.Params;
import com.queryexe.app.Rpc;
import com.queryexe.query.grid.GridModel;
import com.queryexe.connection.ConnectionProfile;
import com.queryexe.connection.DatabaseConnection;
import com.queryexe.query.Exporter;
import com.queryexe.store.RecentFilesManager;
import com.queryexe.query.Workspace;
import com.queryexe.query.SqlSplitter;

import lombok.extern.slf4j.Slf4j;

/** Running scripts, editing result grids, exporting, and the .sql files on disk. */
@Slf4j
class QueryApi {

    private Workspace workspace;
    private FileChooser chooser;

    QueryApi(Workspace workspace, FileChooser chooser) {
        this.workspace = workspace;
        this.chooser = chooser;
    }

    void register(Rpc rpc) {
        rpc.on("query.run", this::run);
        rpc.on("query.cancel", p -> {
            workspace.cancel(Params.string(p, "tabId"));
            return Map.of("ok", true);
        });
        rpc.on("query.closeTab", p -> {
            workspace.closeTab(Params.string(p, "tabId"));
            return Map.of("ok", true);
        });

        rpc.on("grid.edit", p -> {
            String value = p.get("value") == null || p.get("value").isJsonNull() ? null : p.get("value").getAsString();
            return grid(p).edit(Params.integer(p, "row"), Params.integer(p, "column"), value);
        });
        rpc.on("grid.cell", p -> {
            Map<String, Object> out = new HashMap<>();
            out.put("text", grid(p).cellText(Params.integer(p, "row"), Params.integer(p, "column")));
            return out;
        });
        rpc.on("grid.addRow", p -> grid(p).addRow());
        rpc.on("grid.duplicateRow", p -> grid(p).duplicateRow(Params.integer(p, "row")));
        rpc.on("grid.deleteRows", p -> grid(p).deleteRows(ints(p, "rows")));
        rpc.on("grid.revert", p -> grid(p).revert());
        rpc.on("grid.apply", p -> workspace.applyGrid(Params.string(p, "gridId")));
        rpc.on("grid.rowScript", p -> Map.of("sql", grid(p).rowInsertScript(Params.integer(p, "row"))));
        rpc.on("grid.export", this::export);
        rpc.on("grid.copy", p -> {
            GridModel grid = grid(p);
            return Map.of("text", tsv(grid.texts(p.has("rows") ? ints(p, "rows") : null)));
        });

        rpc.on("file.open", this::openFile);
        rpc.on("file.openPath", p -> read(Path.of(Params.string(p, "path")), Params.optionalString(p, "connectionId")));
        rpc.on("file.save", this::saveFile);
        rpc.on("file.pickDatabase", p -> {
            String type = Params.string(p, "type");
            String[] extensions = type.equals("SQLite") ? new String[]{"db", "sqlite", "sqlite3", "db3"} : new String[]{"db"};
            Optional<Path> chosen = chooser.open("Select " + type + " database", type + " files", extensions);
            return chosen.isEmpty() ? Map.of("cancelled", true) : Map.of("path", chosen.get().toString());
        });
        rpc.on("file.recent", p -> recent());
        rpc.on("file.recentRemove", p -> {
            RecentFilesManager.getInstance().removeRecentFile(Params.string(p, "path"));
            return Map.of("ok", true);
        });
        rpc.on("file.recentClear", p -> {
            RecentFilesManager.getInstance().clearRecentFiles();
            return Map.of("ok", true);
        });
    }

    private GridModel grid(JsonObject p) {
        return workspace.grid(Params.string(p, "gridId"));
    }

    private static List<Integer> ints(JsonObject p, String key) {
        List<Integer> out = new ArrayList<>();
        for (JsonElement element : p.getAsJsonArray(key)) {
            out.add(element.getAsInt());
        }
        return out;
    }

    // ------------------------------------------------------------------------------------ running

    /**
     * mode "all" runs the whole text, "selection" the selected text, "statement" the statement the caret is in.
     * Returns at once; results arrive as events.
     */
    private Object run(JsonObject p) {
        String connectionId = Params.string(p, "connectionId");
        ConnectionProfile connection = DatabaseConnection.getInstance().getProfile(connectionId);
        if (connection == null) {
            throw new IllegalStateException("This connection is not open. Connect first.");
        }
        String text = Params.string(p, "text");
        String mode = Params.optionalString(p, "mode");
        List<Workspace.StatementRange> statements = new ArrayList<>();

        if (mode.equals("statement")) {
            int caret = Params.integer(p, "caret");
            Optional<SqlSplitter.Statement> at = SqlSplitter.statementAt(text, caret, connection.dialect().getSqlSyntax());
            at.ifPresent(s -> statements.add(new Workspace.StatementRange(s.getText(), s.getFrom(), s.getTo())));
        } else {
            int offset = mode.equals("selection") ? Params.integer(p, "offset") : 0;
            for (SqlSplitter.Statement s : SqlSplitter.split(text, connection.dialect().getSqlSyntax())) {
                statements.add(new Workspace.StatementRange(s.getText(), s.getFrom() + offset, s.getTo() + offset));
            }
        }
        if (statements.isEmpty()) {
            throw new IllegalArgumentException("There is nothing to run.");
        }
        String runId = UUID.randomUUID().toString();
        workspace.run(Params.string(p, "tabId"), connectionId, runId, statements);
        return Map.of("runId", runId, "count", statements.size());
    }

    // ------------------------------------------------------------------------------------ export and copy

    private Object export(JsonObject p) throws IOException {
        GridModel grid = grid(p);
        String format = Params.string(p, "format");
        if (!Exporter.FORMATS.contains(format)) {
            throw new IllegalArgumentException("Unknown format: " + format);
        }
        Optional<Path> target = chooser.save("Export result", "result." + format, format.toUpperCase() + " files", format);
        if (target.isEmpty()) {
            return Map.of("cancelled", true);
        }
        List<Integer> rows = p.has("rows") ? ints(p, "rows") : null;
        Exporter.write(target.get(), format, grid.columnLabels(), grid.texts(rows));
        return Map.of("path", target.get().toString());
    }

    private static String tsv(List<String[]> rows) {
        StringBuilder out = new StringBuilder();
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    out.append('\t');
                }
                out.append(row[i] == null ? "" : row[i].replace("\t", " ").replace("\r\n", " ").replace('\n', ' '));
            }
            out.append('\n');
        }
        return out.toString();
    }

    // ------------------------------------------------------------------------------------ files

    private Object openFile(JsonObject p) throws IOException {
        Optional<Path> chosen = chooser.open("Open SQL file", "SQL files", "sql");
        if (chosen.isEmpty()) {
            return Map.of("cancelled", true);
        }
        return read(chosen.get(), Params.optionalString(p, "connectionId"));
    }

    private Object read(Path path, String connectionId) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("The file no longer exists: " + path);
        }
        String text = Files.readString(path, StandardCharsets.UTF_8);
        RecentFilesManager.getInstance().addRecentFile(path.toFile(), connectionId.isEmpty() ? null : connectionId);
        Map<String, Object> out = new HashMap<>();
        out.put("path", path.toString());
        out.put("name", baseName(path));
        out.put("text", text);
        return out;
    }

    /** Saves to {@code path}, or asks where when there is none (or when {@code saveAs} is set). */
    private Object saveFile(JsonObject p) throws IOException {
        String given = Params.optionalString(p, "path");
        boolean saveAs = p.has("saveAs") && p.get("saveAs").getAsBoolean();
        Path target;
        if (given.isEmpty() || saveAs) {
            String suggested = Params.optionalString(p, "name");
            Optional<Path> chosen = chooser.save("Save SQL file", (suggested.isEmpty() ? "query" : suggested) + ".sql", "SQL files", "sql");
            if (chosen.isEmpty()) {
                return Map.of("cancelled", true);
            }
            target = chosen.get();
            if (!target.getFileName().toString().contains(".")) {
                target = target.resolveSibling(target.getFileName() + ".sql");
            }
        } else {
            target = Path.of(given);
        }
        Files.writeString(target, Params.string(p, "text"), StandardCharsets.UTF_8);
        String connectionId = Params.optionalString(p, "connectionId");
        RecentFilesManager.getInstance().addRecentFile(target.toFile(), connectionId.isEmpty() ? null : connectionId);
        return Map.of("path", target.toString(), "name", baseName(target));
    }

    private static String baseName(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private Object recent() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RecentFilesManager.Entry entry : RecentFilesManager.getInstance().getRecentEntries()) {
            Map<String, Object> item = new HashMap<>();
            item.put("path", entry.getPath());
            item.put("name", baseName(Path.of(entry.getPath())));
            item.put("connectionId", entry.getConnectionId());
            item.put("lastOpened", entry.getLastOpened());
            out.add(item);
        }
        return out;
    }
}
