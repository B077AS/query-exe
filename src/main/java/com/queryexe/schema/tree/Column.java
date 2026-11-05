package com.queryexe.schema.tree;

import java.util.List;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class Column {
    private String name;
    private String type;
    private boolean primaryKey;
    private boolean notNull;
    private boolean autoIncrement;
}
