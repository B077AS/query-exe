package com.queryexe.schema;

import lombok.Data;

/** One partition (or sub-partition) of a table, as the tree shows it. */
@Data
public class PartitionData {

    private String name;
    /** The partition it is a sub-partition of, or null for a partition of the table itself. */
    private String parent;
    /** RANGE, LIST, HASH, KEY ... when the engine tells; may be null. */
    private String method;
    /** The bound in readable form ("< 2020", "IN (1, 2)", "1 .. 10"); may be null. */
    private String bound;
    /** Estimated rows, or null when unknown. */
    private Long rows;

    public PartitionData(String name, String parent, String method, String bound, Long rows) {
        this.name = name;
        this.parent = parent;
        this.method = method;
        this.bound = bound;
        this.rows = rows;
    }

    @Override
    public String toString() {
        return name;
    }
}
