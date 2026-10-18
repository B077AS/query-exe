package com.queryexe.model.connections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.queryexe.model.data.ColumnData;
import com.queryexe.model.data.DetailedColumnData;
import com.queryexe.model.data.ForeignKeyData;
import com.queryexe.service.DatabaseConnection;

class SQLiteConnectionTest {

    private SQLiteConnection sqlite;
    private Connection connection;

    @BeforeEach
    void fixture(@TempDir Path dir) throws SQLException {
        sqlite = new SQLiteConnection("sqlite-conn-test", "lite", "SQLite",
                "jdbc:sqlite:" + dir.resolve("main.db").toString().replace("\\", "/"), "", "", null);
        DatabaseConnection.getInstance().initialize(sqlite);
        connection = DatabaseConnection.getInstance().getConnection();
        execute("ATTACH DATABASE '" + dir.resolve("aux.db").toString().replace("\\", "/") + "' AS aux",
                "CREATE TABLE parent(id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT UNIQUE, name VARCHAR(50) NOT NULL DEFAULT 'x', "
                        + "price DECIMAL(10,2), total INTEGER GENERATED ALWAYS AS (id * 2) VIRTUAL)",
                "CREATE TABLE p2(a INTEGER, b INTEGER, PRIMARY KEY(a,b))",
                "CREATE TABLE child(id INTEGER PRIMARY KEY, pid INTEGER REFERENCES parent(id) ON DELETE CASCADE, "
                        + "qid INTEGER REFERENCES parent(id), a INTEGER, b INTEGER, mail TEXT, "
                        + "FOREIGN KEY(a,b) REFERENCES p2(a,b), UNIQUE(mail, a))",
                "CREATE TABLE \"my table\"(\"Id\" INTEGER PRIMARY KEY, \"weird col\" TEXT)",
                "CREATE TABLE \"order\"(id INTEGER PRIMARY KEY, \"group\" TEXT)",
                "CREATE TABLE aux.parent(id INTEGER PRIMARY KEY, auxcol TEXT NOT NULL)",
                "CREATE TABLE aux.only_aux(id INTEGER PRIMARY KEY, z TEXT)",
                "CREATE INDEX idx_child_pid ON child(pid)");
    }

    @AfterEach
    void disconnect() {
        DatabaseConnection.getInstance().close("sqlite-conn-test");
    }

    private void execute(String... statements) throws SQLException {
        try (Statement s = connection.createStatement()) {
            for (String statement : statements) {
                s.execute(statement);
            }
        }
    }

    private static ColumnData column(List<ColumnData> columns, String name) {
        return columns.stream().filter(c -> c.getColumnName().equals(name)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------------------------ structure

    @Test
    void awkwardTableNamesNoLongerBreakTheWholeSchema() {
        Map<String, ArrayList<ColumnData>> all = sqlite.getAllTablesAndColumns("main");

        assertNotNull(all, "one table called \"my table\" used to make the whole schema fail to load");
        assertEquals(List.of("child", "my table", "order", "p2", "parent"), all.keySet().stream().toList());
    }

    @Test
    void columnFlags() throws SQLException {
        List<ColumnData> parent = sqlite.getColumnsForTable("main", "parent");

        assertTrue(column(parent, "id").isPrimaryKey());
        assertTrue(column(parent, "id").isAutoIncrement());
        assertTrue(column(parent, "email").isUnique(), "\"email TEXT UNIQUE\" is a unique column");
        assertEquals("VARCHAR(50)", column(parent, "name").getDataType());
        assertTrue(column(parent, "name").isNotNull());

        List<ColumnData> child = sqlite.getColumnsForTable("main", "child");
        assertFalse(column(child, "id").isAutoIncrement(), "no AUTOINCREMENT keyword on child.id");
        assertFalse(column(child, "mail").isUnique(), "mail is only unique together with a");
        List<ColumnData> p2 = sqlite.getColumnsForTable("main", "p2");
        assertTrue(column(p2, "a").isPrimaryKey() && column(p2, "b").isPrimaryKey());
        assertFalse(column(p2, "a").isAutoIncrement());
    }

    @Test
    void everyForeignKeyIsReturned() throws SQLException {
        Map<String, ForeignKeyData> fks = sqlite.extractForeignKeys("main", "child");

        assertEquals(3, fks.size());
        ForeignKeyData composite = fks.values().stream().filter(f -> f.allLocalColumns().size() == 2).findFirst().orElseThrow();
        assertEquals(List.of("a", "b"), composite.allLocalColumns());
        assertEquals("p2", composite.getReferenceTable());
    }

    @Test
    void columnDetailsWorkForEveryTableIncludingIncomingReferences() throws SQLException {
        for (String table : sqlite.getTablesForDatabase("main")) {
            for (ColumnData column : sqlite.getColumnsForTable("main", table)) {
                sqlite.getDetailedColumnInfo("main", table, column.getColumnName()); // used to throw for all of them
            }
        }

        DetailedColumnData id = sqlite.getDetailedColumnInfo("main", "parent", "id");
        assertTrue(id.isPrimaryKey());
        assertTrue(id.isAutoIncrement());
        assertEquals(2, id.getReferencedByForeignKeys().size());
        DetailedColumnData pid = sqlite.getDetailedColumnInfo("main", "child", "pid");
        assertEquals("parent", pid.getForeignKeyReferences().get(0).get("REFERENCED_TABLE_NAME"));
        assertEquals("CASCADE", pid.getForeignKeyReferences().get(0).get("DELETE_RULE"));
        assertTrue(sqlite.getDetailedColumnInfo("main", "parent", "email").isUnique());
        assertThrows(SQLException.class, () -> sqlite.getDetailedColumnInfo("main", "parent", "nope"));
    }

    @Test
    void attachedDatabasesAreListedAndKeptApart() throws SQLException {
        assertEquals(List.of("main", "aux"), sqlite.getDatabases(null));
        assertEquals(List.of("only_aux", "parent"), sqlite.getTablesForDatabase("aux"));
        assertEquals(List.of("id", "auxcol"), sqlite.getColumnsForTable("aux", "parent").stream().map(ColumnData::getColumnName).toList());
    }

    // ------------------------------------------------------------------------------------ scripts

    @Test
    void createScriptComesFromTheRightDatabaseAndIncludesIndexes() {
        assertTrue(sqlite.generateCreateScript("parent", "main").contains("email TEXT UNIQUE"));
        String aux = sqlite.generateCreateScript("parent", "aux");
        assertTrue(aux.contains("auxcol") && !aux.contains("email"), aux);
        assertTrue(sqlite.generateCreateScript("only_aux", "aux").contains("CREATE TABLE only_aux"));
        assertTrue(sqlite.generateCreateScript("child", "main").contains("CREATE INDEX idx_child_pid"));
        assertTrue(sqlite.generateCreateScript("nope", "main").startsWith("-- ERROR"));
    }

    @Test
    void insertScriptUsesTheRequestedDatabasesColumnsAndSkipsWhatCannotBeWritten() {
        String aux = sqlite.generateInsertScript("parent", "aux");
        assertTrue(aux.startsWith("INSERT INTO \"aux\".\"parent\""), aux);
        assertTrue(aux.contains("\"auxcol\"") && aux.contains("''"), aux);
        assertFalse(aux.contains("\"id\"") || aux.contains("email"), aux);

        String main = sqlite.generateInsertScript("parent", "main");
        assertFalse(main.contains("\"id\"") || main.contains("total"), "rowid alias and generated column must be skipped: " + main);
        assertTrue(main.contains("\"name\""), main);
    }

    @Test
    void selectScriptIsQualified() {
        assertEquals("SELECT * FROM \"aux\".\"parent\" LIMIT 10;", sqlite.generateSelectScript("parent", "aux", 10));
        assertEquals("SELECT * FROM \"my table\" LIMIT 10;", sqlite.generateSelectScript("my table", null, 10));
    }

    @Test
    void generatedCreateTableIsValidAndReplaysTheColumns() throws SQLException {
        List<ColumnData> parent = sqlite.getColumnsForTable("main", "parent");
        parent.removeIf(c -> c.getColumnName().equals("total")); // generated columns are not part of the editor's model

        String sql = sqlite.generateCreateTableSQL("main", "parent_copy", parent, List.of());
        assertTrue(sql.contains("\"id\" INTEGER PRIMARY KEY AUTOINCREMENT"), sql);
        execute(sql); // "INTEGER AUTOINCREMENT NOT NULL, PRIMARY KEY (...)" used to be a syntax error

        List<ColumnData> copy = sqlite.getColumnsForTable("main", "parent_copy");
        assertEquals(parent.stream().map(ColumnData::getColumnName).toList(), copy.stream().map(ColumnData::getColumnName).toList());
        assertTrue(column(copy, "id").isAutoIncrement());
        assertTrue(column(copy, "email").isUnique());
    }

    @Test
    void createTableWithCompositeKeysAndForeignKeys() throws SQLException {
        ForeignKeyData fk = new ForeignKeyData("fk_x", "a", "p2", "a", "CASCADE", "NO ACTION");
        fk.setLocalColumns(List.of("a", "b"));
        fk.setReferenceColumns(List.of("a", "b"));
        List<ColumnData> columns = List.of(
                new ColumnData("x", "INTEGER", true, true, false, false),
                new ColumnData("a", "INTEGER", false, false, false, false),
                new ColumnData("b", "INTEGER", false, false, false, false));

        execute(sqlite.generateCreateTableSQL("main", "t_fk", columns, List.of(fk)));

        assertEquals(1, sqlite.extractForeignKeys("main", "t_fk").size());
        assertEquals(List.of("a", "b"), sqlite.extractForeignKeys("main", "t_fk").values().iterator().next().allLocalColumns());
    }

    // ------------------------------------------------------------------------------------ alter

    private String alter(List<ColumnData> oldColumns, List<ColumnData> newColumns, String newName, Map<String, String> renames,
                         Map<String, ForeignKeyData> oldFks, List<ForeignKeyData> newFks) throws SQLException {
        return sqlite.generateAlterTableSQL("main", "parent", newName, oldColumns, newColumns, oldFks, newFks, renames);
    }

    @Test
    void renameAddAndDropAreExecutableInPlace() throws SQLException {
        List<ColumnData> before = sqlite.getColumnsForTable("main", "parent");
        List<ColumnData> after = new ArrayList<>(sqlite.getColumnsForTable("main", "parent"));
        after.removeIf(c -> c.getColumnName().equals("total") || c.getColumnName().equals("price"));
        column(after, "name").setColumnName("title");
        after.add(new ColumnData("note", "TEXT", false, false, false, false));

        String sql = alter(before, after, "parent2", Map.of("name", "title"), new HashMap<>(), new ArrayList<>());
        execute(sql.split(";\\s*\n?"));

        assertEquals(List.of("id", "email", "title", "note"),
                sqlite.getColumnsForTable("main", "parent2").stream().map(ColumnData::getColumnName).toList());
    }

    @Test
    void noChangeMeansNoStatements() throws SQLException {
        List<ColumnData> columns = sqlite.getColumnsForTable("main", "p2");
        assertEquals("", sqlite.generateAlterTableSQL("main", "p2", "p2", columns, sqlite.getColumnsForTable("main", "p2"),
                new HashMap<>(), new ArrayList<>(), new HashMap<>()));
    }

    @Test
    void changesThatNeedATableRebuildAreRefusedInsteadOfIgnored() throws SQLException {
        List<ColumnData> before = sqlite.getColumnsForTable("main", "p2");

        List<ColumnData> typeChange = sqlite.getColumnsForTable("main", "p2");
        column(typeChange, "a").setDataType("TEXT");
        assertThrows(SQLFeatureNotSupportedException.class,
                () -> sqlite.generateAlterTableSQL("main", "p2", "p2", before, typeChange, new HashMap<>(), new ArrayList<>(), new HashMap<>()));

        List<ColumnData> notNullColumn = sqlite.getColumnsForTable("main", "p2");
        notNullColumn.add(new ColumnData("c", "TEXT", false, true, false, false));
        assertThrows(SQLFeatureNotSupportedException.class,
                () -> sqlite.generateAlterTableSQL("main", "p2", "p2", before, notNullColumn, new HashMap<>(), new ArrayList<>(), new HashMap<>()));

        ForeignKeyData newFk = new ForeignKeyData("fk_new", "a", "parent", "id", "NO ACTION", "NO ACTION");
        assertThrows(SQLFeatureNotSupportedException.class,
                () -> sqlite.generateAlterTableSQL("main", "p2", "p2", before, sqlite.getColumnsForTable("main", "p2"),
                        new HashMap<>(), List.of(newFk), new HashMap<>()));
    }

    // ------------------------------------------------------------------------------------ misc

    @Test
    void tableSizeIsMeasured() throws SQLException {
        assertTrue(sqlite.getTableSize("main", "parent") >= 0);
    }

    @Test
    void databasesCannotBeCreatedOrDroppedByName() {
        assertThrows(SQLFeatureNotSupportedException.class, () -> sqlite.createDatabase("x"));
        assertThrows(SQLFeatureNotSupportedException.class, () -> sqlite.deleteDatabase("x"));
    }

    @Test
    void dropTableHonoursTheDatabase() throws SQLException {
        sqlite.deleteTable("aux", "only_aux");
        assertEquals(List.of("parent"), sqlite.getTablesForDatabase("aux"));
        assertTrue(sqlite.getTablesForDatabase("main").contains("parent"), "main.parent must be untouched");
    }
}
