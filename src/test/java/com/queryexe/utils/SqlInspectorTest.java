package com.queryexe.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class SqlInspectorTest {

    private static Optional<String> switchOf(String sql, String dbType) {
        return SqlInspector.detectSchemaSwitch(sql, dbType);
    }

    @Test
    void useInMysqlFamilyAndSqlServer() {
        assertEquals(Optional.of("shop"), switchOf("USE shop", "MySQL"));
        assertEquals(Optional.of("shop db"), switchOf("use `shop db`;", "MariaDB"));
        assertEquals(Optional.of("shop"), switchOf("USE [shop]", "SQLServer"));
        assertEquals(Optional.of("shop"), switchOf("-- switch\nUSE shop", "MySQL"));
        assertEquals(Optional.empty(), switchOf("USE shop; SELECT 1", "MySQL"));
        assertEquals(Optional.empty(), switchOf("SELECT 'USE x'", "MySQL"));
    }

    @Test
    void h2SetSchemaQuotedOrNot() {
        assertEquals(Optional.of("OTHER"), switchOf("SET SCHEMA OTHER", "H2"));
        assertEquals(Optional.of("MixedCase"), switchOf("set schema \"MixedCase\";", "H2"));
        assertEquals(Optional.empty(), switchOf("USE other", "H2"));
    }

    @Test
    void postgresSearchPathForms() {
        assertEquals(Optional.of("sales"), switchOf("SET search_path TO sales", "PostgreSQL"));
        assertEquals(Optional.of("sales"), switchOf("SET search_path = sales, public", "PostgreSQL"));
        assertEquals(Optional.of("sales"), switchOf("SET SESSION search_path TO \"$user\", \"sales\"", "PostgreSQL"));
        assertEquals(Optional.of("public"), switchOf("SET search_path TO public", "PostgreSQL"));
        assertEquals(Optional.of("sales"), switchOf("SET SCHEMA 'sales'", "PostgreSQL"));
        assertEquals(Optional.empty(), switchOf("SET search_path TO \"$user\"", "PostgreSQL"));
        assertEquals(Optional.empty(), switchOf("SET statement_timeout TO 5", "PostgreSQL"));
    }

    @Test
    void sqliteHasNoSchemaSwitch() {
        assertEquals(Optional.empty(), switchOf("USE x", "SQLite"));
    }

    @Test
    void ddlDetection() {
        assertTrue(SqlInspector.isDdl("CREATE TABLE t (a INT)"));
        assertTrue(SqlInspector.isDdl("  /* x */ drop table t"));
        assertTrue(SqlInspector.isDdl("-- c\nALTER TABLE t ADD c INT"));
        assertFalse(SqlInspector.isDdl("SELECT 'create table'"));
        assertFalse(SqlInspector.isDdl("INSERT INTO t VALUES (1)"));
        assertFalse(SqlInspector.isDdl(null));
    }

    @Test
    void databaseListChanges() {
        assertTrue(SqlInspector.changesDatabaseList("CREATE DATABASE x"));
        assertTrue(SqlInspector.changesDatabaseList("drop schema if exists x cascade"));
        assertFalse(SqlInspector.changesDatabaseList("CREATE TABLE schema_log (a INT)"));
        assertFalse(SqlInspector.changesDatabaseList("ALTER TABLE t ADD c INT"));
    }
}
