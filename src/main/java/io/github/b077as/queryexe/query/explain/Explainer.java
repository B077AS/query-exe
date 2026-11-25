package io.github.b077as.queryexe.query.explain;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Asks the engine for the execution plan of a statement and turns the answer into a {@link Plan}. Each engine says it
 * differently (JSON, an indented text tree, a table, XML); the parsers here bring them to one tree. Blocking JDBC work.
 * <p>
 * With {@code analyze} the statement is really run to measure it, so it is run inside a transaction that is rolled back:
 * what an INSERT or DELETE would have done is not kept.
 */
@UtilityClass
public class Explainer {

    private static final Pattern MYSQL_COST = Pattern.compile("\\(cost=([\\d.eE+-]+) rows=([\\d.eE+-]+)\\)");
    private static final Pattern MYSQL_ACTUAL = Pattern.compile("\\(actual time=([\\d.eE+-]+)\\.\\.([\\d.eE+-]+) rows=([\\d.eE+-]+) loops=([\\d.eE+-]+)\\)");
    private static final Pattern MYSQL_TABLE = Pattern.compile(" on ([`\\w.]+)");

    public static boolean canAnalyze(String dbType) {
        return dbType.equals("PostgreSQL") || dbType.equals("MySQL") || dbType.equals("MariaDB");
    }

    /**
     * @param onStatement told about the JDBC statement while it runs (so it can be cancelled) and with null when it is done
     */
    public static Plan explain(Connection connection, String dbType, String sql, boolean analyze, Consumer<Statement> onStatement) throws SQLException {
        String text = sql.strip();
        while (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1).stripTrailing();
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("There is nothing to explain.");
        }
        if (text.regionMatches(true, 0, "explain", 0, 7)) {
            throw new IllegalArgumentException("This is already an EXPLAIN statement.");
        }
        boolean measure = analyze && canAnalyze(dbType);
        Plan plan = new Plan();
        plan.setEngine(dbType);
        plan.setSql(text);
        plan.setAnalyzed(measure);
        plan.setCanAnalyze(canAnalyze(dbType));

        switch (dbType) {
            case "PostgreSQL" -> postgres(connection, text, measure, onStatement, plan);
            case "MySQL" -> mysql(connection, text, measure, onStatement, plan);
            case "MariaDB" -> mariadb(connection, text, measure, onStatement, plan);
            case "SQLServer" -> sqlServer(connection, text, onStatement, plan);
            case "SQLite" -> sqlite(connection, text, onStatement, plan);
            default -> plainText(connection, text, onStatement, plan);
        }
        return plan;
    }

    // ------------------------------------------------------------------------------------ running

    private interface SqlBody {
        void run() throws SQLException;
    }

    /** Runs {@code body}; when the statement is measured, inside a transaction (or savepoint) that is always undone. */
    private static void inRollback(Connection connection, boolean measure, SqlBody body) throws SQLException {
        if (!measure) {
            body.run();
            return;
        }
        boolean auto = connection.getAutoCommit();
        Savepoint savepoint = null;
        if (auto) {
            connection.setAutoCommit(false);
        } else {
            savepoint = connection.setSavepoint();
        }
        try {
            body.run();
        } finally {
            try {
                if (savepoint != null) {
                    connection.rollback(savepoint);
                } else {
                    connection.rollback();
                }
            } finally {
                if (auto) {
                    connection.setAutoCommit(true);
                }
            }
        }
    }

    /** The rows of a query as text cells, with the column labels. */
    private record Table(List<String> columns, List<List<String>> rows) {
        String cell(List<String> row, String column) {
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).equalsIgnoreCase(column)) {
                    return row.get(i);
                }
            }
            return null;
        }
    }

    private static Table query(Connection connection, String sql, Consumer<Statement> onStatement) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            onStatement.accept(statement);
            try (ResultSet rs = statement.executeQuery(sql)) {
                return read(rs);
            }
        } finally {
            onStatement.accept(null);
        }
    }

    private static Table read(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            columns.add(meta.getColumnLabel(i));
        }
        List<List<String>> rows = new ArrayList<>();
        while (rs.next()) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= columns.size(); i++) {
                row.add(rs.getString(i));
            }
            rows.add(row);
        }
        return new Table(columns, rows);
    }

    /** The first column of every row, one per line (how Postgres, MySQL, SQL Server and H2 hand over a plan). */
    private static String firstColumn(Table table) {
        StringBuilder out = new StringBuilder();
        for (List<String> row : table.rows()) {
            out.append(row.get(0) == null ? "" : row.get(0)).append('\n');
        }
        return out.toString().stripTrailing();
    }

    private static String tableText(Table table) {
        StringBuilder out = new StringBuilder(String.join("\t", table.columns())).append('\n');
        for (List<String> row : table.rows()) {
            out.append(String.join("\t", row.stream().map(v -> v == null ? "NULL" : v).toList())).append('\n');
        }
        return out.toString().stripTrailing();
    }

    // ------------------------------------------------------------------------------------ PostgreSQL

    private static void postgres(Connection connection, String sql, boolean analyze, Consumer<Statement> onStatement, Plan plan) throws SQLException {
        String options = analyze ? "ANALYZE, BUFFERS, FORMAT JSON" : "FORMAT JSON";
        Table[] table = new Table[1];
        inRollback(connection, analyze, () -> table[0] = query(connection, "EXPLAIN (" + options + ") " + sql, onStatement));
        String json = firstColumn(table[0]);
        plan.setRaw(json);
        JsonElement root = JsonParser.parseString(json);
        JsonObject top = root.isJsonArray() ? root.getAsJsonArray().get(0).getAsJsonObject() : root.getAsJsonObject();
        plan.getRoots().add(postgresNode(top.getAsJsonObject("Plan")));
        plan.setPlanningMs(number(top, "Planning Time"));
        plan.setExecutionMs(number(top, "Execution Time"));
    }

    private static PlanNode postgresNode(JsonObject json) {
        PlanNode node = new PlanNode(json.get("Node Type").getAsString());
        String relation = string(json, "Relation Name");
        node.setRelation(relation != null ? relation : string(json, "Index Name"));
        node.setCost(number(json, "Total Cost"));
        node.setRows(number(json, "Plan Rows"));
        if (json.has("Actual Total Time")) {
            Double loops = number(json, "Actual Loops");
            double n = loops == null ? 1 : loops;
            node.setActualMs(number(json, "Actual Total Time") * n);
            node.setLoops(loops);
            Double actualRows = number(json, "Actual Rows");
            node.setActualRows(actualRows == null ? null : actualRows * n);
        }
        List<String> detail = new ArrayList<>();
        for (String key : new String[]{"Index Cond", "Recheck Cond", "Hash Cond", "Merge Cond", "Join Filter", "Filter"}) {
            String value = stringOrArray(json, key);
            if (value != null) {
                detail.add(value);
            }
        }
        for (String key : new String[]{"Sort Key", "Group Key"}) {
            String value = stringOrArray(json, key);
            if (value != null) {
                detail.add(key + ": " + value);
            }
        }
        node.setDetail(detail.isEmpty() ? null : String.join("  ·  ", detail));
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getKey().equals("Plans") || entry.getKey().equals("Node Type")) {
                continue;
            }
            if (entry.getValue().isJsonPrimitive() || entry.getValue().isJsonArray()) {
                node.attr(entry.getKey(), stringOrArray(json, entry.getKey()));
            }
        }
        if (json.has("Plans")) {
            for (JsonElement child : json.getAsJsonArray("Plans")) {
                node.getChildren().add(postgresNode(child.getAsJsonObject()));
            }
        }
        return node;
    }

    // ------------------------------------------------------------------------------------ MySQL / MariaDB

    private static void mysql(Connection connection, String sql, boolean analyze, Consumer<Statement> onStatement, Plan plan) throws SQLException {
        Table[] table = new Table[1];
        try {
            inRollback(connection, analyze, () -> table[0] = query(connection, (analyze ? "EXPLAIN ANALYZE " : "EXPLAIN FORMAT=TREE ") + sql, onStatement));
        } catch (SQLException e) {
            if (analyze) {
                throw e;
            }
            // before 8.0.16 there is no tree format: the table is all there is
            Table flat = query(connection, "EXPLAIN " + sql, onStatement);
            plan.setRaw(tableText(flat));
            flatTable(flat, plan);
            return;
        }
        String text = firstColumn(table[0]);
        plan.setRaw(text);
        mysqlTree(text, plan);
    }

    /** The tree MySQL prints: "-> step  (cost=.. rows=..) (actual time=a..b rows=r loops=l)", four spaces deeper for each child. */
    static void mysqlTree(String text, Plan plan) {
        Deque<Object[]> stack = new ArrayDeque<>(); // {column of the arrow, node}
        PlanNode last = null;
        for (String line : text.split("\n")) {
            int arrow = line.indexOf("-> ");
            if (arrow < 0) {
                if (last != null && !line.isBlank()) {
                    last.setDetail((last.getDetail() == null ? "" : last.getDetail() + " ") + line.strip());
                }
                continue;
            }
            PlanNode node = mysqlNode(line.substring(arrow + 3));
            while (!stack.isEmpty() && (int) stack.peek()[0] >= arrow) {
                stack.pop();
            }
            if (stack.isEmpty()) {
                plan.getRoots().add(node);
            } else {
                ((PlanNode) stack.peek()[1]).getChildren().add(node);
            }
            stack.push(new Object[]{arrow, node});
            last = node;
        }
    }

    private static PlanNode mysqlNode(String body) {
        Matcher cost = MYSQL_COST.matcher(body);
        Matcher actual = MYSQL_ACTUAL.matcher(body);
        boolean hasCost = cost.find();
        boolean hasActual = actual.find();
        boolean never = body.contains("(never executed)");

        // the description is whatever comes before the first of the figures
        int end = body.length();
        if (hasCost) {
            end = Math.min(end, cost.start());
        }
        if (hasActual) {
            end = Math.min(end, actual.start());
        }
        if (never) {
            end = Math.min(end, body.indexOf("(never executed)"));
        }
        String label = body.substring(0, end).strip();
        String op = label;
        String detail = null;
        int colon = label.indexOf(": ");
        if (colon > 0) {
            op = label.substring(0, colon);
            detail = label.substring(colon + 2).strip();
        }
        PlanNode node = new PlanNode(op);
        node.setDetail(detail);
        if (hasCost) {
            node.setCost(Double.parseDouble(cost.group(1)));
            node.setRows(Double.parseDouble(cost.group(2)));
        }
        if (hasActual) {
            double loops = Double.parseDouble(actual.group(4));
            node.setLoops(loops);
            node.setActualMs(Double.parseDouble(actual.group(2)) * loops);
            node.setActualRows(Double.parseDouble(actual.group(3)) * loops);
        } else if (never) {
            node.setLoops(0.0);
            node.setActualMs(0.0);
            node.setActualRows(0.0);
        }
        // "Table scan on orders" / "Index lookup on o using idx": the table is what follows "on"
        Matcher on = MYSQL_TABLE.matcher(op);
        if (on.find()) {
            node.setRelation(on.group(1).replace("`", ""));
        }
        return node;
    }

    private static void mariadb(Connection connection, String sql, boolean analyze, Consumer<Statement> onStatement, Plan plan) throws SQLException {
        Table[] table = new Table[1];
        inRollback(connection, analyze, () -> table[0] = query(connection, (analyze ? "ANALYZE " : "EXPLAIN ") + sql, onStatement));
        plan.setRaw(tableText(table[0]));
        flatTable(table[0], plan);
    }

    /** The classic EXPLAIN table: one step per row, in order, no nesting. */
    private static void flatTable(Table table, Plan plan) {
        for (List<String> row : table.rows()) {
            String type = table.cell(row, "type");
            String select = table.cell(row, "select_type");
            String id = table.cell(row, "id");
            PlanNode node = new PlanNode(type == null || type.isBlank() ? (select == null ? "Step" : select) : type);
            node.setRelation(table.cell(row, "table"));
            node.setRows(parse(table.cell(row, "rows")));
            node.setActualRows(parse(table.cell(row, "r_rows")));
            node.setActualMs(parse(table.cell(row, "r_total_time_ms")));
            String key = table.cell(row, "key");
            String extra = table.cell(row, "Extra");
            List<String> detail = new ArrayList<>();
            if (key != null) {
                detail.add("key " + key);
            }
            if (extra != null && !extra.isBlank()) {
                detail.add(extra);
            }
            node.setDetail(detail.isEmpty() ? null : String.join("  ·  ", detail));
            if (id != null && select != null) {
                node.attr("Select", select + " #" + id);
            }
            for (int i = 0; i < table.columns().size(); i++) {
                node.attr(table.columns().get(i), row.get(i));
            }
            plan.getRoots().add(node);
        }
    }

    // ------------------------------------------------------------------------------------ SQLite

    private static final String[] SQLITE_OPS = {"SCAN", "SEARCH", "USE TEMP B-TREE", "CO-ROUTINE", "MATERIALIZE", "CORRELATED SCALAR SUBQUERY",
        "SCALAR SUBQUERY", "LIST SUBQUERY", "COMPOUND QUERY", "MULTI-INDEX OR", "LEFT-MOST SUBQUERY", "RIGHT-MOST SUBQUERY", "MERGE", "UNION", "INDEX"};

    private static void sqlite(Connection connection, String sql, Consumer<Statement> onStatement, Plan plan) throws SQLException {
        Table table = query(connection, "EXPLAIN QUERY PLAN " + sql, onStatement);
        plan.setRaw(tableText(table));
        Map<String, PlanNode> byId = new HashMap<>();
        for (List<String> row : table.rows()) {
            String detail = table.cell(row, "detail");
            PlanNode node = sqliteNode(detail == null ? "" : detail);
            byId.put(table.cell(row, "id"), node);
            PlanNode parent = byId.get(table.cell(row, "parent"));
            if (parent == null) {
                plan.getRoots().add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
    }

    private static PlanNode sqliteNode(String detail) {
        String op = detail;
        String rest = null;
        for (String known : SQLITE_OPS) {
            if (detail.startsWith(known)) {
                op = known;
                rest = detail.substring(known.length()).strip();
                break;
            }
        }
        PlanNode node = new PlanNode(op);
        node.setDetail(rest == null || rest.isEmpty() ? null : rest);
        if (rest != null && (op.equals("SCAN") || op.equals("SEARCH"))) {
            String table = rest.split(" ")[0];
            node.setRelation(table.equals("SUBQUERY") ? null : table);
        }
        node.attr("Detail", detail);
        return node;
    }

    // ------------------------------------------------------------------------------------ SQL Server

    private static void sqlServer(Connection connection, String sql, Consumer<Statement> onStatement, Plan plan) throws SQLException {
        // with SHOWPLAN on, statements are compiled, not run, and the plan comes back as a one-cell result
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SHOWPLAN_XML ON");
        }
        String xml;
        try {
            xml = firstColumn(query(connection, sql, onStatement));
        } finally {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET SHOWPLAN_XML OFF");
            }
        }
        plan.setRaw(xml);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            for (Element relOp : nearestRelOps(document.getDocumentElement())) {
                plan.getRoots().add(sqlServerNode(relOp));
            }
        } catch (Exception e) {
            throw new SQLException("Could not read the plan: " + e.getMessage(), e);
        }
    }

    /** The RelOps below {@code from} with no other RelOp in between. */
    private static List<Element> nearestRelOps(Node from) {
        List<Element> found = new ArrayList<>();
        NodeList children = from.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element) {
                if (element.getTagName().equals("RelOp")) {
                    found.add(element);
                } else {
                    found.addAll(nearestRelOps(element));
                }
            }
        }
        return found;
    }

    private static PlanNode sqlServerNode(Element relOp) {
        String physical = relOp.getAttribute("PhysicalOp");
        String logical = relOp.getAttribute("LogicalOp");
        PlanNode node = new PlanNode(physical.isEmpty() ? logical : physical);
        node.setRows(parse(relOp.getAttribute("EstimateRows")));
        node.setCost(parse(relOp.getAttribute("EstimatedTotalSubtreeCost")));
        if (!logical.isEmpty() && !logical.equals(physical)) {
            node.attr("Logical operation", logical);
        }
        for (String name : new String[]{"EstimateIO", "EstimateCPU", "AvgRowSize", "Parallel", "NodeId", "EstimateRebinds", "EstimateRewinds"}) {
            node.attr(name, relOp.getAttribute(name));
        }
        Element object = firstOwn(relOp, "Object");
        if (object != null) {
            String table = object.getAttribute("Table").replace("[", "").replace("]", "");
            String index = object.getAttribute("Index").replace("[", "").replace("]", "");
            node.setRelation(table.isEmpty() ? null : table);
            node.setDetail(index.isEmpty() ? null : "index " + index);
        }
        Element predicate = firstOwn(relOp, "ScalarOperator");
        if (predicate != null && predicate.hasAttribute("ScalarString")) {
            String text = predicate.getAttribute("ScalarString");
            node.setDetail(node.getDetail() == null ? text : node.getDetail() + "  ·  " + text);
        }
        for (Element child : nearestRelOps(relOp)) {
            node.getChildren().add(sqlServerNode(child));
        }
        return node;
    }

    /** The first element called {@code name} under the step but not inside one of its child steps. */
    private static Element firstOwn(Element from, String name) {
        NodeList children = from.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element && !element.getTagName().equals("RelOp")) {
                if (element.getTagName().equals(name)) {
                    return element;
                }
                Element deeper = firstOwn(element, name);
                if (deeper != null) {
                    return deeper;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------ the rest (H2...)

    private static void plainText(Connection connection, String sql, Consumer<Statement> onStatement, Plan plan) throws SQLException {
        String text = firstColumn(query(connection, "EXPLAIN " + sql, onStatement));
        plan.setRaw(text);
        PlanNode node = new PlanNode("Plan");
        node.setDetail(text);
        plan.getRoots().add(node);
    }

    // ------------------------------------------------------------------------------------ small helpers

    private static String string(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
    }

    private static Double number(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : null;
    }

    private static String stringOrArray(JsonObject json, String key) {
        if (!json.has(key)) {
            return null;
        }
        JsonElement value = json.get(key);
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            List<String> parts = new ArrayList<>();
            array.forEach(e -> parts.add(e.isJsonPrimitive() ? e.getAsString() : e.toString()));
            return String.join(", ", parts);
        }
        return null;
    }

    private static Double parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
