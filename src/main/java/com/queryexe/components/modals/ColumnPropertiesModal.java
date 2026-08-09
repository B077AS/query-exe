package com.queryexe.components.modals;

import lombok.extern.slf4j.Slf4j;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;
import atlantafx.base.theme.Styles;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.model.data.DetailedColumnData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.queryexe.App;

import java.sql.SQLException;
import java.util.Map;

@Slf4j
public class ColumnPropertiesModal extends VBox {

    private String databaseName;
    private String tableName;
    private String columnName;
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

    public ColumnPropertiesModal(String databaseName, String tableName, String columnName) {
        this.databaseName = databaseName;
        this.tableName = tableName;
        this.columnName = columnName;
        initializeUI();
        loadColumnData();
    }

    private void initializeUI() {
        this.getStyleClass().add("modal-container");
        this.setMaxSize(800, 550);
        this.setMinSize(800, 550);
        this.setPrefSize(800, 550);

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

    private void loadColumnData() {
        try {

            DetailedColumnData columnInfo = DatabaseConnection.getInstance().getCurrentConnectionObject().getDetailedColumnInfo(databaseName, tableName, columnName);

            VBox detailsTab = createDetailsTab(columnInfo);
            VBox relationshipsTab = createRelationshipsTab(columnInfo);

            Button detailsButton = createTabHeader("Details", detailsTab, new FontIcon(MaterialDesignI.INFORMATION_OUTLINE));
            Button relationshipsButton = createTabHeader("Relationships", relationshipsTab, new FontIcon(MaterialDesignL.LINK_VARIANT));

            detailsButton.setStyle(activeTab);
            relationshipsButton.setStyle(inactiveTab);

            tabHeaders.getChildren().addAll(detailsButton, relationshipsButton);
            contentPane.getChildren().add(detailsTab);

        } catch (SQLException e) {

            Platform.runLater(() -> {
                App.closeModal();
            });

            CustomNotification notification = new CustomNotification("Load Failed", "Could not load column data: " + e.getMessage(), new FontIcon(MaterialDesignT.TABLE_CANCEL));
            notification.showNotification();
            log.error("loadColumnData failed", e);
        }
    }

    private Button createTabHeader(String title, javafx.scene.Node content, FontIcon icon) {
        Button tabHeader = new Button(title, icon);
        tabHeader.setMinWidth(785 / 2);
        tabHeader.setMaxWidth(785 / 2);
        HBox.setHgrow(tabHeader, Priority.ALWAYS);

        tabHeader.setOnAction(e -> {
            contentPane.getChildren().clear();
            contentPane.getChildren().add(content);
            for (javafx.scene.Node node : tabHeaders.getChildren()) {
                if (node instanceof Button) {
                    ((Button) node).setStyle(inactiveTab);
                }
            }
            tabHeader.setStyle(activeTab);
        });
        return tabHeader;
    }

    private VBox createDetailsTab(DetailedColumnData info) {
        VBox container = new VBox();
        container.setPadding(new Insets(15));
        container.setSpacing(15);
        container.setStyle("-fx-background-color: -color-bg-default;");

        VBox basicSection = createSection("Basic Information", new FontIcon(MaterialDesignT.TABLE_COLUMN));
        GridPane basicGrid = new GridPane();
        basicGrid.setHgap(20);
        basicGrid.setVgap(12);
        basicGrid.setPadding(new Insets(15));

        addPropertyRow(basicGrid, 0, "Column Name:", info.getColumnName());
        addPropertyRow(basicGrid, 1, "Table:", info.getTableName());
        addPropertyRow(basicGrid, 2, "Database:", info.getSchemaName());
        addPropertyRow(basicGrid, 3, "Ordinal Position:", String.valueOf(info.getOrdinalPosition()));

        basicSection.getChildren().add(basicGrid);

        VBox dataTypeSection = createSection("Data Type", new FontIcon(MaterialDesignF.FORMAT_TEXT));
        GridPane dataTypeGrid = new GridPane();
        dataTypeGrid.setHgap(20);
        dataTypeGrid.setVgap(12);
        dataTypeGrid.setPadding(new Insets(15));

        addPropertyRow(dataTypeGrid, 0, "Data Type:", info.getDataType());
        addPropertyRow(dataTypeGrid, 1, "Column Type:", info.getColumnType());

        if (info.getCharacterMaximumLength() != null) {
            addPropertyRow(dataTypeGrid, 2, "Max Length:", String.valueOf(info.getCharacterMaximumLength()));
        }
        if (info.getNumericPrecision() != null) {
            addPropertyRow(dataTypeGrid, 3, "Numeric Precision:", String.valueOf(info.getNumericPrecision()));
        }
        if (info.getNumericScale() != null) {
            addPropertyRow(dataTypeGrid, 4, "Numeric Scale:", String.valueOf(info.getNumericScale()));
        }
        if (info.getCharacterSetName() != null) {
            addPropertyRow(dataTypeGrid, 5, "Character Set:", info.getCharacterSetName());
        }
        if (info.getCollationName() != null) {
            addPropertyRow(dataTypeGrid, 6, "Collation:", info.getCollationName());
        }

        dataTypeSection.getChildren().add(dataTypeGrid);

        VBox constraintsSection = createSection("Constraints", new FontIcon(MaterialDesignS.SHIELD_CHECK));
        GridPane constraintsGrid = new GridPane();
        constraintsGrid.setHgap(20);
        constraintsGrid.setVgap(12);
        constraintsGrid.setPadding(new Insets(15));

        addPropertyRow(constraintsGrid, 0, "Primary Key:", info.isPrimaryKey() ? "Yes" : "No");
        addPropertyRow(constraintsGrid, 1, "Nullable:", info.isNullable() ? "Yes" : "No");
        addPropertyRow(constraintsGrid, 2, "Unique:", info.isUnique() ? "Yes" : "No");
        addPropertyRow(constraintsGrid, 3, "Auto Increment:", info.isAutoIncrement() ? "Yes" : "No");

        if (info.getColumnDefault() != null) {
            addPropertyRow(constraintsGrid, 4, "Default Value:", info.getColumnDefault());
        }

        if (info.getColumnComment() != null && !info.getColumnComment().isEmpty()) {
            addPropertyRow(constraintsGrid, 5, "Comment:", info.getColumnComment());
        }

        constraintsSection.getChildren().add(constraintsGrid);

        container.getChildren().addAll(basicSection, dataTypeSection, constraintsSection);

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox wrapper = new VBox(scrollPane);
        wrapper.setPadding(new Insets(0, 0, 10, 0));
        return wrapper;
    }

    private VBox createRelationshipsTab(DetailedColumnData info) {
        VBox container = new VBox();
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(15));
        container.setSpacing(15);
        container.setStyle("-fx-background-color: -color-bg-default;");

        if (info.getIndexes() != null && !info.getIndexes().isEmpty()) {
            VBox indexSection = createSection("Indexes", new FontIcon(MaterialDesignK.KEY_VARIANT));
            VBox indexBox = new VBox(10);
            indexBox.setPadding(new Insets(15));

            for (Map<String, String> index : info.getIndexes()) {
                HBox indexRow = new HBox(10);
                indexRow.setAlignment(Pos.CENTER_LEFT);
                indexRow.setStyle("-fx-padding: 8 12; -fx-background-color: -color-accent-9-alpha20; -fx-background-radius: 6px;");

                Label indexName = new Label(index.get("INDEX_NAME"));
                indexName.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

                Label indexType = new Label(index.get("INDEX_TYPE").toUpperCase());
                indexType.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7; -fx-padding: 3 8; -fx-background-color: -color-accent-9-alpha30; -fx-background-radius: 3px;");

                Label nonUnique = new Label(index.get("NON_UNIQUE").equals("0") ? "UNIQUE" : "NON-UNIQUE");
                nonUnique.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7; -fx-padding: 3 8; -fx-background-color: -color-accent-9-alpha30; -fx-background-radius: 3px;");

                indexRow.getChildren().addAll(new FontIcon(MaterialDesignK.KEY), indexName, indexType, nonUnique);
                indexBox.getChildren().add(indexRow);
            }

            indexSection.getChildren().add(indexBox);
            container.getChildren().add(indexSection);
        }

        if (info.getForeignKeyReferences() != null && !info.getForeignKeyReferences().isEmpty()) {
            VBox fkSection = createSection("Foreign Key References", new FontIcon(MaterialDesignL.LINK_VARIANT));
            VBox fkBox = new VBox(10);
            fkBox.setPadding(new Insets(15));

            for (Map<String, String> fk : info.getForeignKeyReferences()) {
                HBox fkRow = new HBox(10);
                fkRow.setAlignment(Pos.CENTER_LEFT);
                fkRow.setStyle("-fx-padding: 10 12; -fx-background-color: -color-accent-9-alpha20; -fx-background-radius: 6px;");

                VBox fkDetails = new VBox(5);

                Label fkName = new Label(fk.get("CONSTRAINT_NAME"));
                fkName.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

                Label refInfo = new Label("→ " + fk.get("REFERENCED_TABLE_NAME") + "." + fk.get("REFERENCED_COLUMN_NAME"));
                refInfo.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7; -fx-font-family: 'Consolas', 'Monaco', monospace;");

                fkDetails.getChildren().addAll(fkName, refInfo);
                fkRow.getChildren().addAll(new FontIcon(MaterialDesignT.TABLE_KEY), fkDetails);
                fkBox.getChildren().add(fkRow);
            }

            fkSection.getChildren().add(fkBox);
            container.getChildren().add(fkSection);
        }

        if (info.getReferencedByForeignKeys() != null && !info.getReferencedByForeignKeys().isEmpty()) {
            VBox refBySection = createSection("Referenced By", new FontIcon(MaterialDesignT.TABLE_ARROW_LEFT));
            VBox refByBox = new VBox(10);
            refByBox.setPadding(new Insets(15));

            for (Map<String, String> refBy : info.getReferencedByForeignKeys()) {
                HBox refByRow = new HBox(10);
                refByRow.setAlignment(Pos.CENTER_LEFT);
                refByRow.setStyle("-fx-padding: 10 12; -fx-background-color: -color-accent-9-alpha20; -fx-background-radius: 6px;");

                VBox refByDetails = new VBox(5);

                Label tableName = new Label(refBy.get("TABLE_NAME") + "." + refBy.get("COLUMN_NAME"));
                tableName.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-font-family: 'Consolas', 'Monaco', monospace;");

                Label constraintName = new Label(refBy.get("CONSTRAINT_NAME"));
                constraintName.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

                refByDetails.getChildren().addAll(tableName, constraintName);
                refByRow.getChildren().addAll(new FontIcon(MaterialDesignT.TABLE_ARROW_RIGHT), refByDetails);
                refByBox.getChildren().add(refByRow);
            }

            refBySection.getChildren().add(refByBox);
            container.getChildren().add(refBySection);
        }

        if (container.getChildren().isEmpty()) {
            Label noRelationships = new Label("No relationships found");
            noRelationships.setStyle("-fx-opacity: 0.6; -fx-font-size: 14px;");
            container.getChildren().add(noRelationships);
        }

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox wrapper = new VBox(scrollPane);
        wrapper.setPadding(new Insets(0, 0, 10, 0));
        return wrapper;
    }

    private VBox createSection(String title, FontIcon icon) {
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
        labelNode.setStyle("-fx-opacity: 0.7; -fx-font-size: 13px; -fx-min-width: 140;");

        Label valueNode = new Label(value != null ? value : "—");
        valueNode.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        valueNode.setWrapText(true);
        valueNode.setMaxWidth(450);

        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }
}