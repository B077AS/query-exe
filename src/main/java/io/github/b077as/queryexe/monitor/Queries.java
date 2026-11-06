package io.github.b077as.queryexe.monitor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Small readers the engine monitors share. */
public class Queries {

    private Queries() {
    }

    /** First row of a query as column label (lower case) to number; columns that are not numbers are skipped. */
    public static Map<String, Double> row(Connection connection, String sql) throws SQLException {
        Map<String, Double> out = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    Double value = number(rs.getString(i));
                    if (value != null) {
                        out.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), value);
                    }
                }
            }
        }
        return out;
    }

    /** A query of (name, value) rows as name to number. */
    public static Map<String, Double> pairs(Connection connection, String sql) throws SQLException {
        Map<String, Double> out = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                Double value = number(rs.getString(2));
                if (value != null) {
                    out.put(rs.getString(1), value);
                }
            }
        }
        return out;
    }

    public static Double number(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
