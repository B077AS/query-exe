package io.github.b077as.queryexe.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.b077as.queryexe.engine.Dialect;
import io.github.b077as.queryexe.engine.h2.H2Dialect;
import io.github.b077as.queryexe.engine.mariadb.MariaDbDialect;
import io.github.b077as.queryexe.engine.mysql.MySqlDialect;
import io.github.b077as.queryexe.engine.postgres.PostgresDialect;
import io.github.b077as.queryexe.engine.sqlite.SqliteDialect;
import io.github.b077as.queryexe.engine.sqlserver.SqlServerDialect;

class SqlSyntaxTest {

    @Test
    void everyEngineExposesItsOwnDialect() {
        assertSame(SqlSyntax.MYSQL, new MySqlDialect().getSqlSyntax());
        assertSame(SqlSyntax.MYSQL, new MariaDbDialect().getSqlSyntax());
        assertSame(SqlSyntax.POSTGRES, new PostgresDialect().getSqlSyntax());
        assertSame(SqlSyntax.SQLSERVER, new SqlServerDialect().getSqlSyntax());
        assertSame(SqlSyntax.H2, new H2Dialect().getSqlSyntax());
        assertSame(SqlSyntax.SQLITE, new SqliteDialect().getSqlSyntax());
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
        assertEquals("\"a\"\"b\"", new H2Dialect().quoteIdentifier("a\"b"));
        assertEquals("`a``b`", new MySqlDialect().quoteIdentifier("a`b"));
        assertEquals("[a]]b]", new SqlServerDialect().quoteIdentifier("a]b"));
    }

    @Test
    void tableQualificationPerEngine() {
        // MySQL/MariaDB: the "database" is the catalog; schema-style engines use the schema
        assertEquals("`shop`.`orders`", new MySqlDialect().qualifyTable("shop", "", "orders"));
        assertEquals("\"OTHER\".\"DUP\"", new H2Dialect().qualifyTable("DB", "OTHER", "DUP"));
        assertEquals("\"DUP\"", new H2Dialect().qualifyTable("DB", "", "DUP"));
    }
}
