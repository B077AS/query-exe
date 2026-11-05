package com.queryexe.schema.tree;

import java.util.List;

@lombok.Data
public class Table {
    private String name;
    private List<Column> columns;
    /** Empty for tables that are not partitioned. */
    private List<Partition> partitions;

    public Table(String name, List<Column> columns) {
        this(name, columns, List.of());
    }

    public Table(String name, List<Column> columns, List<Partition> partitions) {
        this.name = name;
        this.columns = columns;
        this.partitions = partitions;
    }
}
