package com.queryexe.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

import com.queryexe.engine.Dialect;
import com.queryexe.engine.h2.H2Dialect;
import com.queryexe.engine.mariadb.MariaDbDialect;
import com.queryexe.engine.mysql.MySqlDialect;
import com.queryexe.engine.postgres.PostgresDialect;
import com.queryexe.engine.sqlite.SqliteDialect;
import com.queryexe.engine.sqlserver.SqlServerDialect;

import com.queryexe.schema.ColumnData;
import com.queryexe.schema.ForeignKeyData;
import com.queryexe.query.RowTarget;

/**
 * Characterization ("golden string") tests for the SQL each engine generates without touching a database: quoting,
 * qualified names, SELECT, CREATE TABLE, ALTER TABLE and row INSERT scripts. The expected text lives in
 * {@code src/test/resources/golden/<engine>.txt}. These files pin today's behaviour so a refactor of the engine classes
 * can be checked against it: a difference is either a regression or an intended change, in which case regenerate the
 * files with {@code mvn test -Dgolden.update=true} and review the diff.
 * <p>
 * A call that needs a live connection is recorded as {@code !! <ExceptionClass>}, which is also pinned.
 */
class DialectGoldenTest {

    private static final Path DIR = Path.of("src", "test", "resources", "golden");

    private static Map<String, Dialect> engines() {
        Map<String, Dialect> m = new LinkedHashMap<>();
        m.put("mysql", new MySqlDialect());
        m.put("mariadb", new MariaDbDialect());
        m.put("postgres", new PostgresDialect());
        m.put("sqlserver", new SqlServerDialect());
        m.put("h2", new H2Dialect());
        m.put("sqlite", new SqliteDialect());
        return m;
    }

    private static ColumnData col(String name, String type, boolean pk, boolean notNull, boolean unique, boolean auto, String def) {
        ColumnData c = new ColumnData(name, type, pk, notNull, unique, auto);
        c.setDefaultValue(def);
        return c;
    }

    private static List<ColumnData> columns() {
        List<ColumnData> l = new ArrayList<>();
        l.add(col("id", "INT", true, true, false, true, null));
        l.add(col("email", "VARCHAR(120)", false, true, true, false, null));
        l.add(col("price", "DECIMAL(10,2)", false, false, false, false, "0"));
        l.add(col("order", "VARCHAR(20)", false, false, false, false, "'new'"));
        return l;
    }

    private static List<ColumnData> columnsWithoutLast() {
        List<ColumnData> l = columns();
        l.remove(l.size() - 1);
        return l;
    }

    private static List<ColumnData> columnsWithOwner() {
        List<ColumnData> l = columns();
        l.add(col("owner_id", "INT", false, false, false, false, null));
        return l;
    }

    private static ForeignKeyData fk() {
        return new ForeignKeyData("fk_orders_owner", "owner_id", "owners", "id", "CASCADE", "NO ACTION");
    }

    private static RowTarget target(String qualified) {
        return new RowTarget() {
            public String tableName() { return "orders"; }
            public String qualifiedTableName() { return qualified; }
            public List<String> columnNames() { return List.of("id", "email", "price", "order"); }
            public int columnSqlType(int index) { return new int[] { Types.INTEGER, Types.VARCHAR, Types.DECIMAL, Types.VARCHAR }[index]; }
        };
    }

    private static String describe(String label, Callable<Object> call) {
        String result;
        try {
            Object value = call.call();
            result = value instanceof Object[] a ? String.join(", ", Arrays.stream(a).map(String::valueOf).toList()) : String.valueOf(value);
        } catch (Throwable t) {
            result = "!! " + t.getClass().getSimpleName();
        }
        return "### " + label + "\n" + result.strip() + "\n\n";
    }

    /** Set.of iteration order differs between JVM runs, so sort every [a, b] list in a toString. */
    private static String sortSets(String s) {
        return java.util.regex.Pattern.compile("\\[([^\\]]*)\\]").matcher(s).replaceAll(m -> {
            String[] parts = m.group(1).isEmpty() ? new String[0] : m.group(1).split(", ");
            Arrays.sort(parts);
            return java.util.regex.Matcher.quoteReplacement("[" + String.join(", ", parts) + "]");
        });
    }

    private static String render(Dialect c) {
        StringBuilder out = new StringBuilder();
        out.append(describe("syntax", () -> sortSets(String.valueOf(c.getSqlSyntax()))));
        out.append(describe("dataTypes", c::getDataTypes));
        out.append(describe("quoteIdentifier plain", () -> c.quoteIdentifier("orders")));
        out.append(describe("quoteIdentifier hostile", () -> c.quoteIdentifier("a\"b`c]d'e")));
        out.append(describe("qualifyTable catalog+schema", () -> c.qualifyTable("cat", "sch", "orders")));
        out.append(describe("qualifyTable schema only", () -> c.qualifyTable(null, "sch", "orders")));
        out.append(describe("qualifyTable catalog only", () -> c.qualifyTable("cat", null, "orders")));
        out.append(describe("qualifyTable table only", () -> c.qualifyTable(null, null, "orders")));
        out.append(describe("defaultsOnlyInsert", () -> c.generateDefaultsOnlyInsert(c.qualifyTable(null, "sch", "orders"))));
        out.append(describe("select", () -> c.generateSelectScript("orders", "shop", 100)));
        out.append(describe("create table", () -> c.generateCreateTableSQL("shop", "orders", columns(), List.of())));
        out.append(describe("create table with fk", () -> c.generateCreateTableSQL("shop", "orders", columnsWithOwner(), List.of(fk()))));
        out.append(describe("alter: add column", () -> c.generateAlterTableSQL("shop", "orders", "orders",
                columns(), columnsWithOwner(), Map.of(), List.of(), Map.of())));
        out.append(describe("alter: drop column", () -> c.generateAlterTableSQL("shop", "orders", "orders",
                columns(), columnsWithoutLast(), Map.of(), List.of(), Map.of())));
        out.append(describe("alter: rename table", () -> c.generateAlterTableSQL("shop", "orders", "orders2",
                columns(), columns(), Map.of(), List.of(), Map.of())));
        out.append(describe("alter: rename column", () -> {
            List<ColumnData> renamed = columns();
            renamed.get(1).setColumnName("mail");
            return c.generateAlterTableSQL("shop", "orders", "orders", columns(), renamed, Map.of(), List.of(), Map.of("email", "mail"));
        }));
        out.append(describe("alter: change type", () -> {
            List<ColumnData> changed = columns();
            changed.get(1).setDataType("VARCHAR(255)");
            return c.generateAlterTableSQL("shop", "orders", "orders", columns(), changed, Map.of(), List.of(), Map.of());
        }));
        out.append(describe("alter: add fk", () -> c.generateAlterTableSQL("shop", "orders", "orders",
                columnsWithOwner(), columnsWithOwner(), Map.of(), List.of(fk()), Map.of())));
        out.append(describe("row insert", () -> c.generateRowInsertScript(List.of("1", "a@b.c", "9.50", "it's"), target(c.qualifyTable(null, "sch", "orders")))));
        return out.toString();
    }

    @Test
    void generatedSqlMatchesTheGoldenFiles() throws IOException {
        boolean update = Boolean.getBoolean("golden.update");
        Files.createDirectories(DIR);
        for (Map.Entry<String, Dialect> e : engines().entrySet()) {
            Path file = DIR.resolve(e.getKey() + ".txt");
            String actual = render(e.getValue());
            if (update || !Files.exists(file)) {
                Files.writeString(file, actual, StandardCharsets.UTF_8);
                continue;
            }
            String expected = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertEquals(expected, actual.replace("\r\n", "\n"), "golden mismatch for " + e.getKey() + " (regenerate with -Dgolden.update=true if intended)");
        }
    }
}
