package io.github.b077as.queryexe.query;

/**
 * A text parameter bound without a SQL type, so the database converts it to the column's own type. Needed where a plain
 * string is rejected (PostgreSQL does not assign character varying to json or xml).
 */
public record UntypedText(String value) {
}
