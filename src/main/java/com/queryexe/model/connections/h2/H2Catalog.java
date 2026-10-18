package com.queryexe.model.connections.h2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Reads an H2 database's structure from {@code INFORMATION_SCHEMA} and renders it back as DDL.
 * <p>
 * Names are always taken and returned exactly as stored (H2 keeps quoted identifiers case-sensitive), never
 * upper-cased. Works on any plain {@link Connection}, so it needs no application state.
 */
@RequiredArgsConstructor
public class H2Catalog {

    /** Names H2 generates for unnamed constraints; they are left out of generated DDL so H2 can generate them again. */
    private static final Pattern GENERATED_CONSTRAINT_NAME = Pattern.compile("^(CONSTRAINT|PRIMARY_KEY)_[0-9A-F]+$");

    private static final long UNBOUNDED_LENGTH = 1_000_000_000L;
    private static final long DEFAULT_NUMERIC_PRECISION = 100_000L;
    private static final int DEFAULT_ARRAY_CARDINALITY = 65_536;

    private final Connection connection;

    // ------------------------------------------------------------------------------------ model

    /** The parts of a column's declared type, as {@code INFORMATION_SCHEMA} reports them. */
    @Value
    @Builder
    static class TypeParts {
        String dataType;
        Long characterLength;
        Long numericPrecision;
        Long numericScale;
        Integer datetimePrecision;
        String intervalType;
        String declaredDataType;
        Integer maximumCardinality;
        String dtdIdentifier;
    }

    @Value
    @Builder
    public static class ColumnInfo {
        String name;
        /** Executable SQL type, e.g. {@code CHARACTER VARYING(50)}, {@code NUMERIC(10, 2)}, {@code INTEGER ARRAY}. */
        String typeSql;
        boolean nullable;
        String defaultValue;
        boolean identity;
        /** {@code BY DEFAULT} or {@code ALWAYS}. */
        String identityGeneration;
        Long identityStart;
        Long identityIncrement;
        boolean generated;
        String generationExpression;
    }

    /** A PRIMARY KEY or UNIQUE constraint. */
    @Value
    @Builder
    public static class KeyInfo {
        String name;
        boolean primary;
        List<String> columns;
    }

    @Value
    @Builder
    public static class ForeignKeyInfo {
        String name;
        List<String> columns;
        String referenceSchema;
        String referenceTable;
        List<String> referenceColumns;
        String deleteRule;
        String updateRule;
    }

    /** An index created on purpose (not one H2 made for a key or foreign key). */
    @Value
    @Builder
    public static class IndexInfo {
        String name;
        boolean unique;
        boolean spatial;
        List<String> columns;
        List<Boolean> descending;
    }

    // ------------------------------------------------------------------------------------ naming

    public static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public static String qualified(String schema, String table) {
        return quote(schema) + "." + quote(table);
    }

    /** The schema the connection currently resolves unqualified names in. */
    public String currentSchema() throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT SCHEMA()");
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getString(1) : "PUBLIC";
        }
    }

    // ------------------------------------------------------------------------------------ reading

    /** Names of the sequences created in the schema (the ones behind identity columns are not listed by H2). */
    public List<String> sequences(String schema) throws SQLException {
        return names("SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ? ORDER BY sequence_name", schema);
    }

    public List<String> synonyms(String schema) throws SQLException {
        return names("SELECT synonym_name FROM information_schema.synonyms WHERE synonym_schema = ? ORDER BY synonym_name", schema);
    }

    private List<String> names(String sql, String schema) throws SQLException {
        List<String> names = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        }
        return names;
    }

    public List<ColumnInfo> columns(String schema, String table) throws SQLException {
        record Raw(ColumnInfo.ColumnInfoBuilder builder, TypeParts parts) {
        }

        List<Raw> raw = new ArrayList<>();
        String sql = "SELECT COLUMN_NAME, COLUMN_DEFAULT, IS_NULLABLE, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, "
                + "NUMERIC_PRECISION, NUMERIC_SCALE, DATETIME_PRECISION, INTERVAL_TYPE, DECLARED_DATA_TYPE, "
                + "MAXIMUM_CARDINALITY, DTD_IDENTIFIER, IS_IDENTITY, IDENTITY_GENERATION, IDENTITY_START, "
                + "IDENTITY_INCREMENT, IS_GENERATED, GENERATION_EXPRESSION "
                + "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TypeParts parts = TypeParts.builder()
                            .dataType(rs.getString("DATA_TYPE"))
                            .characterLength(nullableLong(rs, "CHARACTER_MAXIMUM_LENGTH"))
                            .numericPrecision(nullableLong(rs, "NUMERIC_PRECISION"))
                            .numericScale(nullableLong(rs, "NUMERIC_SCALE"))
                            .datetimePrecision(nullableInt(rs, "DATETIME_PRECISION"))
                            .intervalType(rs.getString("INTERVAL_TYPE"))
                            .declaredDataType(rs.getString("DECLARED_DATA_TYPE"))
                            .maximumCardinality(nullableInt(rs, "MAXIMUM_CARDINALITY"))
                            .dtdIdentifier(rs.getString("DTD_IDENTIFIER"))
                            .build();
                    ColumnInfo.ColumnInfoBuilder builder = ColumnInfo.builder()
                            .name(rs.getString("COLUMN_NAME"))
                            .nullable("YES".equals(rs.getString("IS_NULLABLE")))
                            .defaultValue(rs.getString("COLUMN_DEFAULT"))
                            .identity("YES".equals(rs.getString("IS_IDENTITY")))
                            .identityGeneration(rs.getString("IDENTITY_GENERATION"))
                            .identityStart(nullableLong(rs, "IDENTITY_START"))
                            .identityIncrement(nullableLong(rs, "IDENTITY_INCREMENT"))
                            .generated("ALWAYS".equals(rs.getString("IS_GENERATED")))
                            .generationExpression(rs.getString("GENERATION_EXPRESSION"));
                    raw.add(new Raw(builder, parts));
                }
            }
        }

        TypeLookup lookup = new TypeLookup(schema, table);
        List<ColumnInfo> columns = new ArrayList<>();
        for (Raw column : raw) {
            columns.add(column.builder().typeSql(composeType(column.parts(), lookup)).build());
        }
        return columns;
    }

    /** PRIMARY KEY first, then UNIQUE constraints; columns in key order. */
    public List<KeyInfo> keys(String schema, String table) throws SQLException {
        String sql = "SELECT tc.CONSTRAINT_NAME, tc.CONSTRAINT_TYPE, ku.COLUMN_NAME "
                + "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                + "JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE ku "
                + "  ON ku.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND ku.CONSTRAINT_NAME = tc.CONSTRAINT_NAME "
                + "WHERE tc.CONSTRAINT_TYPE IN ('PRIMARY KEY', 'UNIQUE') AND tc.TABLE_SCHEMA = ? AND tc.TABLE_NAME = ? "
                + "ORDER BY tc.CONSTRAINT_NAME, ku.ORDINAL_POSITION";

        Map<String, List<String>> columnsByKey = new LinkedHashMap<>();
        Map<String, Boolean> primaryByKey = new HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("CONSTRAINT_NAME");
                    columnsByKey.computeIfAbsent(name, k -> new ArrayList<>()).add(rs.getString("COLUMN_NAME"));
                    primaryByKey.put(name, "PRIMARY KEY".equals(rs.getString("CONSTRAINT_TYPE")));
                }
            }
        }

        List<KeyInfo> keys = new ArrayList<>();
        columnsByKey.forEach((name, columns) ->
                keys.add(KeyInfo.builder().name(name).primary(primaryByKey.get(name)).columns(columns).build()));
        keys.sort((a, b) -> Boolean.compare(b.isPrimary(), a.isPrimary()));
        return keys;
    }

    public List<ForeignKeyInfo> foreignKeys(String schema, String table) throws SQLException {
        String sql = "SELECT rc.CONSTRAINT_NAME, rc.DELETE_RULE, rc.UPDATE_RULE, kcu.COLUMN_NAME, "
                + "       ref.TABLE_SCHEMA AS REF_SCHEMA, ref.TABLE_NAME AS REF_TABLE, ref.COLUMN_NAME AS REF_COLUMN "
                + "FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc "
                + "JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu "
                + "  ON kcu.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA AND kcu.CONSTRAINT_NAME = rc.CONSTRAINT_NAME "
                + "JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE ref "
                + "  ON ref.CONSTRAINT_SCHEMA = rc.UNIQUE_CONSTRAINT_SCHEMA AND ref.CONSTRAINT_NAME = rc.UNIQUE_CONSTRAINT_NAME "
                + " AND ref.ORDINAL_POSITION = kcu.POSITION_IN_UNIQUE_CONSTRAINT "
                + "WHERE kcu.TABLE_SCHEMA = ? AND kcu.TABLE_NAME = ? "
                + "ORDER BY rc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION";

        Map<String, ForeignKeyInfo.ForeignKeyInfoBuilder> builders = new LinkedHashMap<>();
        Map<String, List<String>> localColumns = new HashMap<>();
        Map<String, List<String>> referenceColumns = new HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("CONSTRAINT_NAME");
                    builders.computeIfAbsent(name, k -> {
                        try {
                            return ForeignKeyInfo.builder().name(name)
                                    .referenceSchema(rs.getString("REF_SCHEMA"))
                                    .referenceTable(rs.getString("REF_TABLE"))
                                    .deleteRule(rs.getString("DELETE_RULE"))
                                    .updateRule(rs.getString("UPDATE_RULE"));
                        } catch (SQLException e) {
                            throw new IllegalStateException(e);
                        }
                    });
                    localColumns.computeIfAbsent(name, k -> new ArrayList<>()).add(rs.getString("COLUMN_NAME"));
                    referenceColumns.computeIfAbsent(name, k -> new ArrayList<>()).add(rs.getString("REF_COLUMN"));
                }
            }
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw e;
        }

        List<ForeignKeyInfo> result = new ArrayList<>();
        builders.forEach((name, builder) -> result.add(
                builder.columns(localColumns.get(name)).referenceColumns(referenceColumns.get(name)).build()));
        return result;
    }

    /** Indexes someone created explicitly; the ones H2 makes for keys and foreign keys are not included. */
    public List<IndexInfo> userIndexes(String schema, String table) throws SQLException {
        String sql = "SELECT i.INDEX_NAME, i.INDEX_TYPE_NAME, i.IS_GENERATED, ic.COLUMN_NAME, ic.ORDERING_SPECIFICATION "
                + "FROM INFORMATION_SCHEMA.INDEXES i "
                + "JOIN INFORMATION_SCHEMA.INDEX_COLUMNS ic "
                + "  ON ic.INDEX_SCHEMA = i.INDEX_SCHEMA AND ic.INDEX_NAME = i.INDEX_NAME "
                + "WHERE i.TABLE_SCHEMA = ? AND i.TABLE_NAME = ? ORDER BY i.INDEX_NAME, ic.ORDINAL_POSITION";

        Map<String, String> typeByIndex = new LinkedHashMap<>();
        Map<String, List<String>> columnsByIndex = new HashMap<>();
        Map<String, List<Boolean>> descendingByIndex = new HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("INDEX_TYPE_NAME");
                    if ("TRUE".equalsIgnoreCase(rs.getString("IS_GENERATED")) || "PRIMARY KEY".equals(type)) {
                        continue;
                    }
                    String name = rs.getString("INDEX_NAME");
                    typeByIndex.put(name, type);
                    columnsByIndex.computeIfAbsent(name, k -> new ArrayList<>()).add(rs.getString("COLUMN_NAME"));
                    descendingByIndex.computeIfAbsent(name, k -> new ArrayList<>())
                            .add("DESC".equalsIgnoreCase(rs.getString("ORDERING_SPECIFICATION")));
                }
            }
        }

        List<IndexInfo> indexes = new ArrayList<>();
        typeByIndex.forEach((name, type) -> indexes.add(IndexInfo.builder()
                .name(name)
                .unique(type.contains("UNIQUE"))
                .spatial(type.contains("SPATIAL"))
                .columns(columnsByIndex.get(name))
                .descending(descendingByIndex.get(name))
                .build()));
        return indexes;
    }

    // ------------------------------------------------------------------------------------ types

    /** Resolves array element types and enum values, which live in their own INFORMATION_SCHEMA views. */
    private class TypeLookup {
        private final String schema;
        private final String table;
        private Map<String, TypeParts> elementTypes;
        private Map<String, List<String>> enumValues;

        TypeLookup(String schema, String table) {
            this.schema = schema;
            this.table = table;
        }

        TypeParts elementOf(String collectionIdentifier) throws SQLException {
            if (elementTypes == null) {
                elementTypes = new HashMap<>();
                String sql = "SELECT COLLECTION_TYPE_IDENTIFIER, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, "
                        + "NUMERIC_SCALE, DATETIME_PRECISION, INTERVAL_TYPE, DECLARED_DATA_TYPE, MAXIMUM_CARDINALITY, DTD_IDENTIFIER "
                        + "FROM INFORMATION_SCHEMA.ELEMENT_TYPES WHERE OBJECT_SCHEMA = ? AND OBJECT_NAME = ? AND OBJECT_TYPE = 'TABLE'";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, schema);
                    stmt.setString(2, table);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            elementTypes.put(rs.getString("COLLECTION_TYPE_IDENTIFIER"), TypeParts.builder()
                                    .dataType(rs.getString("DATA_TYPE"))
                                    .characterLength(nullableLong(rs, "CHARACTER_MAXIMUM_LENGTH"))
                                    .numericPrecision(nullableLong(rs, "NUMERIC_PRECISION"))
                                    .numericScale(nullableLong(rs, "NUMERIC_SCALE"))
                                    .datetimePrecision(nullableInt(rs, "DATETIME_PRECISION"))
                                    .intervalType(rs.getString("INTERVAL_TYPE"))
                                    .declaredDataType(rs.getString("DECLARED_DATA_TYPE"))
                                    .maximumCardinality(nullableInt(rs, "MAXIMUM_CARDINALITY"))
                                    .dtdIdentifier(rs.getString("DTD_IDENTIFIER"))
                                    .build());
                        }
                    }
                }
            }
            return elementTypes.get(collectionIdentifier);
        }

        List<String> enumValuesOf(String enumIdentifier) throws SQLException {
            if (enumValues == null) {
                enumValues = new HashMap<>();
                String sql = "SELECT ENUM_IDENTIFIER, VALUE_NAME FROM INFORMATION_SCHEMA.ENUM_VALUES "
                        + "WHERE OBJECT_SCHEMA = ? AND OBJECT_NAME = ? AND OBJECT_TYPE = 'TABLE' ORDER BY VALUE_ORDINAL";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, schema);
                    stmt.setString(2, table);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            enumValues.computeIfAbsent(rs.getString("ENUM_IDENTIFIER"), k -> new ArrayList<>())
                                    .add(rs.getString("VALUE_NAME"));
                        }
                    }
                }
            }
            return enumValues.getOrDefault(enumIdentifier, List.of());
        }
    }

    private static String composeType(TypeParts parts, TypeLookup lookup) throws SQLException {
        String type = parts.getDataType();

        switch (type) {
            case "CHARACTER VARYING", "CHARACTER", "BINARY VARYING", "BINARY", "VARCHAR_IGNORECASE": {
                Long length = parts.getCharacterLength();
                boolean unbounded = length == null || (length >= UNBOUNDED_LENGTH && type.endsWith("VARYING"));
                return unbounded ? type : type + "(" + length + ")";
            }
            case "NUMERIC": {
                String name = "DECIMAL".equals(parts.getDeclaredDataType()) ? "DECIMAL" : "NUMERIC";
                Long precision = parts.getNumericPrecision();
                if (precision == null || precision >= DEFAULT_NUMERIC_PRECISION) {
                    return name;
                }
                Long scale = parts.getNumericScale();
                return scale != null && scale > 0 ? name + "(" + precision + ", " + scale + ")" : name + "(" + precision + ")";
            }
            case "DECFLOAT": {
                Long precision = parts.getNumericPrecision();
                return precision == null || precision >= DEFAULT_NUMERIC_PRECISION ? type : type + "(" + precision + ")";
            }
            case "TIMESTAMP", "TIMESTAMP WITH TIME ZONE", "TIME", "TIME WITH TIME ZONE": {
                Integer precision = parts.getDatetimePrecision();
                int defaultPrecision = type.startsWith("TIMESTAMP") ? 6 : 0;
                if (precision == null || precision == defaultPrecision) {
                    return type;
                }
                String base = type.startsWith("TIMESTAMP") ? "TIMESTAMP" : "TIME";
                return base + "(" + precision + ")" + type.substring(base.length());
            }
            case "INTERVAL":
                return parts.getIntervalType() == null ? type : "INTERVAL " + parts.getIntervalType();
            case "ARRAY": {
                TypeParts element = lookup.elementOf(parts.getDtdIdentifier());
                String elementType = element == null ? "VARCHAR" : composeType(element, lookup);
                Integer cardinality = parts.getMaximumCardinality();
                return elementType + " ARRAY" + (cardinality == null || cardinality == DEFAULT_ARRAY_CARDINALITY
                        ? "" : "[" + cardinality + "]");
            }
            case "ENUM": {
                StringJoiner values = new StringJoiner(", ", "ENUM(", ")");
                for (String value : lookup.enumValuesOf(parts.getDtdIdentifier())) {
                    values.add("'" + value.replace("'", "''") + "'");
                }
                return values.toString();
            }
            default:
                return type;
        }
    }

    // ------------------------------------------------------------------------------------ DDL

    /**
     * {@code CREATE TABLE} for the table (columns, key constraints, foreign keys) followed by one
     * {@code CREATE INDEX} per explicitly created index. Everything is qualified with its schema.
     */
    public String createScript(String schema, String table) throws SQLException {
        List<String> definitions = new ArrayList<>();

        for (ColumnInfo column : columns(schema, table)) {
            definitions.add("  " + columnDefinition(column));
        }
        for (KeyInfo key : keys(schema, table)) {
            definitions.add("  " + constraintPrefix(key.getName())
                    + (key.isPrimary() ? "PRIMARY KEY" : "UNIQUE") + " (" + quoteAll(key.getColumns()) + ")");
        }
        for (ForeignKeyInfo fk : foreignKeys(schema, table)) {
            StringBuilder line = new StringBuilder("  ").append(constraintPrefix(fk.getName()))
                    .append("FOREIGN KEY (").append(quoteAll(fk.getColumns())).append(") REFERENCES ")
                    .append(qualified(fk.getReferenceSchema(), fk.getReferenceTable()))
                    .append(" (").append(quoteAll(fk.getReferenceColumns())).append(")");
            if (isAction(fk.getDeleteRule())) {
                line.append(" ON DELETE ").append(fk.getDeleteRule());
            }
            if (isAction(fk.getUpdateRule())) {
                line.append(" ON UPDATE ").append(fk.getUpdateRule());
            }
            definitions.add(line.toString());
        }

        StringBuilder script = new StringBuilder("CREATE TABLE ").append(qualified(schema, table)).append(" (\n")
                .append(String.join(",\n", definitions)).append("\n);");

        for (IndexInfo index : userIndexes(schema, table)) {
            StringJoiner columns = new StringJoiner(", ");
            for (int i = 0; i < index.getColumns().size(); i++) {
                columns.add(quote(index.getColumns().get(i)) + (index.getDescending().get(i) ? " DESC" : ""));
            }
            script.append("\nCREATE ").append(index.isUnique() ? "UNIQUE " : index.isSpatial() ? "SPATIAL " : "")
                    .append("INDEX ").append(qualified(schema, index.getName()))
                    .append(" ON ").append(qualified(schema, table)).append(" (").append(columns).append(");");
        }
        return script.toString();
    }

    private static String columnDefinition(ColumnInfo column) {
        StringBuilder definition = new StringBuilder(quote(column.getName())).append(' ').append(column.getTypeSql());
        if (column.isGenerated()) {
            return definition.append(" GENERATED ALWAYS AS (").append(column.getGenerationExpression()).append(")").toString();
        }
        if (column.isIdentity()) {
            definition.append(" GENERATED ").append(column.getIdentityGeneration()).append(" AS IDENTITY");
            boolean customStart = column.getIdentityStart() != null && column.getIdentityStart() != 1;
            boolean customStep = column.getIdentityIncrement() != null && column.getIdentityIncrement() != 1;
            if (customStart || customStep) {
                definition.append(" (START WITH ").append(Objects.requireNonNullElse(column.getIdentityStart(), 1L))
                        .append(" INCREMENT BY ").append(Objects.requireNonNullElse(column.getIdentityIncrement(), 1L)).append(")");
            }
        } else if (column.getDefaultValue() != null) {
            definition.append(" DEFAULT ").append(column.getDefaultValue());
        }
        if (!column.isNullable()) {
            definition.append(" NOT NULL");
        }
        return definition.toString();
    }

    private static String constraintPrefix(String name) {
        return GENERATED_CONSTRAINT_NAME.matcher(name).matches() ? "" : "CONSTRAINT " + quote(name) + " ";
    }

    private static boolean isAction(String rule) {
        return rule != null && !rule.equals("NO ACTION");
    }

    private static String quoteAll(List<String> identifiers) {
        StringJoiner joined = new StringJoiner(", ");
        identifiers.forEach(identifier -> joined.add(quote(identifier)));
        return joined.toString();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
