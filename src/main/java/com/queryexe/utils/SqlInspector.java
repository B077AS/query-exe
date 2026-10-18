package com.queryexe.utils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.queryexe.model.connections.ConnectionTypes;

import lombok.experimental.UtilityClass;

/**
 * Looks at a single, already split statement to tell what it did to the session or the schema, so the UI can
 * follow along (selected database in the tree, structure refresh). Pure text analysis, no database access.
 */
@UtilityClass
public class SqlInspector {

    private static final Pattern LEADING_TRIVIA = Pattern.compile("\\A(?:\\s+|--[^\\n]*(?:\\n|\\z)|/\\*.*?\\*/)*", Pattern.DOTALL);

    private static final String NAME = "(`[^`]+`|\\[[^\\]]+\\]|\"[^\"]+\"|'[^']+'|[^\\s;,]+)";

    private static final Pattern USE = Pattern.compile("^USE\\s+" + NAME + "\\s*;?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern H2_SET_SCHEMA = Pattern.compile("^SET\\s+SCHEMA\\s+" + NAME + "\\s*;?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PG_SEARCH_PATH = Pattern.compile(
            "^SET\\s+(?:SESSION\\s+|LOCAL\\s+)?SEARCH_PATH\\s*(?:TO|=)\\s*(.+?)\\s*;?\\s*$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PG_SET_SCHEMA = Pattern.compile("^SET\\s+(?:SESSION\\s+|LOCAL\\s+)?SCHEMA\\s+" + NAME + "\\s*;?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DDL = Pattern.compile("^(CREATE|DROP|ALTER|RENAME)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern STRUCTURAL = Pattern.compile(
            "^(?:CREATE|DROP)\\s+(?:OR\\s+REPLACE\\s+)?(?:DATABASE|SCHEMA)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * The database/schema a successfully run statement made current for the session, if it was such a command.
     *
     * @param dbType one of {@link ConnectionTypes}'s names, as stored in {@code ConnectionObject.getDbType()}
     */
    public static Optional<String> detectSchemaSwitch(String sql, String dbType) {
        String statement = stripLeadingTrivia(sql);

        if (dbType.equals(ConnectionTypes.PostgreSQL.toString())) {
            Matcher searchPath = PG_SEARCH_PATH.matcher(statement);
            if (searchPath.matches()) {
                return firstSearchPathSchema(searchPath.group(1));
            }
            Matcher setSchema = PG_SET_SCHEMA.matcher(statement);
            return setSchema.matches() ? Optional.of(unquote(setSchema.group(1))) : Optional.empty();
        }

        if (dbType.equals(ConnectionTypes.H2.toString())) {
            Matcher setSchema = H2_SET_SCHEMA.matcher(statement);
            return setSchema.matches() ? Optional.of(unquote(setSchema.group(1))) : Optional.empty();
        }

        if (dbType.equals(ConnectionTypes.SQLite.toString())) {
            return Optional.empty();
        }

        Matcher use = USE.matcher(statement);
        return use.matches() ? Optional.of(unquote(use.group(1))) : Optional.empty();
    }

    /**
     * The schema that name resolution will try first for a PostgreSQL {@code search_path} value such as
     * {@code "$user", public}: the first entry that isn't the {@code $user} placeholder.
     */
    public static Optional<String> firstSearchPathSchema(String searchPath) {
        if (searchPath == null) {
            return Optional.empty();
        }
        for (String entry : searchPath.split(",")) {
            String name = unquote(entry.trim());
            if (!name.isEmpty() && !name.startsWith("$")) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    /** CREATE / DROP / ALTER / RENAME: the schema's table list may have changed. */
    public static boolean isDdl(String sql) {
        return DDL.matcher(stripLeadingTrivia(sql)).find();
    }

    /** CREATE / DROP of a whole database or schema: the tree's top level changed. */
    public static boolean changesDatabaseList(String sql) {
        return STRUCTURAL.matcher(stripLeadingTrivia(sql)).find();
    }

    static String stripLeadingTrivia(String sql) {
        if (sql == null) {
            return "";
        }
        Matcher trivia = LEADING_TRIVIA.matcher(sql);
        return (trivia.find() ? sql.substring(trivia.end()) : sql).strip();
    }

    private static String unquote(String name) {
        String trimmed = name.strip();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            boolean quoted = (first == '`' && last == '`') || (first == '"' && last == '"')
                    || (first == '\'' && last == '\'') || (first == '[' && last == ']');
            if (quoted) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }
        return trimmed;
    }

}
