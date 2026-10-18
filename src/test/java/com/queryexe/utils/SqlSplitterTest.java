package com.queryexe.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.queryexe.model.connections.SqlSyntax;

class SqlSplitterTest {

    private static List<String> split(String sql, SqlSyntax syntax) {
        return SqlSplitter.texts(SqlSplitter.split(sql, syntax));
    }

    // ---------------------------------------------------------------------------------- basics

    @Test
    void splitsOnTopLevelSemicolons() {
        assertEquals(List.of("SELECT 1", "SELECT 2"), split("SELECT 1; SELECT 2;", SqlSyntax.ANSI));
    }

    @Test
    void lastStatementNeedsNoTerminator() {
        assertEquals(List.of("SELECT 1", "SELECT 2"), split("SELECT 1; SELECT 2", SqlSyntax.ANSI));
    }

    @Test
    void emptyAndBlankInputGiveNothing() {
        assertEquals(List.of(), split("", SqlSyntax.ANSI));
        assertEquals(List.of(), split("  \n ;; ; \n", SqlSyntax.ANSI));
        assertEquals(List.of(), split(null, SqlSyntax.ANSI));
    }

    @Test
    void trailingCommentIsNotAStatement() {
        assertEquals(List.of("SELECT 1"), split("SELECT 1; -- done", SqlSyntax.ANSI));
        assertEquals(List.of("SELECT 1"), split("SELECT 1;\n-- trailing\n/* block */", SqlSyntax.ANSI));
    }

    @Test
    void leadingCommentStaysWithItsStatement() {
        assertEquals(List.of("-- find users\nSELECT 1"), split("-- find users\nSELECT 1;", SqlSyntax.ANSI));
    }

    @Test
    void semicolonsInsideStringsAndCommentsAreIgnored() {
        assertEquals(List.of("SELECT 'a;b'", "SELECT 2"), split("SELECT 'a;b'; SELECT 2", SqlSyntax.ANSI));
        assertEquals(List.of("SELECT \"a;b\"", "SELECT 2"), split("SELECT \"a;b\"; SELECT 2", SqlSyntax.ANSI));
        assertEquals(List.of("SELECT 1 /* a; b */", "SELECT 2"), split("SELECT 1 /* a; b */; SELECT 2", SqlSyntax.ANSI));
        assertEquals(List.of("SELECT 1 -- a; b\n, 2", "SELECT 3"), split("SELECT 1 -- a; b\n, 2; SELECT 3", SqlSyntax.ANSI));
    }

    @Test
    void doubledQuotesDoNotEndAString() {
        assertEquals(List.of("SELECT 'it''s; ok'", "SELECT 2"), split("SELECT 'it''s; ok'; SELECT 2", SqlSyntax.ANSI));
    }

    @Test
    void multilineStringLiteral() {
        assertEquals(List.of("SELECT '\n;\n'", "SELECT 2"), split("SELECT '\n;\n'; SELECT 2", SqlSyntax.ANSI));
    }

    @Test
    void unterminatedStringSwallowsTheRest() {
        assertEquals(List.of("SELECT 'oops; SELECT 2"), split("SELECT 'oops; SELECT 2", SqlSyntax.ANSI));
    }

    // ---------------------------------------------------------------------------------- MySQL / MariaDB

    @Test
    void mysqlBackslashEscapes() {
        assertEquals(List.of("SELECT 'it\\'s; x'", "SELECT 2"), split("SELECT 'it\\'s; x'; SELECT 2", SqlSyntax.MYSQL));
        // without backslash escapes the string already ends at the escaped quote, so the split differs
        assertEquals(2, split("SELECT 'it\\'s; x'; SELECT 2", SqlSyntax.ANSI).size());
    }

    @Test
    void mysqlBacktickIdentifiers() {
        assertEquals(List.of("SELECT `a;b` FROM t", "SELECT 2"), split("SELECT `a;b` FROM t; SELECT 2", SqlSyntax.MYSQL));
        assertEquals(List.of("SELECT `we``ird;` FROM t"), split("SELECT `we``ird;` FROM t;", SqlSyntax.MYSQL));
    }

    @Test
    void mysqlHashComment() {
        assertEquals(List.of("SELECT 1", "# note; with semi\nSELECT 2"),
                split("SELECT 1; # note; with semi\nSELECT 2", SqlSyntax.MYSQL));
    }

    @Test
    void mysqlDashDashNeedsWhitespace() {
        // "--1" is not a comment in MySQL (5--1 is 6), but it is one in standard SQL
        assertEquals(List.of("SELECT 5--1", "SELECT 2"), split("SELECT 5--1; SELECT 2", SqlSyntax.MYSQL));
        assertEquals(List.of("SELECT 5--1; SELECT 2"), split("SELECT 5--1; SELECT 2", SqlSyntax.ANSI));
        assertEquals(List.of("SELECT 5 -- c; d"), split("SELECT 5 -- c; d\n", SqlSyntax.MYSQL));
    }

    @Test
    void mysqlExecutableCommentsAreCode() {
        assertEquals(List.of("/*!40101 SET NAMES utf8 */", "SELECT 1"),
                split("/*!40101 SET NAMES utf8 */;\nSELECT 1;", SqlSyntax.MYSQL));
        // a plain comment on its own is not
        assertEquals(List.of("SELECT 1"), split("/* hi */;\nSELECT 1;", SqlSyntax.MYSQL));
    }

    @Test
    void mysqlProcedureBodyIsOneStatement() {
        String sql = "CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END; SELECT 3";
        assertEquals(List.of("CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END", "SELECT 3"), split(sql, SqlSyntax.MYSQL));
    }

    @Test
    void mysqlNestedBlocksAndControlFlow() {
        String proc = "CREATE DEFINER=`root`@`localhost` PROCEDURE p(IN x INT)\n"
                + "BEGIN\n"
                + "  DECLARE done INT DEFAULT 0;\n"
                + "  IF x > 1 THEN\n"
                + "    SELECT CASE WHEN x = 2 THEN 'a' ELSE 'b' END;\n"
                + "  END IF;\n"
                + "  WHILE done < 3 DO\n"
                + "    SET done = done + 1;\n"
                + "  END WHILE;\n"
                + "  CASE x WHEN 1 THEN SELECT 1; ELSE BEGIN SELECT 2; END; END CASE;\n"
                + "  lbl: LOOP LEAVE lbl; END LOOP lbl;\n"
                + "END";
        List<String> result = split(proc + ";\nSELECT 99;", SqlSyntax.MYSQL);
        assertEquals(List.of(proc, "SELECT 99"), result);
    }

    @Test
    void mysqlTriggerWithAndWithoutBegin() {
        String t1 = "CREATE TRIGGER t1 BEFORE INSERT ON a FOR EACH ROW SET NEW.x = CASE WHEN NEW.y THEN 1 ELSE 2 END";
        String t2 = "CREATE TRIGGER t2 BEFORE INSERT ON a FOR EACH ROW BEGIN SET NEW.x = 1; SET NEW.z = 2; END";
        assertEquals(List.of(t1, t2, "SELECT 1"), split(t1 + ";\n" + t2 + ";\nSELECT 1;", SqlSyntax.MYSQL));
    }

    @Test
    void mysqlColumnNamedLikeKeywordsDoesNotOpenBlocks() {
        // qualified/escaped names are not keywords; and CREATE TABLE is not a routine at all
        assertEquals(List.of("CREATE TABLE trigger_log (`begin` INT, t.end INT)", "SELECT 1"),
                split("CREATE TABLE trigger_log (`begin` INT, t.end INT); SELECT 1;", SqlSyntax.MYSQL));
    }

    @Test
    void mysqlDelimiterCommand() {
        String sql = "DELIMITER $$\n"
                + "CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END$$\n"
                + "SELECT 5; SELECT 6$$\n"
                + "DELIMITER ;\n"
                + "SELECT 7;";
        assertEquals(List.of("CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END", "SELECT 5; SELECT 6", "SELECT 7"),
                split(sql, SqlSyntax.MYSQL));
    }

    @Test
    void mariadbAnonymousBlock() {
        assertEquals(List.of("BEGIN NOT ATOMIC SELECT 1; SELECT 2; END", "SELECT 3"),
                split("BEGIN NOT ATOMIC SELECT 1; SELECT 2; END; SELECT 3", SqlSyntax.MYSQL));
        // plain BEGIN is a transaction start
        assertEquals(List.of("BEGIN", "SELECT 1", "COMMIT"), split("BEGIN; SELECT 1; COMMIT;", SqlSyntax.MYSQL));
    }

    // ---------------------------------------------------------------------------------- PostgreSQL

    @Test
    void postgresDollarQuotedBodies() {
        String fn = "CREATE FUNCTION f() RETURNS int AS $$ BEGIN RETURN 1; END; $$ LANGUAGE plpgsql";
        assertEquals(List.of(fn, "SELECT 2"), split(fn + "; SELECT 2", SqlSyntax.POSTGRES));
        String tagged = "DO $body$ BEGIN PERFORM 1; PERFORM 2; END $body$";
        assertEquals(List.of(tagged), split(tagged + ";", SqlSyntax.POSTGRES));
    }

    @Test
    void dollarSignInsideIdentifierIsNotAQuote() {
        // used to swallow the rest of the script into one statement
        assertEquals(List.of("SELECT a$b$c FROM t", "SELECT 2", "SELECT 3"),
                split("SELECT a$b$c FROM t; SELECT 2; SELECT 3", SqlSyntax.POSTGRES));
        assertEquals(List.of("SELECT a$b$c FROM t", "SELECT 2"), split("SELECT a$b$c FROM t; SELECT 2", SqlSyntax.MYSQL));
    }

    @Test
    void positionalParameterIsNotADollarQuote() {
        assertEquals(List.of("SELECT $1", "SELECT $2"), split("SELECT $1; SELECT $2", SqlSyntax.POSTGRES));
    }

    @Test
    void postgresEscapeStringsOnly() {
        assertEquals(List.of("SELECT E'it\\'s;'", "SELECT 2"), split("SELECT E'it\\'s;'; SELECT 2", SqlSyntax.POSTGRES));
        // a plain string treats the backslash literally
        assertEquals(List.of("SELECT 'a\\'", "SELECT 'b'"), split("SELECT 'a\\'; SELECT 'b'", SqlSyntax.POSTGRES));
        // "e" at the end of an identifier is not an E-string prefix
        assertEquals(List.of("SELECT name'x\\'", "SELECT 2"), split("SELECT name'x\\'; SELECT 2", SqlSyntax.POSTGRES));
    }

    @Test
    void postgresNestedBlockComments() {
        assertEquals(List.of("SELECT 1 /* a /* b; */ c; */", "SELECT 2"),
                split("SELECT 1 /* a /* b; */ c; */; SELECT 2", SqlSyntax.POSTGRES));
        // MySQL comments do not nest
        assertEquals(3, split("SELECT 1 /* a /* b; */ c; */; SELECT 2", SqlSyntax.MYSQL).size());
    }

    @Test
    void postgresJsonbOperatorsStayInTheStatement() {
        assertEquals(List.of("SELECT * FROM t WHERE data ? 'k' AND data ?| array['a;']"),
                split("SELECT * FROM t WHERE data ? 'k' AND data ?| array['a;'];", SqlSyntax.POSTGRES));
    }

    // ---------------------------------------------------------------------------------- SQL Server

    @Test
    void sqlServerGoSeparator() {
        String sql = "SELECT 1\nGO\nSELECT 2\ngo\nSELECT 3";
        assertEquals(List.of("SELECT 1", "SELECT 2", "SELECT 3"), split(sql, SqlSyntax.SQLSERVER));
        assertEquals(List.of("SELECT 1", "SELECT 2"), split("SELECT 1\nGO 5\nSELECT 2\nGO -- end", SqlSyntax.SQLSERVER));
    }

    @Test
    void sqlServerGoIsNotMatchedInsideOtherTokens() {
        assertEquals(List.of("SELECT 1\nGOTO x", "SELECT 2"), split("SELECT 1\nGOTO x; SELECT 2", SqlSyntax.SQLSERVER));
        assertEquals(List.of("SELECT 'a\nGO\nb'"), split("SELECT 'a\nGO\nb'", SqlSyntax.SQLSERVER));
        // other engines have no GO
        assertEquals(List.of("SELECT 1\nGO\nSELECT 2"), split("SELECT 1\nGO\nSELECT 2", SqlSyntax.ANSI));
    }

    @Test
    void sqlServerBracketIdentifiers() {
        assertEquals(List.of("SELECT [a;b] FROM t", "SELECT 2"), split("SELECT [a;b] FROM t; SELECT 2", SqlSyntax.SQLSERVER));
        assertEquals(List.of("SELECT [we]]ird;] FROM t"), split("SELECT [we]]ird;] FROM t", SqlSyntax.SQLSERVER));
    }

    @Test
    void sqlServerProcedureOwnsTheBatchUntilGo() {
        String proc = "CREATE PROCEDURE p AS\nSELECT 1;\nSELECT 2;";
        assertEquals(List.of(proc, "SELECT 3"), split(proc + "\nGO\nSELECT 3", SqlSyntax.SQLSERVER));
        assertEquals(List.of("ALTER TABLE t ADD c INT", "SELECT 1"), split("ALTER TABLE t ADD c INT; SELECT 1", SqlSyntax.SQLSERVER));
    }

    // ---------------------------------------------------------------------------------- SQLite / H2

    @Test
    void sqliteTriggerBody() {
        String trigger = "CREATE TRIGGER tr AFTER INSERT ON t BEGIN UPDATE c SET n = n + 1; DELETE FROM d; END";
        assertEquals(List.of(trigger, "SELECT 1"), split(trigger + "; SELECT 1", SqlSyntax.SQLITE));
        assertEquals(List.of("CREATE TEMP TRIGGER IF NOT EXISTS tr AFTER INSERT ON t BEGIN SELECT 1; END"),
                split("CREATE TEMP TRIGGER IF NOT EXISTS tr AFTER INSERT ON t BEGIN SELECT 1; END;", SqlSyntax.SQLITE));
    }

    @Test
    void sqliteIdentifierQuotingStyles() {
        assertEquals(List.of("SELECT `a;b`, [c;d], \"e;f\" FROM t", "SELECT 2"),
                split("SELECT `a;b`, [c;d], \"e;f\" FROM t; SELECT 2", SqlSyntax.SQLITE));
    }

    @Test
    void h2DollarQuotedAlias() {
        String alias = "CREATE ALIAS HELLO AS $$ String hello() { return \"x;\"; } $$";
        assertEquals(List.of(alias, "SELECT 1"), split(alias + "; SELECT 1", SqlSyntax.H2));
    }

    // ---------------------------------------------------------------------------------- statement at caret

    @Test
    void statementAtCaret() {
        String sql = "SELECT 1;\nSELECT 'a;b';\n\nSELECT 3";
        assertEquals("SELECT 1", at(sql, 0));
        assertEquals("SELECT 1", at(sql, 5));
        assertEquals("SELECT 1", at(sql, 9));                       // right after the first ';'
        assertEquals("SELECT 'a;b'", at(sql, sql.indexOf("a;b") + 1)); // inside a string with ';'
        assertEquals("SELECT 3", at(sql, sql.length()));
    }

    @Test
    void caretInTrailingCommentSelectsLastStatement() {
        String sql = "SELECT 1; SELECT 2;\n-- end";
        assertEquals("SELECT 2", at(sql, sql.length()));
    }

    @Test
    void caretLookupUsesDelimiterContext() {
        String sql = "DELIMITER $$\nCREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END$$\nDELIMITER ;\nSELECT 3;";
        assertEquals("CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END", at(sql, sql.indexOf("SELECT 2")));
        assertEquals("SELECT 3", at(sql, sql.length() - 2));
    }

    @Test
    void noStatementsMeansNoMatch() {
        assertTrue(SqlSplitter.statementAt("  -- nothing\n", 3, SqlSyntax.ANSI).isEmpty());
    }

    private static String at(String sql, int caret) {
        return SqlSplitter.statementAt(sql, caret, SqlSyntax.MYSQL).orElseThrow().getText();
    }
}
