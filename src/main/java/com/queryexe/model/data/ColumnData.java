package com.queryexe.model.data;

import lombok.Data;

@Data
public class ColumnData {

    private String columnName;
    private String dataType;
    private boolean primaryKey;
    private boolean notNull;
    private boolean unique;
    private boolean autoIncrement;
    private String uniqueIndexName;

    public ColumnData(String columnName, String dataType, boolean primaryKey, boolean notNull, boolean unique, boolean autoIncrement) {
        this.columnName = columnName;
        this.dataType = dataType;
        this.primaryKey = primaryKey;
        this.notNull = notNull;
        this.unique = unique;
        this.autoIncrement = autoIncrement;
    }

    public ColumnData(String columnName, String dataType, boolean primaryKey, boolean notNull, boolean unique, boolean autoIncrement, String uniqueIndexName) {
        this.columnName = columnName;
        this.dataType = dataType;
        this.primaryKey = primaryKey;
        this.notNull = notNull;
        this.unique = unique;
        this.autoIncrement = autoIncrement;
        this.uniqueIndexName=uniqueIndexName;
    }

    @Override
    public String toString() {
        return columnName;
    }
}