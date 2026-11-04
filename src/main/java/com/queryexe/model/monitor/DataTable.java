package com.queryexe.model.monitor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import lombok.Value;

/** Rows of text for the page. {@code note} says why there is nothing to show when the engine could not answer (for example a missing privilege). */
@Value
public class DataTable {

    private static final int MAX_ROWS = 500;
    private static final int MAX_CELL = 400;

    List<String> columns;
    List<List<String>> rows;
    String note;

    public static DataTable empty(String note) {
        return new DataTable(List.of(), List.of(), note);
    }

    /** Runs a query and keeps at most {@link #MAX_ROWS} rows; long cells are cut. A failure becomes a note, not an error. */
    public static DataTable query(Connection connection, String sql) {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int count = meta.getColumnCount();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                columns.add(meta.getColumnLabel(i).toLowerCase(java.util.Locale.ROOT));
            }
            List<List<String>> rows = new ArrayList<>();
            while (rs.next() && rows.size() < MAX_ROWS) {
                List<String> row = new ArrayList<>(count);
                for (int i = 1; i <= count; i++) {
                    String value = rs.getString(i);
                    row.add(value == null ? null : value.length() > MAX_CELL ? value.substring(0, MAX_CELL) + "…" : value);
                }
                rows.add(row);
            }
            return new DataTable(columns, rows, null);
        } catch (SQLException e) {
            return empty(e.getMessage());
        }
    }
}
