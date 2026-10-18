package com.queryexe.components.modals;

import atlantafx.base.theme.Styles;
import com.queryexe.components.modals.shell.ModalUi;
import com.queryexe.components.modals.shell.SidebarModal;
import com.queryexe.components.modals.shell.SimpleTab;
import com.queryexe.model.data.ColumnData;
import com.queryexe.model.data.ForeignKeyData;
import com.queryexe.queryexe.App;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.service.SQLFormatterUtils;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Creates a table, or (edit mode) alters an existing one; both end in a SQL preview rather than running anything directly. */
@Slf4j
public class CreateTableModal extends SidebarModal {

    private static final List<String> REFERENTIAL_ACTIONS = List.of("NO ACTION", "RESTRICT", "CASCADE", "SET NULL", "SET DEFAULT");


    private final String databaseName;
    private final String originalTableName;
    private final boolean isEditMode;
    private final Runnable refreshTables;

    /** Names of the columns currently in the form, same order as {@link #columnRows}; feeds the foreign key "local column" choices. */
    private final ObservableList<String> localColumnsList = FXCollections.observableArrayList();
    private final ObservableList<ColumnRow> columnRows = FXCollections.observableArrayList();
    private final List<ForeignKeyRow> foreignKeyRows = new ArrayList<>();
    private final ObservableList<String> dataTypes = FXCollections.observableArrayList(
            DatabaseConnection.getInstance().getConnectionObject().getDataTypes()).sorted(String::compareToIgnoreCase);
    private final VBox columnsBox = new VBox();
    private final VBox columnsEmpty = ModalUi.emptyState(MaterialDesignT.TABLE_COLUMN, "No columns yet. Use “Add column” to create one.");

    private final TextField tableNameField = new TextField();
    private final VBox foreignKeysBox = new VBox(12);
    private final VBox foreignKeysEmpty = ModalUi.emptyState(MaterialDesignT.TABLE_KEY, "No foreign keys yet. Use “Add foreign key” to create one.");
    private List<String> tableChoices = List.of();

    /** A column as edited in the form; the table cells bind straight to these properties. */
    private static class ColumnRow {
        final StringProperty name = new SimpleStringProperty("");
        final StringProperty type = new SimpleStringProperty();
        final BooleanProperty primaryKey = new SimpleBooleanProperty();
        final BooleanProperty notNull = new SimpleBooleanProperty();
        final BooleanProperty unique = new SimpleBooleanProperty();
        final BooleanProperty autoIncrement = new SimpleBooleanProperty();
        String originalName;
        HBox node;
    }

    private static class ForeignKeyRow {
        VBox node;
        TextField constraintNameField;
        ComboBox<String> localColumnCombo;
        ComboBox<String> referenceTableCombo;
        ComboBox<ColumnData> referenceColumnCombo;
        ComboBox<String> onDeleteCombo;
        ComboBox<String> onUpdateCombo;
    }

    /** Create mode. */
    public CreateTableModal(String databaseName, Runnable refreshTables) {
        this(databaseName, null, refreshTables);
    }

    /** Edit mode when {@code tableName} is non-null. */
    public CreateTableModal(String databaseName, String tableName, Runnable refreshTables) {
        super(MaterialDesignT.TABLE_EDIT, tableName != null ? tableName : "New Table", databaseName,
                tableName != null ? "Alter table" : "Create table", 1100, 660);
        this.databaseName = databaseName;
        this.originalTableName = tableName;
        this.isEditMode = tableName != null;
        this.refreshTables = refreshTables;

        try {
            tableChoices = DatabaseConnection.getInstance().getConnectionObject().getTablesForDatabase(databaseName);
        } catch (SQLException e) {
            log.error("Could not list tables of {}", databaseName, e);
        }

        tableNameField.setPromptText("Table name");
        tableNameField.textProperty().addListener((obs, old, name) ->
                setObjectTitle(name == null || name.isBlank() ? (isEditMode ? originalTableName : "New Table") : name.trim()));

        Button addColumn = addButton("Add column", this::addColumnRow);
        Button addForeignKey = addButton("Add foreign key", () -> addForeignKeyRow(null));

        addTab(new SimpleTab("General", "Name of the table", MaterialDesignI.INFORMATION_OUTLINE, generalPane()));
        addTab(new SimpleTab("Columns", "Names, types and constraints", MaterialDesignT.TABLE_COLUMN, columnsPane(), addColumn));
        addTab(new SimpleTab("Foreign Keys", "Links to columns in other tables", MaterialDesignT.TABLE_KEY, foreignKeysPane(), addForeignKey));

        Button cancel = closeFooterButton("Cancel");
        Button review = footerButton(isEditMode ? "Review & Update" : "Review & Create");
        review.getStyleClass().add(Styles.ACCENT);
        review.setDefaultButton(true);
        review.setOnAction(e -> handleCreateTable(tableNameField.getText()));
        setFooter(cancel, review);

        if (isEditMode) {
            loadTableData();
        } else {
            addColumnRow();
        }
        updateForeignKeysEmpty();
    }

    private static Button addButton(String text, Runnable action) {
        Button button = new Button(text, new FontIcon(MaterialDesignP.PLUS));
        button.setFocusTraversable(false);
        button.getStyleClass().addAll(Styles.SMALL, Styles.ACCENT);
        button.setOnAction(e -> action.run());
        return button;
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private VBox generalPane() {
        VBox card = ModalUi.card("Table", MaterialDesignT.TABLE);
        VBox form = new VBox(6, ModalUi.fieldLabel("Table name", MaterialDesignL.LABEL_OUTLINE), tableNameField);
        form.setPadding(new Insets(0, 16, 16, 16));
        tableNameField.setMaxWidth(Double.MAX_VALUE);

        Label dbValue = new Label(databaseName);
        dbValue.setStyle("-fx-font-weight: bold;");
        VBox dbForm = new VBox(6, ModalUi.fieldLabel("Database", MaterialDesignD.DATABASE_OUTLINE), dbValue);
        dbForm.setPadding(new Insets(0, 16, 16, 16));

        card.getChildren().addAll(form, dbForm);
        VBox content = ModalUi.content();
        content.getChildren().add(card);
        return new VBox(ModalUi.scroll(content));
    }

    private VBox columnsPane() {
        columnsEmpty.visibleProperty().bind(Bindings.isEmpty(columnRows));
        columnsEmpty.managedProperty().bind(columnsEmpty.visibleProperty());

        columnsBox.setSpacing(0);
        ModalUi.markLast(columnsBox);
        VBox list = new VBox(8, columnsEmpty, columnsBox);
        return ModalUi.listTab(list);
    }
    private VBox foreignKeysPane() {
        VBox content = ModalUi.content();
        content.getChildren().addAll(foreignKeysEmpty, foreignKeysBox);
        return new VBox(ModalUi.scroll(content));
    }

    private void updateForeignKeysEmpty() {
        boolean empty = foreignKeyRows.isEmpty();
        foreignKeysEmpty.setVisible(empty);
        foreignKeysEmpty.setManaged(empty);
    }

    // ── Loading (edit mode) ───────────────────────────────────────────────────

    private void loadTableData() {
        try {
            tableNameField.setText(originalTableName);
            for (ColumnData column : DatabaseConnection.getInstance().getConnectionObject().getColumnsForTable(databaseName, originalTableName)) {
                addColumnRow(column);
            }
            Map<String, ForeignKeyData> foreignKeys = DatabaseConnection.getInstance().getConnectionObject().extractForeignKeys(databaseName, originalTableName);
            for (ForeignKeyData fk : foreignKeys.values()) {
                addForeignKeyRow(fk);
            }
        } catch (SQLException e) {
            log.error("loadTableData failed", e);
            ModalUi.error("Load Failed", "Could not load table data: " + e.getMessage());
        }
    }

    private void addColumnRow() {
        addColumnRow(null);
    }

    private void addColumnRow(ColumnData data) {
        ColumnRow row = new ColumnRow();
        if (data != null) {
            row.name.set(data.getColumnName());
            row.type.set(data.getDataType());
            row.primaryKey.set(data.isPrimaryKey());
            row.notNull.set(data.isNotNull());
            row.unique.set(data.isUnique());
            row.autoIncrement.set(data.isAutoIncrement());
            row.originalName = data.getColumnName();
        }
        row.node = columnCard(row);
        columnRows.add(row);
        columnsBox.getChildren().add(row.node);
        localColumnsList.add(row.name.get());
        row.name.addListener((obs, old, name) -> {
            int index = columnRows.indexOf(row);
            if (index >= 0) localColumnsList.set(index, name != null ? name : "");
        });
    }

    private void removeColumnRow(ColumnRow row) {
        int index = columnRows.indexOf(row);
        if (index < 0) return;
        columnRows.remove(index);
        localColumnsList.remove(index);
        columnsBox.getChildren().remove(row.node);
    }

    /** One column as a card: name and type fields, constraint toggles and a remove button. */
    private HBox columnCard(ColumnRow row) {
        TextField name = new TextField();
        name.setPromptText("e.g. customer_id");
        name.setMaxWidth(Double.MAX_VALUE);
        name.setMinWidth(120);
        name.textProperty().bindBidirectional(row.name);

        ComboBox<String> type = new ComboBox<>(dataTypes);
        type.setPromptText("Select or type");
        type.setEditable(true);
        type.setPrefWidth(210);
        type.setMinWidth(210);
        type.valueProperty().bindBidirectional(row.type);

        HBox constraints = new HBox(6,
                toggle("PK", "Primary key", row.primaryKey), toggle("NOT NULL", "Not null", row.notNull),
                toggle("UNIQUE", "Unique", row.unique), toggle("AUTO INC", "Auto increment", row.autoIncrement));
        constraints.setAlignment(Pos.CENTER_LEFT);
        constraints.setMinWidth(Region.USE_PREF_SIZE);

        Button delete = new Button(null, new FontIcon(Feather.TRASH_2));
        delete.setFocusTraversable(false);
        delete.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.DANGER);
        delete.setTooltip(new Tooltip("Remove column"));
        delete.setOnAction(e -> removeColumnRow(row));

        // Every control in the row gets the same height and the cells are top-aligned under their labels, so the
        // fields, toggles and the remove button share one baseline; the button's cell reserves the label's height.
        for (Region control : new Region[]{name, type, delete}) fixHeight(control);
        delete.setMinWidth(CONTROL_HEIGHT);
        delete.setPrefWidth(CONTROL_HEIGHT);
        delete.setMaxWidth(CONTROL_HEIGHT);
        constraints.getChildren().forEach(t -> fixHeight((Region) t));

        VBox nameBox = labeled("COLUMN NAME", name);
        name.setPrefWidth(280);
        nameBox.setMaxWidth(Double.MAX_VALUE);
        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        Label spacerLabel = ModalUi.sectionLabel("COLUMN NAME");
        spacerLabel.setVisible(false);
        HBox card = new HBox(12, nameBox, labeled("DATA TYPE", type), labeled("CONSTRAINTS", constraints),
                fill, new VBox(4, spacerLabel, delete));
        card.setAlignment(Pos.TOP_LEFT);
        card.setStyle("-fx-padding: 14 24 14 24;");
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("modal-item");
        return card;
    }

    private static final double CONTROL_HEIGHT = 34;

    private static void fixHeight(Region control) {
        control.setMinHeight(CONTROL_HEIGHT);
        control.setPrefHeight(CONTROL_HEIGHT);
        control.setMaxHeight(CONTROL_HEIGHT);
    }

    private static VBox labeled(String label, Node control) {
        return new VBox(4, ModalUi.sectionLabel(label), control);
    }

    private static ToggleButton toggle(String text, String tooltip, BooleanProperty property) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("modal-toggle");
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        button.selectedProperty().bindBidirectional(property);
        return button;
    }

    // ── Foreign key rows ──────────────────────────────────────────────────────

    private void addForeignKeyRow(ForeignKeyData data) {
        ForeignKeyRow row = new ForeignKeyRow();

        row.constraintNameField = new TextField(data != null ? data.getConstraintName() : "");
        row.constraintNameField.setPromptText("Auto-generated if empty");

        row.localColumnCombo = new ComboBox<>(localColumnsList);
        row.localColumnCombo.setPromptText("Select column");
        if (data != null) row.localColumnCombo.setValue(data.getLocalColumn());

        row.referenceTableCombo = new ComboBox<>(FXCollections.observableArrayList(tableChoices));
        row.referenceTableCombo.setPromptText("Select table");

        row.referenceColumnCombo = new ComboBox<>();
        row.referenceColumnCombo.setPromptText("Select column");

        row.referenceTableCombo.setOnAction(e -> loadReferenceColumns(row, row.referenceTableCombo.getValue(), null));
        if (data != null && data.getReferenceTable() != null) {
            row.referenceTableCombo.setValue(data.getReferenceTable());
            loadReferenceColumns(row, data.getReferenceTable(), data.getReferenceColumn());
        }

        row.onDeleteCombo = actionCombo(data != null ? data.getOnDelete() : null);
        row.onUpdateCombo = actionCombo(data != null ? data.getOnUpdate() : null);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(6);
        grid.setPadding(new Insets(4, 16, 16, 16));
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        grid.getColumnConstraints().addAll(half, half);

        grid.add(ModalUi.fieldLabel("Constraint name", MaterialDesignL.LABEL_VARIANT_OUTLINE), 0, 0);
        grid.add(row.constraintNameField, 0, 1);
        grid.add(ModalUi.fieldLabel("Local column", MaterialDesignT.TABLE_COLUMN), 0, 2);
        grid.add(row.localColumnCombo, 0, 3);
        grid.add(ModalUi.fieldLabel("On delete", Feather.TRASH_2), 0, 4);
        grid.add(row.onDeleteCombo, 0, 5);

        grid.add(ModalUi.fieldLabel("References table", MaterialDesignT.TABLE), 1, 0);
        grid.add(row.referenceTableCombo, 1, 1);
        grid.add(ModalUi.fieldLabel("Referenced column", MaterialDesignL.LINK_VARIANT), 1, 2);
        grid.add(row.referenceColumnCombo, 1, 3);
        grid.add(ModalUi.fieldLabel("On update", MaterialDesignU.UPDATE), 1, 4);
        grid.add(row.onUpdateCombo, 1, 5);

        for (javafx.scene.Node n : grid.getChildren()) {
            if (n instanceof javafx.scene.control.Control c) c.setMaxWidth(Double.MAX_VALUE);
        }

        Label title = new Label();
        title.getStyleClass().add("modal-card-title");
        title.setPadding(new Insets(0));
        title.setGraphic(new FontIcon(MaterialDesignT.TABLE_KEY));
        title.setGraphicTextGap(8);
        title.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> {
            String name = row.constraintNameField.getText();
            return name == null || name.isBlank() ? "Foreign key " + (foreignKeyRows.indexOf(row) + 1) : name.trim();
        }, row.constraintNameField.textProperty(), foreignKeysBox.getChildren()));

        Button delete = new Button(null, new FontIcon(Feather.TRASH_2));
        delete.setFocusTraversable(false);
        delete.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.DANGER);
        delete.setOnAction(e -> {
            foreignKeyRows.remove(row);
            foreignKeysBox.getChildren().remove(row.node);
            updateForeignKeysEmpty();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, spacer, delete);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 10, 4, 16));

        row.node = ModalUi.card(null, null);
        row.node.setMaxWidth(Double.MAX_VALUE);
        row.node.getChildren().addAll(header, grid);

        foreignKeyRows.add(row);
        foreignKeysBox.getChildren().add(row.node);
        updateForeignKeysEmpty();
    }

    private static ComboBox<String> actionCombo(String value) {
        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(REFERENTIAL_ACTIONS));
        combo.setPromptText("Choose action");
        if (value != null) combo.setValue(value);
        return combo;
    }

    private void loadReferenceColumns(ForeignKeyRow row, String table, String selectColumn) {
        if (table == null) return;
        try {
            List<ColumnData> columns = DatabaseConnection.getInstance().getConnectionObject().getColumnsForTable(databaseName, table);
            row.referenceColumnCombo.setItems(FXCollections.observableArrayList(columns));
            if (selectColumn != null) {
                columns.stream().filter(c -> c.getColumnName().equals(selectColumn)).findFirst()
                        .ifPresent(row.referenceColumnCombo::setValue);
            }
        } catch (SQLException e) {
            log.error("Could not load columns of {}.{}", databaseName, table, e);
            ModalUi.error("Load Failed", "Could not load the columns of " + table + ": " + e.getMessage());
        }
    }

    // ── Review ────────────────────────────────────────────────────────────────

    private void handleCreateTable(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            ModalUi.error("Invalid Table", "The table name cannot be empty.");
            return;
        }

        List<ColumnData> columns = new ArrayList<>();
        for (ColumnRow row : columnRows) {
            String name = row.name.get();
            String dataType = row.type.get();

            if (name == null || name.trim().isEmpty()) {
                ModalUi.error("Invalid Column", "A column name cannot be empty.");
                return;
            }
            if (dataType == null || dataType.trim().isEmpty()) {
                ModalUi.error("Invalid Column", "The data type for column '" + name + "' cannot be empty.");
                return;
            }

            columns.add(new ColumnData(name.trim(), dataType.trim(),
                    row.primaryKey.get(), row.notNull.get(),
                    row.unique.get(), row.autoIncrement.get()));
        }

        if (columns.isEmpty()) {
            ModalUi.error("Invalid Table", "At least one column is required.");
            return;
        }

        List<ForeignKeyData> foreignKeys = new ArrayList<>();
        for (ForeignKeyRow row : foreignKeyRows) {
            String localCol = row.localColumnCombo.getValue();
            String refTable = row.referenceTableCombo.getValue();
            ColumnData refCol = row.referenceColumnCombo.getValue();

            if (localCol == null || refTable == null || refCol == null) {
                continue;
            }

            String constraintName = row.constraintNameField.getText();
            if (constraintName == null || constraintName.trim().isEmpty()) {
                constraintName = "fk_" + tableName + "_" + localCol;
            }

            foreignKeys.add(new ForeignKeyData(constraintName.trim(), localCol, refTable, refCol.getColumnName(),
                    row.onDeleteCombo.getValue(), row.onUpdateCombo.getValue()));
        }

        String sql;
        if (isEditMode) {
            Map<String, String> columnRenames = new HashMap<>();
            for (ColumnRow row : columnRows) {
                String currentName = row.name.get().trim();
                if (row.originalName != null && !row.originalName.equals(currentName)) {
                    columnRenames.put(row.originalName, currentName);
                }
            }
            try {
                ArrayList<ColumnData> oldColumns = DatabaseConnection.getInstance().getConnectionObject().getColumnsForTable(databaseName, originalTableName);
                Map<String, ForeignKeyData> oldForeignKeys = DatabaseConnection.getInstance().getConnectionObject().extractForeignKeys(databaseName, originalTableName);

                sql = DatabaseConnection.getInstance().getConnectionObject()
                        .generateAlterTableSQL(databaseName, originalTableName, tableName.trim(),
                                oldColumns, columns, oldForeignKeys, foreignKeys, columnRenames);
            } catch (SQLException e) {
                log.error("generateAlterTableSQL failed", e);
                ModalUi.error("Script Generation Failed", "Could not generate the ALTER script: " + e.getMessage());
                return;
            }
        } else {
            sql = DatabaseConnection.getInstance().getConnectionObject().generateCreateTableSQL(databaseName, tableName.trim(), columns, foreignKeys);
        }

        sql = SQLFormatterUtils.format(sql, DatabaseConnection.getInstance().getConnectionObject());

        App.showModal(new SQLEditorModal(sql, refreshTables));
    }
}
