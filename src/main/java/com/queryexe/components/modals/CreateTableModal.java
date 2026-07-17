package com.queryexe.components.modals;

import lombok.extern.slf4j.Slf4j;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import org.kordamp.ikonli.materialdesign2.MaterialDesignU;
import atlantafx.base.theme.Styles;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.model.data.ColumnData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.model.data.ForeignKeyData;
import com.queryexe.queryexe.App;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CreateTableModal extends VBox {

    private ObservableList<String> localColumnsList;
    private VBox columnsContainerBox;
    private VBox foreignKeysContainerBox;
    private StackPane contentPane;
    private HBox tabHeaders;
    private VBox columnsTab;
    private VBox foreignKeysTab;
    private Button addButton;
    private Runnable refreshTables;
    private String databaseName;
    private TextField tableNameField;
    private boolean isEditMode = false;
    private String originalTableName;

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

    private List<ColumnDataComponents> columnDataList = new ArrayList<>();
    private List<ForeignKeyComponents> foreignKeyDataList = new ArrayList<>();

    private static class ColumnDataComponents {
        TextField nameField;
        ComboBox<String> dataTypeCombo;
        CheckBox primaryKeyCheck;
        CheckBox notNullCheck;
        CheckBox uniqueCheck;
        CheckBox autoIncrementCheck;
        String originalName;

        ColumnDataComponents(TextField nameField, ComboBox<String> dataTypeCombo,
                             CheckBox primaryKeyCheck, CheckBox notNullCheck,
                             CheckBox uniqueCheck, CheckBox autoIncrementCheck) {
            this.nameField = nameField;
            this.dataTypeCombo = dataTypeCombo;
            this.primaryKeyCheck = primaryKeyCheck;
            this.notNullCheck = notNullCheck;
            this.uniqueCheck = uniqueCheck;
            this.autoIncrementCheck = autoIncrementCheck;
        }
    }

    private static class ForeignKeyComponents {
        TextField constraintNameField;
        ComboBox<String> localColumnCombo;
        ComboBox<String> referenceTableCombo;
        ComboBox<ColumnData> referenceColumnCombo;
        ComboBox<String> onDeleteCombo;
        ComboBox<String> onUpdateCombo;
        TitledPane pane;

        ForeignKeyComponents(TextField constraintNameField, ComboBox<String> localColumnCombo,
                             ComboBox<String> referenceTableCombo, ComboBox<ColumnData> referenceColumnCombo,
                             ComboBox<String> onDeleteCombo, ComboBox<String> onUpdateCombo, TitledPane pane) {
            this.constraintNameField = constraintNameField;
            this.localColumnCombo = localColumnCombo;
            this.referenceTableCombo = referenceTableCombo;
            this.referenceColumnCombo = referenceColumnCombo;
            this.onDeleteCombo = onDeleteCombo;
            this.onUpdateCombo = onUpdateCombo;
            this.pane = pane;
        }
    }

    public CreateTableModal(String databaseName, Runnable refreshTables) {
        this.databaseName = databaseName;
        this.refreshTables = refreshTables;
        this.localColumnsList = FXCollections.observableArrayList();
        this.isEditMode = false;
        initializeUI();
    }

    public CreateTableModal(String databaseName, String tableName, Runnable refreshTables) {
        this.databaseName = databaseName;
        this.originalTableName = tableName;
        this.refreshTables = refreshTables;
        this.localColumnsList = FXCollections.observableArrayList();
        this.isEditMode = true;
        initializeUI();
        loadTableData(tableName);
    }

    private void initializeUI() {
        this.getStyleClass().add("modal-container");
        this.setMaxSize(950, 550);
        this.setMinSize(950, 550);
        this.setPrefSize(950, 550);

        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_RIGHT);
        headerBox.setPadding(new Insets(20, 10, 0, 10));
        headerBox.setMinHeight(40);
        headerBox.setMaxHeight(40);

        Region headerFillerRegion = new Region();
        Region headerSecondFillerRegion = new Region();

        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(event -> {
            App.closeModal();
        });

        Button fakeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        fakeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        fakeButton.setVisible(false);

        HBox.setHgrow(headerFillerRegion, Priority.ALWAYS);
        HBox.setHgrow(headerSecondFillerRegion, Priority.ALWAYS);

        tableNameField = new TextField();
        tableNameField.setAlignment(Pos.CENTER);
        tableNameField.setPromptText("Table Name");
        tableNameField.setMinWidth(400);
        tableNameField.setMaxWidth(400);

        headerBox.getChildren().addAll(fakeButton, headerFillerRegion, tableNameField, headerSecondFillerRegion, closeButton);

        columnsContainerBox = new VBox();
        columnsContainerBox.setPadding(new Insets(10, 20, 10, 20));
        columnsContainerBox.setAlignment(Pos.TOP_CENTER);
        columnsContainerBox.setSpacing(10);

        if (!isEditMode) {
            columnsContainerBox.getChildren().add(createRow());
        }

        ScrollPane scrollPane = new ScrollPane(columnsContainerBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        tabHeaders = new HBox();
        tabHeaders.setAlignment(Pos.CENTER);
        tabHeaders.setSpacing(5);
        tabHeaders.setPadding(new Insets(20, 5, 0, 5));

        contentPane = new StackPane();
        contentPane.setStyle("-fx-border-width: 0 0 2px 0; -fx-border-color: -color-border-default;");

        columnsTab = new VBox(scrollPane);
        columnsTab.setPadding(new Insets(10, 0, 0, 0));

        initializeForeignKeysContent();

        contentPane.getChildren().add(columnsTab);

        addButton = new Button("Add", new FontIcon(MaterialDesignP.PLUS));
        addButton.setFocusTraversable(false);
        addButton.getStyleClass().add("hover-opac");
        addButton.setStyle("""
                -fx-background-color: transparent;
                -fx-border-color: #666666;
                -fx-border-width: 1px;
                -fx-background-radius: 6px;
                -fx-border-radius: 6px;
                """);

        Button columnsTabButton = createTabHeader("Columns", columnsTab, new FontIcon(MaterialDesignT.TABLE_COLUMN),
                () -> {
                    columnsContainerBox.getChildren().add(createRow());
                }
        );
        columnsTabButton.setStyle(activeTab);

        Button foreignKeysTabButton = createTabHeader("Foreign Keys", foreignKeysTab, new FontIcon(MaterialDesignT.TABLE_KEY), this::addNewForeignKey);
        foreignKeysTabButton.setStyle(inactiveTab);

        tabHeaders.getChildren().addAll(columnsTabButton, foreignKeysTabButton);

        VBox.setVgrow(contentPane, Priority.ALWAYS);

        HBox buttonsBox = new HBox();
        buttonsBox.setAlignment(Pos.BOTTOM_RIGHT);
        buttonsBox.setSpacing(10);
        buttonsBox.setPadding(new Insets(10, 20, 10, 20));

        Button confirmButton = new Button(isEditMode ? "Update Table" : "Create Table");
        confirmButton.setPrefWidth(125);
        confirmButton.setDefaultButton(true);
        confirmButton.setOnAction(event -> {
            handleCreateTable(tableNameField.getText());
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(125);
        cancelButton.setOnAction(event -> {
            App.closeModal();
        });

        addButton.setOnAction(e -> {
            columnsContainerBox.getChildren().add(createRow());
        });

        Region buttonsBoxBufferRegion = new Region();
        HBox.setHgrow(buttonsBoxBufferRegion, Priority.ALWAYS);

        buttonsBox.getChildren().addAll(addButton, buttonsBoxBufferRegion, confirmButton, cancelButton);

        this.getChildren().addAll(headerBox, tabHeaders, contentPane, buttonsBox);
    }

    private void loadTableData(String tableName) {
        try {
            tableNameField.setText(tableName);

            ArrayList<ColumnData> columns = DatabaseConnection.getInstance().getConnectionObject().getColumnsForTable(databaseName, tableName);

            for (ColumnData column : columns) {
                Node rowNode = createRow(column);
                columnsContainerBox.getChildren().add(rowNode);
            }

            Map<String, ForeignKeyData> foreignKeys = DatabaseConnection.getInstance().getConnectionObject().extractForeignKeys(databaseName, tableName);

            if (!foreignKeys.isEmpty()) {
                Accordion accordion = findAccordion();
                if (accordion != null) {
                    accordion.getPanes().clear();
                    foreignKeyDataList.clear();
                }

                int index = 1;
                for (Map.Entry<String, ForeignKeyData> entry : foreignKeys.entrySet()) {
                    ForeignKeyData fkData = entry.getValue();
                    TitledPane fkPane = createForeignKeyTitledPane(index++, fkData);
                    if (accordion != null) {
                        accordion.getPanes().add(fkPane);
                    }
                }
            }

        } catch (SQLException e) {
            CustomNotification notification = new CustomNotification("Load Failed", "Could not load table data: " + e.getMessage(), new FontIcon(MaterialDesignT.TABLE_CANCEL));
            notification.showNotificationOnCustomPane((StackPane) this.getParent());
            log.error("loadTableData failed", e);
        }
    }

    private Accordion findAccordion() {
        for (Node node : foreignKeysContainerBox.getChildren()) {
            if (node instanceof Accordion) {
                return (Accordion) node;
            }
        }
        return null;
    }

    private Button createTabHeader(String title, Node content, FontIcon icon, Runnable function) {
        Button tabHeader = new Button(title, icon);
        tabHeader.setMinWidth(935 / 2);
        tabHeader.setMaxWidth(935 / 2);

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

            addButton.setOnAction(event -> {
                function.run();
            });
        });
        return tabHeader;
    }

    public Node createRow() {
        return createRow(null);
    }

    public Node createRow(ColumnData columnData) {
        HBox rowBox = new HBox();
        rowBox.setSpacing(15);
        rowBox.setAlignment(Pos.CENTER_LEFT);
        rowBox.setStyle("""
                -fx-background-color: -color-bg-default;
                -fx-background-radius: 8px;
                -fx-border-radius: 8px;
                -fx-border-color: #44475a;
                -fx-border-width: 1px;
                -fx-padding: 10px;
                """);

        TextField parameterField = new TextField();
        parameterField.setPromptText("Column name");
        parameterField.setMinWidth(250);
        parameterField.setMaxWidth(250);

        String columnEntry = columnData != null ? columnData.getColumnName() : "";
        if (columnData != null) {
            parameterField.setText(columnData.getColumnName());
        }

        localColumnsList.add(columnEntry);
        final int columnIndex = localColumnsList.size() - 1;

        parameterField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (columnIndex < localColumnsList.size()) {
                localColumnsList.set(columnIndex, newValue != null ? newValue : "");
            }
        });

        ComboBox<String> dataTypes = new ComboBox<>();
        dataTypes.setPromptText("Type");
        dataTypes.setMinWidth(250);
        dataTypes.setMaxWidth(250);

        ObservableList<String> dataTypesList = FXCollections.observableArrayList(
                DatabaseConnection.getInstance().getConnectionObject().getDataTypes()
        );
        dataTypesList.sort(String::compareToIgnoreCase);

        dataTypes.setItems(dataTypesList);
        dataTypes.setEditable(true);

        if (columnData != null) {
            dataTypes.setValue(columnData.getDataType());
        }

        CheckBox primaryKeyCheck = new CheckBox("PK");
        primaryKeyCheck.setTooltip(new Tooltip("Primary Key"));
        if (columnData != null) {
            primaryKeyCheck.setSelected(columnData.isPrimaryKey());
        }

        CheckBox isNullCheck = new CheckBox("NN");
        isNullCheck.setTooltip(new Tooltip("Not NULL"));
        if (columnData != null) {
            isNullCheck.setSelected(columnData.isNotNull());
        }

        CheckBox uniqueCheck = new CheckBox("UQ");
        uniqueCheck.setTooltip(new Tooltip("Unique"));
        if (columnData != null) {
            uniqueCheck.setSelected(columnData.isUnique());
        }

        CheckBox autoIncrementCheck = new CheckBox("AI");
        autoIncrementCheck.setTooltip(new Tooltip("Auto Increment"));
        if (columnData != null) {
            autoIncrementCheck.setSelected(columnData.isAutoIncrement());
        }

        ColumnDataComponents components = new ColumnDataComponents(
                parameterField, dataTypes, primaryKeyCheck, isNullCheck, uniqueCheck, autoIncrementCheck
        );
        components.originalName = columnData != null ? columnData.getColumnName() : null;
        columnDataList.add(components);

        Button deleteButton = new Button(null, new FontIcon(Feather.TRASH_2));
        deleteButton.setFocusTraversable(false);
        deleteButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);

        deleteButton.setOnAction(e -> {
            if (columnIndex < localColumnsList.size()) {
                localColumnsList.remove(columnIndex);
            }
            columnDataList.remove(components);
            columnsContainerBox.getChildren().remove(rowBox);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        rowBox.getChildren().addAll(
                parameterField,
                dataTypes,
                primaryKeyCheck,
                isNullCheck,
                uniqueCheck,
                autoIncrementCheck,
                spacer,
                deleteButton
        );

        return rowBox;
    }

    private void initializeForeignKeysContent() {
        foreignKeysContainerBox = new VBox();
        foreignKeysContainerBox.setPadding(new Insets(15, 25, 15, 25));
        foreignKeysContainerBox.setAlignment(Pos.TOP_CENTER);
        foreignKeysContainerBox.setSpacing(15);
        foreignKeysContainerBox.setStyle("-fx-background-color: -color-bg-default;");

        Accordion foreignKeysAccordion = new Accordion();
        foreignKeysAccordion.setPrefWidth(880);
        foreignKeysAccordion.setStyle("""
                -fx-background-color: transparent;
                -fx-border-color: transparent;
                -fx-font-size: 14px;
                """);

        if (!isEditMode) {
            TitledPane firstForeignKey = createForeignKeyTitledPane(1);
            foreignKeysAccordion.getPanes().add(firstForeignKey);
            foreignKeysAccordion.setExpandedPane(firstForeignKey);
        }

        foreignKeysContainerBox.getChildren().addAll(foreignKeysAccordion);

        ScrollPane scrollPane = new ScrollPane(foreignKeysContainerBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        foreignKeysTab = new VBox(scrollPane);
        foreignKeysTab.setStyle("-fx-background-color: -color-bg-default;");
    }

    private TitledPane createForeignKeyTitledPane(int index) {
        return createForeignKeyTitledPane(index, null);
    }

    private TitledPane createForeignKeyTitledPane(int index, ForeignKeyData fkData) {
        VBox content = new VBox();
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(0, 0, 20, 0));

        GridPane mainGrid = new GridPane();
        mainGrid.setHgap(10);
        mainGrid.setVgap(10);
        mainGrid.setAlignment(Pos.CENTER);

        Label nameLabel = new Label("Constraint Name");
        nameLabel.setGraphic(new FontIcon(MaterialDesignL.LABEL_VARIANT_OUTLINE));

        TextField constraintNameField = new TextField();
        constraintNameField.setPromptText("Auto-generated if empty");
        if (fkData != null) {
            constraintNameField.setText(fkData.getConstraintName());
        }

        Label refTableLabel = new Label("References Table");
        refTableLabel.setGraphic(new FontIcon(MaterialDesignT.TABLE));

        ComboBox<String> referenceTables = new ComboBox<>();
        referenceTables.setPromptText("Select table");
        referenceTables.setMaxWidth(Double.MAX_VALUE);

        ObservableList<String> tablesList = null;
        try {
            tablesList = FXCollections.observableArrayList(DatabaseConnection.getInstance().getConnectionObject().getTablesForDatabase(this.databaseName));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        referenceTables.setItems(tablesList);
        if (fkData != null) {
            referenceTables.setValue(fkData.getReferenceTable());
        }

        Label localColumnLabel = new Label("Local Column");
        localColumnLabel.setGraphic(new FontIcon(MaterialDesignT.TABLE_COLUMN));

        ComboBox<String> localColumn = new ComboBox<>();
        localColumn.setPromptText("Select column");
        localColumn.setMaxWidth(Double.MAX_VALUE);
        localColumn.setItems(localColumnsList);
        if (fkData != null) {
            localColumn.setValue(fkData.getLocalColumn());
        }

        Label foreignColumnLabel = new Label("Referenced Column");
        foreignColumnLabel.setGraphic(new FontIcon(MaterialDesignL.LINK_VARIANT));

        ComboBox<ColumnData> foreignColumn = new ComboBox<>();
        foreignColumn.setPromptText("Select column");
        foreignColumn.setMaxWidth(Double.MAX_VALUE);

        referenceTables.setOnAction(event -> {
            String tableName = referenceTables.getSelectionModel().getSelectedItem();
            if (tableName != null) {
                ArrayList<ColumnData> columns = null;
                try {
                    columns = DatabaseConnection.getInstance().getConnectionObject().getColumnsForTable(this.databaseName, tableName);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                foreignColumn.setItems(FXCollections.observableArrayList(columns));
            }
        });

        if (fkData != null && fkData.getReferenceTable() != null) {
            ArrayList<ColumnData> columns = null;
            try {
                columns = DatabaseConnection.getInstance().getConnectionObject().getColumnsForTable(this.databaseName, fkData.getReferenceTable());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            foreignColumn.setItems(FXCollections.observableArrayList(columns));

            for (ColumnData col : columns) {
                if (col.getColumnName().equals(fkData.getReferenceColumn())) {
                    foreignColumn.setValue(col);
                    break;
                }
            }
        }

        Label onDeleteLabel = new Label("On Delete");
        onDeleteLabel.setGraphic(new FontIcon(Feather.TRASH_2));

        ComboBox<String> onDeleteAction = new ComboBox<>();
        onDeleteAction.setItems(FXCollections.observableArrayList("NO ACTION", "RESTRICT", "CASCADE", "SET NULL", "SET DEFAULT"));
        onDeleteAction.setPromptText("Choose action");
        onDeleteAction.setMaxWidth(Double.MAX_VALUE);
        if (fkData != null && fkData.getOnDelete() != null) {
            onDeleteAction.setValue(fkData.getOnDelete());
        }

        Label onUpdateLabel = new Label("On Update");
        onUpdateLabel.setGraphic(new FontIcon(MaterialDesignU.UPDATE));

        ComboBox<String> onUpdateAction = new ComboBox<>();
        onUpdateAction.setItems(FXCollections.observableArrayList("NO ACTION", "RESTRICT", "CASCADE", "SET NULL", "SET DEFAULT"));
        onUpdateAction.setPromptText("Choose action");
        onUpdateAction.setMaxWidth(Double.MAX_VALUE);
        if (fkData != null && fkData.getOnUpdate() != null) {
            onUpdateAction.setValue(fkData.getOnUpdate());
        }

        mainGrid.add(nameLabel, 0, 0);
        mainGrid.add(constraintNameField, 0, 1);
        mainGrid.add(localColumnLabel, 0, 2);
        mainGrid.add(localColumn, 0, 3);
        mainGrid.add(onDeleteLabel, 0, 4);
        mainGrid.add(onDeleteAction, 0, 5);

        mainGrid.add(refTableLabel, 1, 0);
        mainGrid.add(referenceTables, 1, 1);
        mainGrid.add(foreignColumnLabel, 1, 2);
        mainGrid.add(foreignColumn, 1, 3);
        mainGrid.add(onUpdateLabel, 1, 4);
        mainGrid.add(onUpdateAction, 1, 5);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        col1.setHgrow(Priority.ALWAYS);

        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        col2.setHgrow(Priority.ALWAYS);

        mainGrid.getColumnConstraints().addAll(col1, col2);

        content.getChildren().add(mainGrid);

        TitledPane foreignKeyPane = new TitledPane(null, content);

        ForeignKeyComponents fkComponents = new ForeignKeyComponents(
                constraintNameField, localColumn, referenceTables, foreignColumn,
                onDeleteAction, onUpdateAction, foreignKeyPane
        );
        foreignKeyDataList.add(fkComponents);

        HBox titlePaneHeaderBox = new HBox();
        titlePaneHeaderBox.setAlignment(Pos.CENTER);
        titlePaneHeaderBox.setPrefWidth(830);
        Label titlePaneHeaderLabel = new Label("Foreign Key");
        Region titlePaneHeaderFiller = new Region();
        HBox.setHgrow(titlePaneHeaderFiller, Priority.ALWAYS);
        Button titlePaneHeaderButton = new Button(null, new FontIcon(Feather.TRASH_2));
        titlePaneHeaderButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
        titlePaneHeaderButton.setOnAction(event -> {
            Accordion accordion = null;
            for (Node node : foreignKeysContainerBox.getChildren()) {
                if (node instanceof Accordion) {
                    accordion = (Accordion) node;
                    break;
                }
            }
            if (accordion != null) {
                accordion.getPanes().remove(foreignKeyPane);
                foreignKeyDataList.remove(fkComponents);
            }
        });
        titlePaneHeaderBox.getChildren().addAll(titlePaneHeaderLabel, titlePaneHeaderFiller, titlePaneHeaderButton);
        foreignKeyPane.setGraphic(titlePaneHeaderBox);

        return foreignKeyPane;
    }

    private void handleCreateTable(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            CustomNotification notification = new CustomNotification("Invalid Table", "The table name cannot be empty.", new FontIcon(MaterialDesignT.TABLE_CANCEL));
            notification.showNotificationOnCustomPane((StackPane) this.getParent());
            return;
        }

        List<ColumnData> columns = new ArrayList<>();
        for (ColumnDataComponents comp : columnDataList) {
            String name = comp.nameField.getText();
            String dataType = comp.dataTypeCombo.getValue();

            if (name == null || name.trim().isEmpty()) {
                CustomNotification notification = new CustomNotification("Invalid Column", "A column name cannot be empty.", new FontIcon(MaterialDesignT.TABLE_CANCEL));
                notification.showNotificationOnCustomPane((StackPane) this.getParent());
                return;
            }

            if (dataType == null || dataType.trim().isEmpty()) {
                CustomNotification notification = new CustomNotification("Invalid Column", "The data type for column '" + name + "' cannot be empty.", new FontIcon(MaterialDesignT.TABLE_CANCEL));
                notification.showNotificationOnCustomPane((StackPane) this.getParent());
                return;
            }

            columns.add(new ColumnData(
                    name.trim(),
                    dataType.trim(),
                    comp.primaryKeyCheck.isSelected(),
                    comp.notNullCheck.isSelected(),
                    comp.uniqueCheck.isSelected(),
                    comp.autoIncrementCheck.isSelected()
            ));
        }

        if (columns.isEmpty()) {
            CustomNotification notification = new CustomNotification("Invalid Table", "At least one column is required.", new FontIcon(MaterialDesignT.TABLE_CANCEL));
            notification.showNotificationOnCustomPane((StackPane) this.getParent());
            return;
        }

        List<ForeignKeyData> foreignKeys = new ArrayList<>();
        for (ForeignKeyComponents fkComp : foreignKeyDataList) {
            String localCol = fkComp.localColumnCombo.getValue();
            String refTable = fkComp.referenceTableCombo.getValue();
            ColumnData refCol = fkComp.referenceColumnCombo.getValue();

            if (localCol == null || refTable == null || refCol == null) {
                continue;
            }

            String constraintName = fkComp.constraintNameField.getText();
            if (constraintName == null || constraintName.trim().isEmpty()) {
                constraintName = "fk_" + tableName + "_" + localCol;
            }

            foreignKeys.add(new ForeignKeyData(
                    constraintName.trim(),
                    localCol,
                    refTable,
                    refCol.getColumnName(),
                    fkComp.onDeleteCombo.getValue(),
                    fkComp.onUpdateCombo.getValue()
            ));
        }

        String sql;
        Map<String, String> columnRenames = new HashMap<>();
        if (isEditMode) {
            for (ColumnDataComponents comp : columnDataList) {
                String currentName = comp.nameField.getText().trim();
                if (comp.originalName != null && !comp.originalName.equals(currentName)) {
                    columnRenames.put(comp.originalName, currentName);
                }
            }
            try {
                ArrayList<ColumnData> oldColumns = DatabaseConnection.getInstance().getConnectionObject().getColumnsForTable(databaseName, originalTableName);

                Map<String, ForeignKeyData> oldForeignKeys = DatabaseConnection.getInstance().getConnectionObject().extractForeignKeys(databaseName, originalTableName);

                sql = DatabaseConnection.getInstance().getConnectionObject()
                        .generateAlterTableSQL(databaseName, originalTableName, tableName.trim(),
                                oldColumns, columns, oldForeignKeys, foreignKeys, columnRenames);
            } catch (SQLException e) {
                CustomNotification notification = new CustomNotification("Script Generation Failed", "Could not generate the ALTER script: " + e.getMessage(), new FontIcon(MaterialDesignT.TABLE_CANCEL));
                notification.showNotificationOnCustomPane((StackPane) this.getParent());
                return;
            }
        } else {
            sql = DatabaseConnection.getInstance().getConnectionObject().generateCreateTableSQL(this.databaseName, tableName.trim(), columns, foreignKeys);
        }

        App.showModalOnTop(new SQLEditorModal(sql, refreshTables));
    }

    private void addNewForeignKey() {
        Accordion accordion = null;
        for (Node node : foreignKeysContainerBox.getChildren()) {
            if (node instanceof Accordion) {
                accordion = (Accordion) node;
                break;
            }
        }

        if (accordion != null) {
            int newIndex = accordion.getPanes().size() + 1;
            TitledPane newForeignKey = createForeignKeyTitledPane(newIndex);
            accordion.getPanes().add(newForeignKey);
            accordion.setExpandedPane(newForeignKey);
        }
    }
}