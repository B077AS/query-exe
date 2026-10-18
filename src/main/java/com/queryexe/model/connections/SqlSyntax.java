package com.queryexe.model.connections;

import java.util.Set;

import lombok.Builder;
import lombok.Value;

/**
 * The lexical rules of an engine's SQL dialect that matter when cutting a script into statements:
 * how strings/identifiers/comments are quoted, which client-side commands exist, and which
 * {@code CREATE} objects can contain statement terminators in their body.
 * <p>
 * Each {@link ConnectionObject} exposes its own via {@link ConnectionObject#getSqlSyntax()}.
 * Build variants of an existing dialect with {@link #toBuilder()}.
 */
@Value
@Builder(toBuilder = true)
public class SqlSyntax {

    /** Backslash escapes inside '...' and "..." strings (MySQL, MariaDB). */
    boolean backslashEscapes;

    /** Backslash escapes only inside E'...' strings (PostgreSQL). */
    boolean eStrings;

    /** `identifier` quoting. */
    boolean backtickIdentifiers;

    /** [identifier] quoting. */
    boolean bracketIdentifiers;

    /** # line comments. */
    boolean hashComments;

    /** "--" only starts a comment when followed by whitespace (MySQL, MariaDB). */
    boolean dashDashNeedsSpace;

    /** Block comments starting with a bang (e.g. MySQL version comments) are run by the server, so they count as code. */
    boolean executableComments;

    /** $$ ... $$ and $tag$ ... $tag$ quoting. */
    boolean dollarQuoting;

    /** Block comments nest (PostgreSQL, SQL Server). */
    boolean nestedBlockComments;

    /** Client-side "DELIMITER x" command (MySQL, MariaDB). */
    boolean delimiterCommand;

    /** A line containing only GO ends a batch (SQL Server). */
    boolean goSeparator;

    /** CREATE of one of these objects may hold ';' inside a BEGIN ... END body. */
    @Builder.Default
    Set<String> beginEndObjects = Set.of();

    /** CREATE of one of these objects owns the whole batch up to GO; ';' inside it is not a terminator. */
    @Builder.Default
    Set<String> batchScopedObjects = Set.of();

    public static final SqlSyntax ANSI = SqlSyntax.builder().build();

    public static final SqlSyntax MYSQL = ANSI.toBuilder()
            .backslashEscapes(true)
            .backtickIdentifiers(true)
            .hashComments(true)
            .dashDashNeedsSpace(true)
            .executableComments(true)
            .delimiterCommand(true)
            .beginEndObjects(Set.of("PROCEDURE", "FUNCTION", "TRIGGER", "EVENT"))
            .build();

    public static final SqlSyntax POSTGRES = ANSI.toBuilder()
            .eStrings(true)
            .dollarQuoting(true)
            .nestedBlockComments(true)
            .build();

    public static final SqlSyntax SQLSERVER = ANSI.toBuilder()
            .bracketIdentifiers(true)
            .nestedBlockComments(true)
            .goSeparator(true)
            .batchScopedObjects(Set.of("PROC", "PROCEDURE", "FUNCTION", "TRIGGER"))
            .build();

    public static final SqlSyntax H2 = ANSI.toBuilder()
            .backtickIdentifiers(true)
            .dollarQuoting(true)
            .build();

    public static final SqlSyntax SQLITE = ANSI.toBuilder()
            .backtickIdentifiers(true)
            .bracketIdentifiers(true)
            .beginEndObjects(Set.of("TRIGGER"))
            .build();
}
