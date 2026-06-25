package com.queryexe.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.model.data.TableRowData;

public class ExportUtils {
	
	public static void exportToCSV(TableView<TableRowData> tableView) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Save CSV File");
		fileChooser.getExtensionFilters().add(
				new FileChooser.ExtensionFilter("CSV Files", "*.csv")
				);
		fileChooser.setInitialFileName("table_export.csv");

		File file = fileChooser.showSaveDialog(tableView.getScene().getWindow());

		if (file != null) {
			try (FileWriter csvWriter = new FileWriter(file)) {
				String headers = tableView.getColumns().stream()
						.map(col -> escapeCSV(col.getText()))
						.collect(Collectors.joining(";"));
				csvWriter.append(headers);
				csvWriter.append("\n");

				for (TableRowData row : tableView.getItems()) {
					String rowData = row.getStringData().stream()
							.map(cell -> escapeCSV(cell != null ? cell : ""))
							.collect(Collectors.joining(";"));
					csvWriter.append(rowData);
					csvWriter.append("\n");
				}

				csvWriter.flush();

				CustomNotification notification = new CustomNotification("Export Complete", "Your results were exported successfully.", new FontIcon(MaterialDesignD.DATABASE_EXPORT));
				notification.showNotification();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static String escapeCSV(String value) {
	    if (value == null) {
	        return "";
	    }
	    boolean needsQuotes = value.contains("\"") || value.contains(",") || 
	                         value.contains("\n") || value.contains("\r");
	    
	    if (needsQuotes) {
	        String escapedValue = value.replace("\"", "\"\"");
	        return "\"" + escapedValue + "\"";
	    }
	    return value;
	}
	
	public static void exportToJSON(TableView<TableRowData> tableView) {
	    FileChooser fileChooser = new FileChooser();
	    fileChooser.setTitle("Save JSON File");
	    fileChooser.getExtensionFilters().add(
	        new FileChooser.ExtensionFilter("JSON Files", "*.json")
	    );
	    fileChooser.setInitialFileName("table_export.json");

	    File file = fileChooser.showSaveDialog(tableView.getScene().getWindow());

	    if (file != null) {
	        try (FileWriter jsonWriter = new FileWriter(file)) {
	            List<String> headers = tableView.getColumns().stream()
	                .map(col -> col.getText())
	                .collect(Collectors.toList());

	            jsonWriter.write("[\n");

	            int rowCount = 0;
	            for (TableRowData row : tableView.getItems()) {
	                if (rowCount > 0) {
	                    jsonWriter.write(",\n");
	                }
	                
	                jsonWriter.write("  {");
	                for (int i = 0; i < headers.size(); i++) {
	                    if (i > 0) {
	                        jsonWriter.write(",");
	                    }
	                    String value = row.getStringData().get(i);
	                    jsonWriter.write(String.format("\n    \"%s\": \"%s\"",
	                        escapeJSON(headers.get(i)),
	                        escapeJSON(value != null ? value : "")
	                    ));
	                }
	                jsonWriter.write("\n  }");
	                rowCount++;
	            }

	            jsonWriter.write("\n]\n");
	            jsonWriter.flush();

	            CustomNotification notification = new CustomNotification(
	                "Export Complete",
	                "Your results were exported successfully.",
	                new FontIcon(MaterialDesignD.DATABASE_EXPORT)
	            );
	            notification.showNotification();

	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	    }
	}

	public static String escapeJSON(String input) {
	    if (input == null) {
	        return "";
	    }
	    return input.replace("\\", "\\\\")
	                .replace("\"", "\\\"")
	                .replace("\b", "\\b")
	                .replace("\f", "\\f")
	                .replace("\n", "\\n")
	                .replace("\r", "\\r")
	                .replace("\t", "\\t");
	}
	
	public static void exportToXML(TableView<TableRowData> tableView) {
	    FileChooser fileChooser = new FileChooser();
	    fileChooser.setTitle("Save XML File");
	    fileChooser.getExtensionFilters().add(
	            new FileChooser.ExtensionFilter("XML Files", "*.xml")
	    );
	    fileChooser.setInitialFileName("table_export.xml");

	    File file = fileChooser.showSaveDialog(tableView.getScene().getWindow());

	    if (file != null) {
	        try (FileWriter xmlWriter = new FileWriter(file)) {
	            xmlWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
	            xmlWriter.write("<Table>\n");

	            for (TableRowData row : tableView.getItems()) {
	                xmlWriter.write("    <Row>\n");

	                for (int i = 0; i < tableView.getColumns().size(); i++) {
	                    String columnName = escapeXML(tableView.getColumns().get(i).getText());
	                    String cellValue = escapeXML(row.getStringData().get(i) != null ? row.getStringData().get(i) : "");

	                    xmlWriter.write("        <" + columnName + ">" + cellValue + "</" + columnName + ">\n");
	                }

	                xmlWriter.write("    </Row>\n");
	            }
	            xmlWriter.write("</Table>\n");
	            xmlWriter.flush();

	            CustomNotification notification = new CustomNotification("Export Complete", "Your results were exported successfully.", new FontIcon(MaterialDesignD.DATABASE_EXPORT));
	            notification.showNotification();

	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	    }
	}

	public static String escapeXML(String input) {
	    if (input == null) return "";
	    return input.replace("&", "&amp;")
	                .replace("<", "&lt;")
	                .replace(">", "&gt;")
	                .replace("\"", "&quot;")
	                .replace("'", "&apos;");
	}
}
