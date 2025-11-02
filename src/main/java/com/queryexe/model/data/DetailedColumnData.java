package com.queryexe.model.data;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DetailedColumnData {

    // Basic Information
    private String columnName;
    private String tableName;
    private String schemaName;
    private int ordinalPosition;

    // Data Type Information
    private String dataType;
    private String columnType;
    private Long characterMaximumLength;
    private Long characterOctetLength;
    private Integer numericPrecision;
    private Integer numericScale;
    private String characterSetName;
    private String collationName;

    // Constraints
    private boolean isPrimaryKey;
    private boolean isNullable;
    private boolean isUnique;
    private boolean isAutoIncrement;
    private String columnDefault;
    private String columnComment;
    private String columnKey;

    // Relationships
    private List<Map<String, String>> indexes;
    private List<Map<String, String>> foreignKeyReferences;
    private List<Map<String, String>> referencedByForeignKeys;
}