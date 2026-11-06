package io.github.b077as.queryexe.query;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import lombok.experimental.UtilityClass;

/** Writes a result grid as CSV, JSON or XML (UTF-8). */
@UtilityClass
public class Exporter {

    public static final List<String> FORMATS = List.of("csv", "json", "xml");

    public static void write(Path file, String format, List<String> headers, List<String[]> rows) throws IOException {
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            switch (format) {
                case "csv" -> csv(out, headers, rows);
                case "json" -> json(out, headers, rows);
                case "xml" -> xml(out, headers, rows);
                default -> throw new IllegalArgumentException("Unknown format: " + format);
            }
        }
    }

    static void csv(Writer out, List<String> headers, List<String[]> rows) throws IOException {
        out.write(joinCsv(headers.toArray(new String[0])));
        out.write("\r\n");
        for (String[] row : rows) {
            out.write(joinCsv(row));
            out.write("\r\n");
        }
    }

    private static String joinCsv(String[] cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escapeCsv(cells[i]));
        }
        return line.toString();
    }

    public static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains("\"") || value.contains(",") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    static void json(Writer out, List<String> headers, List<String[]> rows) throws IOException {
        out.write("[");
        boolean firstRow = true;
        for (String[] row : rows) {
            out.write(firstRow ? "\n" : ",\n");
            firstRow = false;
            out.write("  {");
            for (int i = 0; i < headers.size(); i++) {
                out.write(i == 0 ? "\n" : ",\n");
                String value = i < row.length ? row[i] : null;
                out.write("    \"" + escapeJson(headers.get(i)) + "\": " + (value == null ? "null" : "\"" + escapeJson(value) + "\""));
            }
            out.write("\n  }");
        }
        out.write(rows.isEmpty() ? "]\n" : "\n]\n");
    }

    public static String escapeJson(String input) {
        StringBuilder out = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    static void xml(Writer out, List<String> headers, List<String[]> rows) throws IOException {
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Table>\n");
        for (String[] row : rows) {
            out.write("    <Row>\n");
            for (int i = 0; i < headers.size(); i++) {
                String name = elementName(headers.get(i), i);
                String value = i < row.length ? row[i] : null;
                if (value == null) {
                    out.write("        <" + name + " nil=\"true\"/>\n");
                } else {
                    out.write("        <" + name + ">" + escapeXml(value) + "</" + name + ">\n");
                }
            }
            out.write("    </Row>\n");
        }
        out.write("</Table>\n");
    }

    /** A column label as an XML element name: letters, digits, '-', '_' and '.', not starting with a digit. */
    static String elementName(String label, int index) {
        String name = label == null ? "" : label.replaceAll("[^\\p{L}\\p{N}_.-]", "_");
        if (name.isEmpty()) {
            return "column" + (index + 1);
        }
        return Character.isLetter(name.charAt(0)) || name.charAt(0) == '_' ? name : "_" + name;
    }

    public static String escapeXml(String input) {
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
