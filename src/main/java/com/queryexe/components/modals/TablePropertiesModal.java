package com.queryexe.components.modals;

import lombok.extern.slf4j.Slf4j;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;
import atlantafx.base.theme.Styles;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.model.data.ColumnData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.model.data.ForeignKeyData;
import com.queryexe.queryexe.App;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

@Slf4j
public class TablePropertiesModal extends VBox {

    private String databaseName;
    private String tableName;
    private StackPane contentPane;
    private HBox tabHeaders;

    private final String activeTab = """
            -fx-background-color: transparent;
            -fx-border-width: 0 0 2px 0;
            -fx-border-color: -color-border-default;
            -fx-font-weight: bold;
            """;

    private final String inactiveTab = """
            -fx-background-color: transparent;
            -fx-border-width: 0 0 2px 0;
            -fx-border-color: transparent;
            -fx-font-weight: bold;
            -fx-opacity: 0.6;
            """;

    public TablePropertiesModal(String databaseName, String tableName) {
        this.databaseName = databaseName;
        this.tableName = tableName;
        initializeUI();
        loadTableData();
    }

    private void initializeUI() {
        this.getStyleClass().add("modal-container");
        this.setMaxSize(950, 550);
        this.setMinSize(950, 550);
        this.setPrefSize(950, 550);

        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPadding(new Insets(10, 10, 0, 0));

        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(event -> App.closeModal());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        headerBox.getChildren().addAll(spacer, closeButton);

        tabHeaders = new HBox();
        tabHeaders.setAlignment(Pos.CENTER);
        tabHeaders.setSpacing(5);
        tabHeaders.setPadding(new Insets(15, 5, 0, 5));

        contentPane = new StackPane();
        VBox.setVgrow(contentPane, Priority.ALWAYS);

        this.getChildren().addAll(headerBox, tabHeaders, contentPane);
    }

    private void loadTableData() {
        try {
            ArrayList<ColumnData> columns = DatabaseConnection.getInstance().getConnectionObject().getColumnsForTable(databaseName, tableName);
            Map<String, ForeignKeyData> foreignKeys = DatabaseConnection.getInstance().getConnectionObject().extractForeignKeys(databaseName, tableName);

            VBox columnsTab = createColumnsTab(columns);
            VBox foreignKeysTab = createForeignKeysTab(foreignKeys);
            VBox propertiesTab = createPropertiesTab(columns.size(), foreignKeys.size());

            Button columnsButton = createTabHeader("Columns", columnsTab, new FontIcon(MaterialDesignF.FORMAT_COLUMNS));
            Button foreignKeysButton = createTabHeader("Foreign Keys", foreignKeysTab, new FontIcon(MaterialDesignT.TABLE_KEY));
            Button propertiesButton = createTabHeader("Details", propertiesTab, new FontIcon(MaterialDesignI.INFORMATION_OUTLINE));

            columnsButton.setStyle(activeTab);
            foreignKeysButton.setStyle(inactiveTab);
            propertiesButton.setStyle(inactiveTab);

            tabHeaders.getChildren().addAll(columnsButton, foreignKeysButton, propertiesButton);
            contentPane.getChildren().add(columnsTab);

        } catch (SQLException e) {
            CustomNotification notification = new CustomNotification("Load Failed", "Could not load table data: " + e.getMessage(), new FontIcon(MaterialDesignT.TABLE_CANCEL));
            notification.showNotificationOnCustomPane((StackPane) this.getParent());
            log.error("loadTableData failed", e);
        }
    }

    private Button createTabHeader(String title, Node content, FontIcon icon) {
        Button tabHeader = new Button(title, icon);
        tabHeader.setMinWidth(935 / 3);
        tabHeader.setMaxWidth(935 / 3);
        HBox.setHgrow(tabHeader, Priority.ALWAYS);

        tabHeader.setOnAction(e -> {
            contentPane.getChildren().clear();
            contentPane.getChildren().add(content);
            for (Node node : tabHeaders.getChildren()) {
                if (node instanceof Button) {
                    ((Button) node).setStyle(inactiveTab);
                }
            }
            tabHeader.setStyle(activeTab);
        });
        return tabHeader;
    }

    private VBox createColumnsTab(ArrayList<ColumnData> columns) {
        VBox container = new VBox();
        container.setPadding(new Insets(15));
        container.setStyle("-fx-background-color: -color-bg-default;");

        if (columns.isEmpty()) {
            Label emptyLabel = new Label("No columns found");
            emptyLabel.setStyle("-fx-opacity: 0.6; -fx-font-size: 14px;");
            container.getChildren().add(emptyLabel);
        } else {
            VBox tableContainer = new VBox();
            tableContainer.setStyle("""
                    -fx-background-color: -color-bg-default;
                    -fx-border-radius: 8px;
                    -fx-background-radius: 8px;
                    -fx-border-color: -color-accent-9;
                    -fx-border-width: 1px;
                    """);

            HBox headerRow = createColumnHeaderRow();
            tableContainer.getChildren().add(headerRow);

            for (int i = 0; i < columns.size(); i++) {
                ColumnData column = columns.get(i);
                HBox dataRow = createColumnDataRow(column, i % 2 == 0);
                tableContainer.getChildren().add(dataRow);
            }

            container.getChildren().add(tableContainer);
        }

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox wrapper = new VBox(scrollPane);
        wrapper.setPadding(new Insets(0, 0, 10, 0));
        return wrapper;
    }

    private HBox createColumnHeaderRow() {
        HBox headerRow = new HBox();
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(12, 15, 12, 15));
        headerRow.setStyle("""
                -fx-background-color: -color-accent-9-alpha30;
                -fx-border-width: 0 0 1px 0;
                -fx-border-color: -color-accent-9;
                """);

        Label nameHeader = new Label("Column Name");
        nameHeader.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 0.8;");
        nameHeader.setPrefWidth(250);
        nameHeader.setMinWidth(250);

        Label typeHeader = new Label("Data Type");
        typeHeader.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 0.8;");
        typeHeader.setPrefWidth(250);
        typeHeader.setMinWidth(250);

        Label constraintsHeader = new Label("Constraints");
        constraintsHeader.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 0.8;");
        HBox.setHgrow(constraintsHeader, Priority.ALWAYS);

        headerRow.getChildren().addAll(nameHeader, typeHeader, constraintsHeader);
        return headerRow;
    }

    private HBox createColumnDataRow(ColumnData column, boolean alternate) {
        HBox dataRow = new HBox();
        dataRow.setAlignment(Pos.CENTER_LEFT);
        dataRow.setPadding(new Insets(10, 15, 10, 15));
        dataRow.setSpacing(0);

        String bgColor = alternate ? "-color-accent-9-alpha10" : "transparent";
        dataRow.setStyle("""
                -fx-background-color: %s;
                -fx-border-width: 0 0 1px 0;
                -fx-border-color: -color-accent-9-alpha30;
                """.formatted(bgColor));

        HBox nameBox = new HBox(6);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        nameBox.setPrefWidth(250);
        nameBox.setMinWidth(250);

        FontIcon columnIcon = new FontIcon(MaterialDesignT.TABLE_COLUMN);

        Label nameLabel = new Label(column.getColumnName());
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        nameBox.getChildren().addAll(columnIcon, nameLabel);

        Label typeLabel = new Label(column.getDataType());
        typeLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.8; -fx-font-family: 'Consolas', 'Monaco', monospace;");
        typeLabel.setPrefWidth(250);
        typeLabel.setMinWidth(250);

        HBox constraintsBox = new HBox(6);
        constraintsBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(constraintsBox, Priority.ALWAYS);

        if (column.isPrimaryKey()) {
            Label badge = createConstraintBadge("PK", MaterialDesignK.KEY);
            constraintsBox.getChildren().add(badge);
        }

        if (column.isNotNull()) {
            Label badge = createConstraintBadge("NOT NULL", MaterialDesignC.CANCEL);
            constraintsBox.getChildren().add(badge);
        }

        if (column.isUnique()) {
            Label badge = createConstraintBadge("UNIQUE", MaterialDesignS.STAR_OUTLINE);
            constraintsBox.getChildren().add(badge);
        }

        if (column.isAutoIncrement()) {
            Label badge = createConstraintBadge("AUTO_INC", MaterialDesignN.NUMERIC);
            constraintsBox.getChildren().add(badge);
        }

        if (constraintsBox.getChildren().isEmpty()) {
            Label noneLabel = new Label("—");
            noneLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.4;");
            constraintsBox.getChildren().add(noneLabel);
        }

        dataRow.getChildren().addAll(nameBox, typeLabel, constraintsBox);
        return dataRow;
    }

    private Label createConstraintBadge(String text, Ikon icon) {
        Label badge = new Label(text);

        FontIcon iconGraphic = new FontIcon(icon);
        iconGraphic.setIconSize(11);

        badge.setGraphic(iconGraphic);
        badge.setGraphicTextGap(3);
        badge.setStyle("""
                -fx-font-size: 10px;
                -fx-opacity: 0.85;
                -fx-padding: 3 7 3 7;
                -fx-background-color: -color-accent-9-alpha40;
                -fx-background-radius: 3px;
                -fx-font-weight: 600;
                """);

        return badge;
    }

    private VBox createForeignKeysTab(Map<String, ForeignKeyData> foreignKeys) {
        VBox container = new VBox();
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(15));
        container.setStyle("-fx-background-color: -color-bg-default;");

        if (foreignKeys.isEmpty()) {
            Label emptyLabel = new Label("No foreign keys defined");
            emptyLabel.setStyle("-fx-opacity: 0.6; -fx-font-size: 14px;");
            container.getChildren().add(emptyLabel);
        } else {
            VBox tableContainer = new VBox();
            tableContainer.setStyle("""
                    -fx-background-color: -color-bg-default;
                    -fx-border-radius: 8px;
                    -fx-background-radius: 8px;
                    -fx-border-color: -color-accent-9;
                    -fx-border-width: 1px;
                    """);

            HBox headerRow = createForeignKeyHeaderRow();
            tableContainer.getChildren().add(headerRow);

            int index = 0;
            for (Map.Entry<String, ForeignKeyData> entry : foreignKeys.entrySet()) {
                HBox dataRow = createForeignKeyDataRow(entry.getValue(), index % 2 == 0);
                tableContainer.getChildren().add(dataRow);
                index++;
            }

            container.getChildren().add(tableContainer);
        }

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox wrapper = new VBox(scrollPane);
        wrapper.setPadding(new Insets(0, 0, 10, 0));
        return wrapper;
    }

    private HBox createForeignKeyHeaderRow() {
        HBox headerRow = new HBox();
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(12, 15, 12, 15));
        headerRow.setStyle("""
                -fx-background-color: -color-accent-9-alpha30;
                -fx-border-width: 0 0 1px 0;
                -fx-border-color: -color-accent-9;
                """);

        Label constraintHeader = new Label("Constraint Name");
        constraintHeader.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 0.8;");
        constraintHeader.setPrefWidth(180);
        constraintHeader.setMinWidth(180);

        Label localHeader = new Label("Local Column");
        localHeader.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 0.8;");
        localHeader.setPrefWidth(140);
        localHeader.setMinWidth(140);

        Label referencesHeader = new Label("References");
        referencesHeader.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 0.8;");
        referencesHeader.setPrefWidth(200);
        referencesHeader.setMinWidth(200);

        Label actionsHeader = new Label("Actions");
        actionsHeader.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 0.8;");
        HBox.setHgrow(actionsHeader, Priority.ALWAYS);

        headerRow.getChildren().addAll(constraintHeader, localHeader, referencesHeader, actionsHeader);
        return headerRow;
    }

    private HBox createForeignKeyDataRow(ForeignKeyData fk, boolean alternate) {
        HBox dataRow = new HBox();
        dataRow.setAlignment(Pos.CENTER_LEFT);
        dataRow.setPadding(new Insets(10, 15, 10, 15));
        dataRow.setSpacing(0);

        String bgColor = alternate ? "-color-accent-9-alpha10" : "transparent";
        dataRow.setStyle("""
                -fx-background-color: %s;
                -fx-border-width: 0 0 1px 0;
                -fx-border-color: -color-accent-9-alpha30;
                """.formatted(bgColor));

        HBox constraintBox = new HBox(6);
        constraintBox.setAlignment(Pos.CENTER_LEFT);
        constraintBox.setPrefWidth(180);
        constraintBox.setMinWidth(180);

        FontIcon keyIcon = new FontIcon(MaterialDesignL.LABEL_VARIANT_OUTLINE);

        Label constraintLabel = new Label(fk.getConstraintName());
        constraintLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        constraintLabel.setMaxWidth(150);

        constraintBox.getChildren().addAll(keyIcon, constraintLabel);

        Label localLabel = new Label(fk.getLocalColumn());
        localLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.8; -fx-font-family: 'Consolas', 'Monaco', monospace;");
        localLabel.setPrefWidth(140);
        localLabel.setMinWidth(140);

        HBox refBox = new HBox(4);
        refBox.setAlignment(Pos.CENTER_LEFT);
        refBox.setPrefWidth(200);
        refBox.setMinWidth(200);

        FontIcon linkIcon = new FontIcon(MaterialDesignL.LINK_VARIANT);

        Label refLabel = new Label(fk.getReferenceTable() + "." + fk.getReferenceColumn());
        refLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.8; -fx-font-family: 'Consolas', 'Monaco', monospace;");
        refLabel.setMaxWidth(180);

        refBox.getChildren().addAll(linkIcon, refLabel);

        HBox actionsBox = new HBox(6);
        actionsBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(actionsBox, Priority.ALWAYS);

        if (fk.getOnDelete() != null) {
            Label deleteAction = createActionBadge("DELETE: " + fk.getOnDelete(), Feather.TRASH_2);
            actionsBox.getChildren().add(deleteAction);
        }

        if (fk.getOnUpdate() != null) {
            Label updateAction = createActionBadge("UPDATE: " + fk.getOnUpdate(), MaterialDesignU.UPDATE);
            actionsBox.getChildren().add(updateAction);
        }

        if (actionsBox.getChildren().isEmpty()) {
            Label noneLabel = new Label("—");
            noneLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.4;");
            actionsBox.getChildren().add(noneLabel);
        }

        dataRow.getChildren().addAll(constraintBox, localLabel, refBox, actionsBox);
        return dataRow;
    }

    private Label createActionBadge(String text, Ikon icon) {
        Label badge = new Label(text);

        FontIcon iconGraphic = new FontIcon(icon);
        iconGraphic.setIconSize(10);

        badge.setGraphic(iconGraphic);
        badge.setGraphicTextGap(3);
        badge.setStyle("""
                -fx-font-size: 10px;
                -fx-opacity: 0.85;
                -fx-padding: 3 7 3 7;
                -fx-background-color: -color-accent-9-alpha40;
                -fx-background-radius: 3px;
                -fx-font-weight: 600;
                """);

        return badge;
    }

    private VBox createPropertiesTab(int columnCount, int foreignKeyCount) {
        VBox container = new VBox();
        container.setPadding(new Insets(15));
        container.setSpacing(15);
        container.setStyle("-fx-background-color: -color-bg-default;");

        VBox generalSection = createPropertySection("General Information", new FontIcon(MaterialDesignI.INFORMATION_OUTLINE));

        GridPane generalGrid = new GridPane();
        generalGrid.setHgap(20);
        generalGrid.setVgap(12);
        generalGrid.setPadding(new Insets(15));

        addPropertyRow(generalGrid, 0, "Database:", databaseName);
        addPropertyRow(generalGrid, 1, "Table Name:", tableName);
        addPropertyRow(generalGrid, 2, "Column Count:", String.valueOf(columnCount));
        addPropertyRow(generalGrid, 3, "Foreign Keys:", String.valueOf(foreignKeyCount));

        generalSection.getChildren().add(generalGrid);

        VBox statsSection = createPropertySection("Statistics",
                new FontIcon(MaterialDesignC.CHART_BAR));

        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(20);
        statsGrid.setVgap(12);
        statsGrid.setPadding(new Insets(15));

        try {
            double tableSizeInMB = DatabaseConnection.getInstance()
                    .getConnectionObject()
                    .getTableSize(databaseName, tableName);

            String formattedSize = String.format("%.2f MB", tableSizeInMB);
            addPropertyRow(statsGrid, 0, "Table Size:", formattedSize);
        } catch (SQLException e) {
            addPropertyRow(statsGrid, 0, "Table Size:", "N/A (Error retrieving)");
            log.error("createPropertiesTab failed", e);
        }

        statsSection.getChildren().add(statsGrid);

        container.getChildren().addAll(generalSection, statsSection);

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox wrapper = new VBox(scrollPane);
        wrapper.setPadding(new Insets(0, 0, 10, 0));
        return wrapper;
    }

    private VBox createPropertySection(String title, FontIcon icon) {
        VBox section = new VBox();
        section.setSpacing(0);
        section.setStyle("""
                -fx-background-color: -color-bg-default;
                -fx-background-radius: 8px;
                -fx-border-radius: 8px;
                -fx-border-color: -color-accent-9;
                -fx-border-width: 1px;
                """);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 15 15 10 15;");
        titleLabel.setGraphic(icon);

        section.getChildren().add(titleLabel);
        return section;
    }

    private void addPropertyRow(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-opacity: 0.7; -fx-font-size: 13px; -fx-min-width: 120;");

        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }
}