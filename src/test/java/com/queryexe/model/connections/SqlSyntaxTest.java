package com.queryexe.model.connections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SqlSyntaxTest {

    @Test
    void everyEngineExposesItsOwnDialect() {
        assertSame(SqlSyntax.MYSQL, new MySQLConnection("1", "n", "MySQL", "jdbc:x", "u", "p", null).getSqlSyntax());
        assertSame(SqlSyntax.MYSQL, new MariaDBConnection("1", "n", "MariaDB", "jdbc:x", "u", "p", null).getSqlSyntax());
        assertSame(SqlSyntax.POSTGRES, new PostgresConnection("1", "n", "PostgreSQL", "jdbc:x", "u", "p", null).getSqlSyntax());
        assertSame(SqlSyntax.SQLSERVER, new SQLServerConnection("1", "n", "SQLServer", "jdbc:x", "u", "p", null).getSqlSyntax());
        assertSame(SqlSyntax.H2, new H2Connection("1", "n", "H2", "jdbc:x", "u", "p", null).getSqlSyntax());
        assertSame(SqlSyntax.SQLITE, new SQLiteConnection("1", "n", "SQLite", "jdbc:x", "u", "p", null).getSqlSyntax());
    }

    @Test
    void ansiHasNoDialectExtensions() {
        SqlSyntax ansi = SqlSyntax.ANSI;
        assertFalse(ansi.isBackslashEscapes() || ansi.isBacktickIdentifiers() || ansi.isBracketIdentifiers()
                || ansi.isHashComments() || ansi.isDollarQuoting() || ansi.isDelimiterCommand() || ansi.isGoSeparator());
        assertTrue(ansi.getBeginEndObjects().isEmpty());
        assertTrue(ansi.getBatchScopedObjects().isEmpty());
    }

    @Test
    void toBuilderCopiesAndOverridesWithoutTouchingTheOriginal() {
        SqlSyntax custom = SqlSyntax.MYSQL.toBuilder().goSeparator(true).beginEndObjects(Set.of("PROCEDURE")).build();

        assertTrue(custom.isGoSeparator());
        assertTrue(custom.isBackslashEscapes());
        assertEquals(Set.of("PROCEDURE"), custom.getBeginEndObjects());
        assertFalse(SqlSyntax.MYSQL.isGoSeparator());
        assertTrue(SqlSyntax.MYSQL.getBeginEndObjects().contains("EVENT"));
    }

    @Test
    void quotingStylesPerEngine() {
        assertEquals("\"a\"\"b\"", new H2Connection("1", "n", "H2", "jdbc:x", "u", "p", null).quoteIdentifier("a\"b"));
        assertEquals("`a``b`", new MySQLConnection("1", "n", "MySQL", "jdbc:x", "u", "p", null).quoteIdentifier("a`b"));
        assertEquals("[a]]b]", new SQLServerConnection("1", "n", "SQLServer", "jdbc:x", "u", "p", null).quoteIdentifier("a]b"));
    }

    @Test
    void tableQualificationPerEngine() {
        // MySQL/MariaDB: the "database" is the catalog; schema-style engines use the schema
        assertEquals("`shop`.`orders`", new MySQLConnection("1", "n", "MySQL", "jdbc:x", "u", "p", null).qualifyTable("shop", "", "orders"));
        assertEquals("\"OTHER\".\"DUP\"", new H2Connection("1", "n", "H2", "jdbc:x", "u", "p", null).qualifyTable("DB", "OTHER", "DUP"));
        assertEquals("\"DUP\"", new H2Connection("1", "n", "H2", "jdbc:x", "u", "p", null).qualifyTable("DB", "", "DUP"));
    }
}
