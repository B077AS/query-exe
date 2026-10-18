package com.queryexe.model.connections.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.queryexe.model.connections.sqlite.SqliteCatalog.ColumnInfo;
import com.queryexe.model.connections.sqlite.SqliteCatalog.ForeignKeyInfo;
import com.queryexe.model.connections.sqlite.SqliteCatalog.IndexInfo;

class SqliteCatalogTest {

    private Connection connection;
    private SqliteCatalog catalog;

    @BeforeEach
    void fixture(@TempDir Path dir) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("main.db"));
        catalog = new SqliteCatalog(connection);
        String aux = dir.resolve("aux.db").toString().replace("\\", "/");
        execute("ATTACH DATABASE '" + aux + "' AS aux",
                "CREATE TABLE parent(id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT UNIQUE, name VARCHAR(50) NOT NULL DEFAULT 'x', "
                        + "price DECIMAL(10,2), created DATETIME DEFAULT CURRENT_TIMESTAMP, total INTEGER GENERATED ALWAYS AS (id * 2) VIRTUAL)",
                "CREATE TABLE p2(a INTEGER, b INTEGER, PRIMARY KEY(a,b))",
                "CREATE TABLE child(id INTEGER PRIMARY KEY, pid INTEGER REFERENCES parent(id) ON DELETE CASCADE, "
                        + "qid INTEGER REFERENCES parent(id), a INTEGER, b INTEGER, mail TEXT, "
                        + "FOREIGN KEY(a,b) REFERENCES p2(a,b), UNIQUE(mail, a))",
                "CREATE TABLE implicit(id INTEGER PRIMARY KEY, pid INTEGER REFERENCES parent)",
                "CREATE TABLE \"my table\"(\"Id\" INTEGER PRIMARY KEY, \"weird col\" TEXT)",
                "CREATE TABLE \"order\"(id INTEGER PRIMARY KEY, \"group\" TEXT)",
                "CREATE TABLE aux.parent(id INTEGER PRIMARY KEY, auxcol TEXT)",
                "CREATE TABLE aux.only_aux(id INTEGER PRIMARY KEY, z TEXT)",
                "CREATE INDEX idx_child_pid ON child(pid)",
                "CREATE TRIGGER trg_parent AFTER INSERT ON parent BEGIN UPDATE parent SET name = name WHERE id = NEW.id; END");
    }

    @AfterEach
    void close() throws SQLException {
        connection.close();
    }

    private void execute(String... statements) throws SQLException {
        try (Statement s = connection.createStatement()) {
            for (String statement : statements) {
                s.execute(statement);
            }
        }
    }

    private ColumnInfo column(String schema, String table, String name) throws SQLException {
        return catalog.columns(schema, table).stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void awkwardTableNamesDoNotBreakAnything() throws SQLException {
        assertEquals(List.of("child", "implicit", "my table", "order", "p2", "parent"), catalog.tables("main"));
        assertEquals(List.of("Id", "weird col"), catalog.columns("main", "my table").stream().map(ColumnInfo::getName).toList());
        assertEquals(List.of("id", "group"), catalog.columns("main", "order").stream().map(ColumnInfo::getName).toList());
        assertTrue(catalog.foreignKeys("main", "my table").isEmpty());
    }

    @Test
    void attachedDatabasesAreSeparate() throws SQLException {
        assertEquals(List.of("main", "aux"), catalog.databases());
        assertEquals(List.of("only_aux", "parent"), catalog.tables("aux"));
        assertEquals(List.of("id", "auxcol"), catalog.columns("aux", "parent").stream().map(ColumnInfo::getName).toList());
        assertTrue(catalog.createSql("aux", "parent").orElseThrow().contains("auxcol"));
        assertTrue(catalog.createSql("main", "parent").orElseThrow().contains("email"));
        assertTrue(catalog.createSql("aux", "missing").isEmpty());
    }

    @Test
    void columnDetails() throws SQLException {
        assertEquals("VARCHAR(50)", column("main", "parent", "name").getType());
        assertTrue(column("main", "parent", "name").isNotNull());
        assertEquals("'x'", column("main", "parent", "name").getDefaultValue());
        assertEquals("DECIMAL(10,2)", column("main", "parent", "price").getType());
        assertEquals(1, column("main", "parent", "id").getPrimaryKeyPosition());
        assertTrue(column("main", "parent", "total").isGenerated());
        assertFalse(column("main", "parent", "email").isGenerated());
    }

    @Test
    void compositePrimaryKeyKeepsItsOrder() throws SQLException {
        assertEquals(List.of("a", "b"), catalog.primaryKeyColumns("main", "p2"));
    }

    @Test
    void uniqueIsReadFromIndexesNotFromTheCreateText() throws SQLException {
        IndexInfo emailUnique = catalog.indexes("main", "parent").stream()
                .filter(i -> i.getColumns().equals(List.of("email"))).findFirst().orElseThrow();
        assertTrue(emailUnique.isUnique());
        assertEquals("u", emailUnique.getOrigin());

        IndexInfo composite = catalog.indexes("main", "child").stream()
                .filter(i -> i.getColumns().equals(List.of("mail", "a"))).findFirst().orElseThrow();
        assertTrue(composite.isUnique());
        assertTrue(catalog.indexes("main", "child").stream().anyMatch(i -> i.getName().equals("idx_child_pid") && !i.isUnique()));
    }

    @Test
    void everyForeignKeyIsKeptIncludingUnnamedAndCompositeOnes() throws SQLException {
        List<ForeignKeyInfo> fks = catalog.foreignKeys("main", "child");

        assertEquals(3, fks.size());
        assertEquals(3, fks.stream().map(ForeignKeyInfo::getName).distinct().count(), "names must be distinct");
        ForeignKeyInfo cascade = fks.stream().filter(f -> f.getColumns().equals(List.of("pid"))).findFirst().orElseThrow();
        assertEquals("CASCADE", cascade.getOnDelete());
        assertEquals("NO ACTION", cascade.getOnUpdate());
        ForeignKeyInfo composite = fks.stream().filter(f -> f.getColumns().size() == 2).findFirst().orElseThrow();
        assertEquals(List.of("a", "b"), composite.getColumns());
        assertEquals("p2", composite.getReferenceTable());
        assertEquals(List.of("a", "b"), composite.getReferenceColumns());
    }

    @Test
    void referenceWithoutColumnListResolvesToThePrimaryKey() throws SQLException {
        ForeignKeyInfo fk = catalog.foreignKeys("main", "implicit").get(0);
        assertEquals(List.of("id"), fk.getReferenceColumns());
    }

    @Test
    void incomingForeignKeys() throws SQLException {
        assertEquals(List.of("child", "child", "implicit"),
                catalog.referencing("main", "parent").stream().map(ForeignKeyInfo::getTable).toList());
        assertTrue(catalog.referencing("main", "child").isEmpty());
    }

    @Test
    void autoincrementOnlyOnTheSingleIntegerKeyThatDeclaresIt() throws SQLException {
        assertTrue(catalog.isAutoincrement("main", "parent"));
        assertFalse(catalog.isAutoincrement("main", "child"), "a plain INTEGER PRIMARY KEY is not AUTOINCREMENT");
        assertFalse(catalog.isAutoincrement("main", "p2"), "composite keys cannot be AUTOINCREMENT");
    }

    @Test
    void indexesAndTriggersBelongingToATableAreListed() throws SQLException {
        List<String> related = catalog.relatedSql("main", "child");
        assertEquals(1, related.size());
        assertTrue(related.get(0).startsWith("CREATE INDEX idx_child_pid"));
        assertTrue(catalog.relatedSql("main", "parent").get(0).startsWith("CREATE TRIGGER trg_parent"));
    }

    @Test
    void sizeIsMeasuredPerTable() throws SQLException {
        execute("CREATE TABLE big(id INTEGER PRIMARY KEY, v TEXT)",
                "WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 20000) INSERT INTO big SELECT i, hex(randomblob(50)) FROM n");

        double big = catalog.sizeMegabytes("main", "big");
        double small = catalog.sizeMegabytes("main", "p2");

        assertTrue(big > 1.0, "20000 rows of ~100 bytes should be a couple of MB, got " + big);
        assertTrue(small < big);
    }
}
