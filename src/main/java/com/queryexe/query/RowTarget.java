package com.queryexe.query;

import java.util.List;

/** What a result grid knows about the table its rows came from; used to script a row as an INSERT. */
public interface RowTarget {

    /** The table name as reported by the result set metadata. */
    String tableName();

    /** The quoted, schema-qualified name to use in generated SQL. */
    String qualifiedTableName();

    /** The real (un-aliased) column names, in grid order. */
    List<String> columnNames();

    /** {@code java.sql.Types} code of the column at this index. */
    int columnSqlType(int index);
}
