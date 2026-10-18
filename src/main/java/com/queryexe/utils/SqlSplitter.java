package com.queryexe.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.queryexe.model.connections.SqlSyntax;

import lombok.Builder;
import lombok.Value;

/**
 * Cuts a SQL script into executable statements according to an engine's {@link SqlSyntax}.
 * <p>
 * Terminators inside string literals, quoted identifiers, comments, dollar-quoted bodies and
 * {@code BEGIN ... END} bodies of stored routines/triggers are not statement boundaries. Chunks that
 * contain only comments/whitespace are dropped. Client-side commands ({@code DELIMITER}, {@code GO})
 * are consumed and never reach the server.
 */
public final class SqlSplitter {

    /** One executable statement of a script. */
    @Value
    @Builder
    public static class Statement {
        /** Trimmed statement text, without its terminator. */
        String text;
        /** Offset where the statement's region starts (right after the previous terminator). */
        int from;
        /** Offset just past the terminator (or the end of the script). */
        int to;
    }

    private enum Kind {UNDECIDED, NORMAL, BEGIN_END, BATCH}

    // Object words that end the search for a routine/trigger keyword in "CREATE ...".
    private static final Set<String> NON_ROUTINE_OBJECTS = Set.of(
            "TABLE", "INDEX", "VIEW", "DATABASE", "SCHEMA", "SEQUENCE", "USER", "ROLE", "TYPE", "DOMAIN",
            "EXTENSION", "SERVER", "RULE", "POLICY", "TABLESPACE", "COLLATION", "VIRTUAL", "LOGIN", "STATISTICS");

    private static final int MAX_CLASSIFY_WORDS = 10;

    private final String sql;
    private final SqlSyntax syntax;
    private final int length;

    private int pos = 0;
    private String delimiter = ";";

    private int from = 0;
    private boolean hasCode = false;
    private Kind kind = Kind.UNDECIDED;
    private final List<String> words = new ArrayList<>();
    // 'B' = BEGIN, 'C' = CASE; END closes the top one.
    private final Deque<Character> blocks = new ArrayDeque<>();
    private final List<Statement> statements = new ArrayList<>();
    // The identifier run scanned just before the current one ("?" when it was not a plain keyword).
    private String previousWord = "";

    private SqlSplitter(String sql, SqlSyntax syntax) {
        this.sql = sql;
        this.syntax = syntax;
        this.length = sql.length();
    }

    public static List<Statement> split(String sql, SqlSyntax syntax) {
        if (sql == null || sql.isEmpty()) {
            return List.of();
        }
        return new SqlSplitter(sql, syntax == null ? SqlSyntax.ANSI : syntax).scan();
    }

    /**
     * The statement the caret is in. A caret directly after a terminator still belongs to the statement
     * before it; a caret past the last statement (trailing whitespace/comments) selects the last one.
     */
    public static Optional<Statement> statementAt(String sql, int caret, SqlSyntax syntax) {
        List<Statement> all = split(sql, syntax);
        for (Statement statement : all) {
            if (caret <= statement.getTo()) {
                return Optional.of(statement);
            }
        }
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(all.size() - 1));
    }

    public static List<String> texts(List<Statement> statements) {
        return statements.stream().map(Statement::getText).toList();
    }

    // ------------------------------------------------------------------------------------------------

    private List<Statement> scan() {
        while (pos < length) {
            char c = sql.charAt(pos);

            if (Character.isWhitespace(c)) {
                pos++;
            } else if (!hasCode && syntax.isDelimiterCommand() && atLineStart() && tryDelimiterCommand()) {
                // consumed
            } else if (syntax.isGoSeparator() && atLineStart() && tryGo()) {
                // consumed
            } else if (startsLineComment()) {
                pos = endOfLine(pos);
            } else if (c == '/' && peek(1) == '*') {
                scanBlockComment();
            } else if (c == '\'' || c == '"') {
                hasCode = true;
                pos = skipQuoted(pos, c, usesBackslashEscape(c));
            } else if (c == '`' && syntax.isBacktickIdentifiers()) {
                hasCode = true;
                pos = skipQuoted(pos, '`', false);
            } else if (c == '[' && syntax.isBracketIdentifiers()) {
                hasCode = true;
                pos = skipBracketed(pos);
            } else if (c == '$' && syntax.isDollarQuoting() && !isIdentChar(charBefore(pos)) && tryDollarQuote()) {
                hasCode = true;
            } else if (sql.startsWith(delimiter, pos)) {
                if (isTerminatorAllowed()) {
                    endStatement(pos, pos + delimiter.length());
                } else {
                    hasCode = true;
                    pos += delimiter.length();
                }
            } else if (isIdentChar(c)) {
                scanWord();
            } else {
                hasCode = true;
                pos++;
            }
        }

        if (hasCode) {
            statements.add(Statement.builder().text(sql.substring(from, length).trim()).from(from).to(length).build());
        }
        return statements;
    }

    private void endStatement(int terminatorStart, int terminatorEnd) {
        if (hasCode) {
            statements.add(Statement.builder().text(sql.substring(from, terminatorStart).trim()).from(from).to(terminatorEnd).build());
        }
        from = terminatorEnd;
        pos = terminatorEnd;
        hasCode = false;
        kind = Kind.UNDECIDED;
        words.clear();
        previousWord = "";
        blocks.clear();
    }

    private boolean isTerminatorAllowed() {
        return kind != Kind.BATCH && !(kind == Kind.BEGIN_END && !blocks.isEmpty());
    }

    // --- client-side commands -------------------------------------------------------------------------

    private boolean tryDelimiterCommand() {
        if (!sql.regionMatches(true, pos, "DELIMITER", 0, 9)) {
            return false;
        }
        int after = pos + 9;
        if (after >= length || (sql.charAt(after) != ' ' && sql.charAt(after) != '\t')) {
            return false;
        }
        int eol = endOfLine(after);
        String token = sql.substring(after, eol).trim();
        if (token.isEmpty()) {
            return false;
        }
        delimiter = token;
        from = eol;
        pos = eol;
        return true;
    }

    // A line holding only GO (optionally followed by a repeat count) ends the current batch.
    private boolean tryGo() {
        if (!sql.regionMatches(true, pos, "GO", 0, 2)) {
            return false;
        }
        int eol = endOfLine(pos);
        // "GO", optionally followed by a repeat count and/or a trailing comment; "GOTO x" or "go_on" are not separators
        if (!sql.substring(pos + 2, eol).matches("(\\s+\\d+)?\\s*(--.*)?")) {
            return false;
        }
        endStatement(pos, eol);
        return true;
    }

    // --- tokens ---------------------------------------------------------------------------------------

    private boolean startsLineComment() {
        char c = sql.charAt(pos);
        if (c == '-' && peek(1) == '-') {
            if (!syntax.isDashDashNeedsSpace()) {
                return true;
            }
            int after = pos + 2;
            return after >= length || Character.isWhitespace(sql.charAt(after)) || Character.isISOControl(sql.charAt(after));
        }
        return c == '#' && syntax.isHashComments();
    }

    private void scanBlockComment() {
        // MySQL/MariaDB run the content of /*! ... */ and /*M! ... */, so it is code, not a comment.
        if (syntax.isExecutableComments() && (peek(2) == '!' || (peek(2) == 'M' && peek(3) == '!'))) {
            hasCode = true;
        }
        int depth = 1;
        int i = pos + 2;
        while (i < length && depth > 0) {
            if (syntax.isNestedBlockComments() && sql.charAt(i) == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                depth++;
                i += 2;
            } else if (sql.charAt(i) == '*' && i + 1 < length && sql.charAt(i + 1) == '/') {
                depth--;
                i += 2;
            } else {
                i++;
            }
        }
        pos = i;
    }

    private boolean usesBackslashEscape(char quote) {
        if (syntax.isBackslashEscapes()) {
            return true;
        }
        // PostgreSQL: only E'...' strings (not preceded by another identifier character) take backslash escapes
        return syntax.isEStrings() && quote == '\''
                && (charBefore(pos) == 'e' || charBefore(pos) == 'E')
                && !isIdentChar(charBefore(pos - 1));
    }

    private int skipQuoted(int start, char quote, boolean backslashEscapes) {
        int i = start + 1;
        while (i < length) {
            char ch = sql.charAt(i);
            if (backslashEscapes && ch == '\\') {
                i += 2;
            } else if (ch == quote) {
                if (i + 1 < length && sql.charAt(i + 1) == quote) {
                    i += 2;
                } else {
                    return i + 1;
                }
            } else {
                i++;
            }
        }
        return length;
    }

    private int skipBracketed(int start) {
        int i = start + 1;
        while (i < length) {
            if (sql.charAt(i) == ']') {
                if (i + 1 < length && sql.charAt(i + 1) == ']') {
                    i += 2;
                } else {
                    return i + 1;
                }
            } else {
                i++;
            }
        }
        return length;
    }

    // $$ ... $$ or $tag$ ... $tag$. A '$' followed by a digit ($1) is a parameter, not a quote.
    private boolean tryDollarQuote() {
        int i = pos + 1;
        if (i < length && sql.charAt(i) == '$') {
            i++;
        } else if (i < length && (Character.isLetter(sql.charAt(i)) || sql.charAt(i) == '_')) {
            i++;
            while (i < length && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) {
                i++;
            }
            if (i >= length || sql.charAt(i) != '$') {
                return false;
            }
            i++;
        } else {
            return false;
        }
        String tag = sql.substring(pos, i);
        int close = sql.indexOf(tag, i);
        pos = close == -1 ? length : close + tag.length();
        return true;
    }

    // A run of identifier characters. Only plain words (not "@var", "a$b", "t.end") act as keywords.
    private void scanWord() {
        int start = pos;
        while (pos < length && isIdentChar(sql.charAt(pos))
                && !(pos > start && sql.startsWith(delimiter, pos))) {
            pos++;
        }
        hasCode = true;

        String raw = sql.substring(start, pos);
        boolean keywordCandidate = raw.chars().allMatch(ch -> Character.isLetter(ch) || ch == '_')
                && charBefore(start) != '.';
        String word = keywordCandidate ? raw.toUpperCase(Locale.ROOT) : "?";

        String before = previousWord;
        previousWord = word;

        if (words.size() < 16) {
            words.add(word);
        }
        if (kind == Kind.UNDECIDED) {
            classify();
        }
        if (kind == Kind.BEGIN_END && keywordCandidate) {
            trackBlocks(word, before);
        }
    }

    private void classify() {
        String first = words.get(0);

        if (first.equals("CREATE") || first.equals("ALTER")) {
            for (int i = 1; i < words.size(); i++) {
                String word = words.get(i);
                if (syntax.getBeginEndObjects().contains(word)) {
                    kind = Kind.BEGIN_END;
                    return;
                }
                if (syntax.getBatchScopedObjects().contains(word)) {
                    kind = Kind.BATCH;
                    return;
                }
                if (NON_ROUTINE_OBJECTS.contains(word)) {
                    kind = Kind.NORMAL;
                    return;
                }
            }
            if (words.size() > MAX_CLASSIFY_WORDS) {
                kind = Kind.NORMAL;
            }
        } else if (first.equals("BEGIN") && !syntax.getBeginEndObjects().isEmpty()) {
            // MariaDB anonymous block: BEGIN NOT ATOMIC ... END
            if (words.size() >= 3) {
                if (words.get(1).equals("NOT") && words.get(2).equals("ATOMIC")) {
                    kind = Kind.BEGIN_END;
                    blocks.push('B');
                } else {
                    kind = Kind.NORMAL;
                }
            } else if (words.size() == 2 && !words.get(1).equals("NOT")) {
                kind = Kind.NORMAL;
            }
        } else {
            kind = Kind.NORMAL;
        }
    }

    // BEGIN and CASE open a block, END closes one. "END IF/LOOP/WHILE/REPEAT" close constructs that
    // were not opened by BEGIN/CASE; "END CASE" closes a CASE statement.
    private void trackBlocks(String word, String before) {
        switch (word) {
            case "BEGIN" -> blocks.push('B');
            case "CASE" -> {
                // the CASE of "END CASE" is the closing keyword of a CASE statement, not a new block
                if (!before.equals("END")) {
                    blocks.push('C');
                }
            }
            case "END" -> {
                String next = peekWord();
                if (next.equals("IF") || next.equals("LOOP") || next.equals("WHILE") || next.equals("REPEAT")) {
                    return;
                }
                if (!blocks.isEmpty()) {
                    blocks.pop();
                }
            }
            default -> {
            }
        }
    }

    // --- helpers --------------------------------------------------------------------------------------

    private String peekWord() {
        int i = pos;
        while (i < length && Character.isWhitespace(sql.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < length && (Character.isLetter(sql.charAt(i)) || sql.charAt(i) == '_')) {
            i++;
        }
        return sql.substring(start, i).toUpperCase(Locale.ROOT);
    }

    private boolean atLineStart() {
        for (int i = pos - 1; i >= from && i >= 0; i--) {
            char ch = sql.charAt(i);
            if (ch == '\n') {
                return true;
            }
            if (!Character.isWhitespace(ch)) {
                return false;
            }
        }
        return true;
    }

    private int endOfLine(int start) {
        int i = sql.indexOf('\n', start);
        return i == -1 ? length : i;
    }

    private char peek(int offset) {
        int i = pos + offset;
        return i < length ? sql.charAt(i) : '\0';
    }

    private char charBefore(int index) {
        return index > 0 && index <= length ? sql.charAt(index - 1) : '\0';
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '@' || c == '#';
    }
}
