package io.github.b077as.queryexe.schema.tree;

import java.util.List;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class Partition {
    private String name;
    /** The partition this is a sub-partition of; null for a partition of the table. */
    private String parent;
    private String method;
    private String bound;
    private Long rows;
    /** True when the partition is a table of its own (PostgreSQL), so the page can open it like one. */
    private boolean table;
}
