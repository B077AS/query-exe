package com.queryexe.components.tree;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignK;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import com.queryexe.model.data.ColumnData;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.model.connections.PostgresConnection;

public class CustomTree extends VBox {

    private TreeView<String> databaseTree = new TreeView<String>();
    private TreeView<String> usersTree;
    private LinkedHashMap<String, CustomTreeItem> databasesMap;
    private CustomTreeItem selectedDatabase;
    private TextField searchField;
    private Button searchButton;
    private HBox headerBox;
    private TreeItem<String> originalRootItem;
    private TreeItem<String> originalUsersRootItem;
    private boolean isSearchMode = false;
    private VBox usersView;
    private boolean isUsersViewActive = false;
    private DatabaseStructureCache structureCache;

    public static class DatabaseStructureCache {
        private Map<String, LinkedHashMap<String, ArrayList<ColumnData>>> postgresSchemas = new LinkedHashMap<>();
        private Map<String, Map<String, ArrayList<ColumnData>>> databases = new LinkedHashMap<>();

        public void addSchema(String schema, LinkedHashMap<String, ArrayList<ColumnData>> tables) {
            postgresSchemas.put(schema.toLowerCase(), tables);
        }

        public void addDatabase(String database, Map<String, ArrayList<ColumnData>> tables) {
            databases.put(database.toLowerCase(), tables);
        }

        public LinkedHashMap<String, ArrayList<ColumnData>> getPostgresSchema(String schema) {
            return postgresSchemas.get(schema.toLowerCase());
        }

        public Map<String, ArrayList<ColumnData>> getDatabase(String database) {
            return databases.get(database.toLowerCase());
        }

        public ArrayList<String> getPostgresSchemas() {
            return new ArrayList<>(postgresSchemas.keySet());
        }

        public ArrayList<String> getDatabases() {
            return new ArrayList<>(databases.keySet());
        }
    }

    public CustomTree() {
        initialize();
    }

    public void initialize() {
        this.structureCache = new DatabaseStructureCache();
        this.databasesMap = new LinkedHashMap<String, CustomTreeItem>();
        this.getChildren().clear();
        this.setStyle("-fx-background-color: -color-bg-default;");

        ArrayList<String> databaseStringList = DatabaseConnection.getInstance().getConnectionObject().getDatabases(DatabaseConnection.getInstance().getConnectionObject().getDatabaseName());

        if (DatabaseConnection.getInstance().getConnectionObject().getDbType().equals(ConnectionTypes.PostgreSQL.toString())) {

            if (!DatabaseConnection.getInstance().getConnectionObject().getDatabaseName().isEmpty()) {
                createPostgresTreeItem();

                try {
                    String useDb = "SHOW search_path;";
                    PreparedStatement pathStatement = DatabaseConnection.getInstance().getConnection().prepareStatement(useDb);
                    ResultSet path = pathStatement.executeQuery();
                    path.next();

                    String defaultSchema = path.getString(1).split(",")[1].trim();
                    CustomTreeItem schemaItem = databasesMap.get(defaultSchema.toLowerCase());

                    if (schemaItem != null) {
                        databaseTree.getSelectionModel().select(schemaItem);
                        selectedDatabase = schemaItem;
                        selectedDatabase.setSelected();
                    }
                } catch (SQLException e) {
                }
            } else {
                LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, ArrayList<ColumnData>>>> structure = ((PostgresConnection) DatabaseConnection.getInstance().getConnectionObject()).getCompleteHierarchy();
                CustomTreeItem databasesMainTreeItem = new CustomTreeItem(DatabaseConnection.getInstance().getConnectionObject().getConnectionName(), new FontIcon(MaterialDesignD.DATABASE));
                databasesMainTreeItem.setupDatabasesContextMenu();
                databasesMainTreeItem.setExpanded(true);

                databaseTree = new TreeView<String>(databasesMainTreeItem);

                for (Map.Entry<String, LinkedHashMap<String, LinkedHashMap<String, ArrayList<ColumnData>>>> database : structure.entrySet()) {
                    CustomTreeItem databaseItem = new CustomTreeItem(database.getKey(), new FontIcon(MaterialDesignH.HOCKEY_PUCK));
                    databasesMainTreeItem.setDatabaseName(database.getKey());
                    databasesMainTreeItem.getChildren().add(databaseItem);

                    CustomTreeItem schemaMainItem = new CustomTreeItem("Schemas", new FontIcon(MaterialDesignF.FILE_TABLE_BOX_MULTIPLE_OUTLINE));
                    schemaMainItem.setupSchemasContextMenu();
                    databaseItem.getChildren().add(schemaMainItem);

                    for (Map.Entry<String, LinkedHashMap<String, ArrayList<ColumnData>>> schema : database.getValue().entrySet()) {
                        CustomTreeItem schemaItem = new CustomTreeItem(schema.getKey(), new FontIcon(MaterialDesignF.FILE_TABLE_BOX_OUTLINE));
                        schemaMainItem.getChildren().add(schemaItem);

                        CustomTreeItem tablesMainTreeItem = new CustomTreeItem("Tables", new FontIcon(MaterialDesignT.TABLE_MULTIPLE));
                        tablesMainTreeItem.setDatabaseName(schema.getKey());
                        schemaItem.getChildren().add(tablesMainTreeItem);

                        for (Map.Entry<String, ArrayList<ColumnData>> table : schema.getValue().entrySet()) {
                            CustomTreeItem tableItem = new CustomTreeItem(table.getKey(), new FontIcon(MaterialDesignT.TABLE));
                            tablesMainTreeItem.getChildren().add(tableItem);

                            for (ColumnData column : table.getValue()) {
                                CustomTreeItem columnsTree;
                                if (column.isPrimaryKey()) {
                                    columnsTree = new CustomTreeItem(column.getColumnName(), new FontIcon(MaterialDesignT.TABLE_COLUMN));
                                } else {
                                    columnsTree = new CustomTreeItem(column.getColumnName(), new FontIcon(MaterialDesignK.KEY));
                                }
                                columnsTree.setDatabaseName(schema.getKey());
                                columnsTree.setupColumnContextMenu();
                                tableItem.getChildren().add(columnsTree);
                            }
                        }
                    }
                }
            }
            this.getChildren().addAll(createHeaderBox(), databaseTree);
        } else {
            CustomTreeItem databasesMainTreeItem = new CustomTreeItem(DatabaseConnection.getInstance().getConnectionObject().getConnectionName(), new FontIcon(MaterialDesignD.DATABASE));
            databasesMainTreeItem.setupDatabasesContextMenu();
            databasesMainTreeItem.setExpanded(true);

            databaseTree = new TreeView<String>(databasesMainTreeItem);

            for (String database : databaseStringList) {

                CustomTreeItem databaseItem = new CustomTreeItem(database, new FontIcon(MaterialDesignH.HOCKEY_PUCK));
                databaseItem.setDatabaseName(database);
                databaseItem.setupDatabaseContextMenu();
                databasesMainTreeItem.getChildren().add(databaseItem);
                databasesMap.put(database.toLowerCase(), databaseItem);

                CustomTreeItem tablesMainTreeItem = new CustomTreeItem("Tables", new FontIcon(MaterialDesignT.TABLE_MULTIPLE));
                tablesMainTreeItem.setDatabaseName(database);
                tablesMainTreeItem.setupTablesContextMenu();

                Map<String, ArrayList<ColumnData>> tablesAndColumnsMap = DatabaseConnection.getInstance().getConnectionObject().getAllTablesAndColumns(database);
                structureCache.addDatabase(database, tablesAndColumnsMap);
                addTablesAndColumns(tablesAndColumnsMap, tablesMainTreeItem, database);

                databaseItem.getChildren().add(tablesMainTreeItem);
            }
            this.getChildren().addAll(createHeaderBox(), databaseTree);

            if (DatabaseConnection.getInstance().getConnectionObject().getDatabaseName() != null && !DatabaseConnection.getInstance().getConnectionObject().getDatabaseName().isEmpty()) {
                try {
                    String useDb = "USE " + DatabaseConnection.getInstance().getConnectionObject().getDatabaseName();
                    PreparedStatement tableStatement = DatabaseConnection.getInstance().getConnection().prepareStatement(useDb);
                    tableStatement.execute();
                } catch (SQLException e) {
                }
                databaseTree.getSelectionModel().select(1);
                selectedDatabase = databasesMap
                        .get(DatabaseConnection.getInstance().getConnectionObject().getDatabaseName());
                selectedDatabase.setSelected();
            } else if (DatabaseConnection.getInstance().getConnectionObject().getDatabaseName() == null && DatabaseConnection.getInstance().getConnectionObject().getDbType().equals(ConnectionTypes.H2.toString())) {
                try {
                    String useDb = "SELECT SCHEMA();";
                    PreparedStatement pathStatement = DatabaseConnection.getInstance().getConnection().prepareStatement(useDb);
                    ResultSet path = pathStatement.executeQuery();
                    path.next();

                    String defaultSchema = path.getString(1);
                    CustomTreeItem schemaItem = databasesMap.get(defaultSchema.toLowerCase());

                    if (schemaItem != null) {
                        databaseTree.getSelectionModel().select(schemaItem);
                        selectedDatabase = schemaItem;
                        selectedDatabase.setSelected();
                    }
                } catch (SQLException e) {
                }
            }
        }

        originalRootItem = databaseTree.getRoot();

        usersView = createUsersView();
        originalUsersRootItem = usersTree.getRoot();

        VBox.setVgrow(databaseTree, Priority.ALWAYS);

        databaseTree.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                Node node = event.getPickResult().getIntersectedNode();
                while (node != null && !(node instanceof TreeCell)) {
                    node = node.getParent();
                }

                if (node instanceof TreeCell<?>) {
                    TreeCell<?> cell = (TreeCell<?>) node;
                    if (cell.getItem() != null) {
                        CustomTreeItem selectedItem = (CustomTreeItem) databaseTree.getSelectionModel().getSelectedItem();
                        if (selectedItem.getContextMenu() != null) {
                            selectedItem.getContextMenu().show(selectedItem.getGraphic(), event.getScreenX(),
                                    event.getScreenY());
                        }
                    }
                }
            }
        });

        setupSearchField();
    }

    private void setupSearchField() {
        searchField = new TextField();
        searchField.setPromptText("Search...");
        searchField.getStyleClass().add(Styles.SMALL);
        searchField.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                if (newValue != null && !newValue.trim().isEmpty()) {
                    if (isUsersViewActive) {
                        filterUsersTree(newValue.trim());
                    } else {
                        filterTree(newValue.trim());
                    }
                } else {
                    clearFilter();
                }
            }
        });

        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String searchText = searchField.getText().trim();
                if (!searchText.isEmpty()) {
                    if (isUsersViewActive) {
                        filterUsersTree(searchText);
                    } else {
                        filterTree(searchText);
                    }
                }
            } else if (event.getCode() == KeyCode.ESCAPE) {
                hideSearchField();
            }
        });
    }

    private HBox createSearchHeaderBox() {
        HBox searchHeaderBox = new HBox();
        searchHeaderBox.setSpacing(3);
        searchHeaderBox.setPadding(new Insets(5, 5, 5, 5));
        searchHeaderBox.setStyle("-fx-border-width: 0px 0px 2px 0px; -fx-border-color: -color-border-default;");
        searchHeaderBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon cancelIcon = new FontIcon(MaterialDesignC.CLOSE);
        cancelIcon.getStyleClass().add("custom-icon-25px");
        Button cancelButton = new Button(null, cancelIcon);
        cancelButton.getStyleClass().addAll(Styles.FLAT);
        cancelButton.setPadding(new Insets(5, 5, 5, 5));
        cancelButton.setOnAction(event -> hideSearchField());

        HBox.setHgrow(searchField, Priority.ALWAYS);

        searchHeaderBox.getChildren().addAll(searchField, cancelButton);
        return searchHeaderBox;
    }

    private HBox createNormalHeaderBox() {
        HBox normalHeaderBox = new HBox();
        normalHeaderBox.setSpacing(3);
        normalHeaderBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon databaseViewIcon = new FontIcon(MaterialDesignD.DATABASE);
        databaseViewIcon.getStyleClass().add("custom-icon-25px");
        Button databaseViewButton = new Button("Databases", databaseViewIcon);
        databaseViewButton.getStyleClass().addAll(Styles.FLAT);
        databaseViewButton.setPadding(new Insets(5, 5, 5, 5));
        if (!isUsersViewActive) {
            databaseViewButton.setStyle("-fx-background-color: -color-accent-subtle;");
        }
        databaseViewButton.setOnAction(event -> switchToDatabaseView());

        FontIcon usersViewIcon = new FontIcon(MaterialDesignA.ACCOUNT_MULTIPLE);
        usersViewIcon.getStyleClass().add("custom-icon-25px");
        Button usersViewButton = new Button("Users", usersViewIcon);
        usersViewButton.getStyleClass().addAll(Styles.FLAT);
        usersViewButton.setPadding(new Insets(5, 5, 5, 5));
        if (isUsersViewActive) {
            usersViewButton.setStyle("-fx-background-color: -color-accent-subtle;");
        }
        usersViewButton.setOnAction(event -> switchToUsersView());

        Region headerFillerRegion = new Region();
        HBox.setHgrow(headerFillerRegion, Priority.ALWAYS);

        FontIcon refreshIcon = new FontIcon(MaterialDesignR.REFRESH);
        refreshIcon.getStyleClass().add("custom-icon-25px");
        Button refresh = new Button(null, refreshIcon);
        refresh.getStyleClass().addAll(Styles.FLAT);
        refresh.setPadding(new Insets(5, 5, 5, 5));
        refresh.setOnAction(event -> {
            this.initialize();
        });

        FontIcon searchIcon = new FontIcon(MaterialDesignM.MAGNIFY);
        searchIcon.getStyleClass().add("custom-icon-25px");
        searchButton = new Button(null, searchIcon);
        searchButton.getStyleClass().addAll(Styles.FLAT);
        searchButton.setPadding(new Insets(5, 5, 5, 5));
        searchButton.setOnAction(event -> showSearchField());

        normalHeaderBox.getChildren().addAll(databaseViewButton, usersViewButton, headerFillerRegion, refresh, searchButton);

        return normalHeaderBox;
    }

    private void switchToDatabaseView() {
        if (!isUsersViewActive) return;

        isUsersViewActive = false;
        this.getChildren().clear();
        this.getChildren().addAll(createHeaderBox(), databaseTree);

        headerBox.getChildren().clear();
        headerBox.getChildren().addAll(createNormalHeaderBox().getChildren());

        if (isSearchMode) {
            searchField.setPromptText("Search tables and columns...");
        }
    }

    private void switchToUsersView() {
        if (isUsersViewActive) return;

        isUsersViewActive = true;

        this.getChildren().clear();
        this.getChildren().addAll(createHeaderBox(), usersView);

        headerBox.getChildren().clear();
        headerBox.getChildren().addAll(createNormalHeaderBox().getChildren());

        if (isSearchMode) {
            searchField.setPromptText("Search users...");
        }
    }

    private VBox createUsersView() {

        VBox usersContainer = new VBox();
        usersContainer.setStyle("-fx-background-color: -color-bg-default;");
        VBox.setVgrow(usersContainer, Priority.ALWAYS);
        try {

            List<String> users = DatabaseConnection.getInstance().getConnectionObject().getUsers();

            CustomTreeItem databasesMainTreeItem = new CustomTreeItem(DatabaseConnection.getInstance().getConnectionObject().getConnectionName(), new FontIcon(MaterialDesignA.ACCOUNT_MULTIPLE));
            databasesMainTreeItem.setExpanded(true);

            if (users != null) {
                for (String user : users) {
                    CustomTreeItem userTreeItem = new CustomTreeItem(user, new FontIcon(MaterialDesignA.ACCOUNT));
                    databasesMainTreeItem.getChildren().add(userTreeItem);
                }
            }

            usersTree = new TreeView<String>(databasesMainTreeItem);
            VBox.setVgrow(usersTree, Priority.ALWAYS);

        } catch (SQLException e) {
            usersTree = new TreeView<String>();
        }
        usersContainer.getChildren().add(usersTree);
        return usersContainer;
    }

    private void showSearchField() {
        if (!isSearchMode) {
            headerBox.getChildren().clear();
            headerBox.getChildren().addAll(createSearchHeaderBox().getChildren());

            if (isUsersViewActive) {
                searchField.setPromptText("Search users...");
            } else {
                searchField.setPromptText("Search tables and columns...");
            }

            searchField.requestFocus();
            isSearchMode = true;
        } else {
            hideSearchField();
        }
    }

    private void hideSearchField() {
        if (isSearchMode) {
            searchField.clear();
            clearFilter();

            headerBox.getChildren().clear();
            headerBox.getChildren().addAll(createNormalHeaderBox().getChildren());

            isSearchMode = false;
        }
    }

    private void filterTree(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            clearFilter();
            return;
        }

        String searchLower = searchText.toLowerCase();
        TreeItem<String> filteredRoot = createFilteredTree(originalRootItem, searchLower);

        if (filteredRoot != null) {
            databaseTree.setRoot(filteredRoot);
            expandFilteredTree(filteredRoot);
        } else {
            clearFilter();
        }
    }

    private void filterUsersTree(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            clearFilter();
            return;
        }

        String searchLower = searchText.toLowerCase();
        TreeItem<String> filteredRoot = createFilteredUsersTree(originalUsersRootItem, searchLower);

        if (filteredRoot != null) {
            usersTree.setRoot(filteredRoot);
            expandFilteredTree(filteredRoot);
        } else {
            clearFilter();
        }
    }

    private TreeItem<String> createFilteredTree(TreeItem<String> original, String searchText) {
        if (original == null)
            return null;

        TreeItem<String> filteredItem = null;
        boolean hasMatchingChildren = false;

        if (original instanceof CustomTreeItem) {
            CustomTreeItem customOriginal = (CustomTreeItem) original;
            String itemText = customOriginal.getTitleLabel().getText().toLowerCase();

            CustomTreeItem filteredCustomItem = new CustomTreeItem(customOriginal.getTitleLabel().getText(),
                    new FontIcon(customOriginal.getIcon().getIconCode()));
            filteredCustomItem.setDatabaseName(customOriginal.getDatabaseName());
            if (itemText.contains("tables")) {
                filteredCustomItem.setupTablesContextMenu();
            } else if (isTableItem(customOriginal)) {
                filteredCustomItem.setupTableContextMenu();
            } else if (isDatabaseOrSchemaItem(customOriginal)) {
                filteredCustomItem.setupDatabaseContextMenu();
            } else if (isColumnItem(customOriginal)) {
                filteredCustomItem.setupColumnContextMenu();
            }

            filteredItem = filteredCustomItem;

            for (TreeItem<String> child : original.getChildren()) {
                TreeItem<String> filteredChild = createFilteredTree(child, searchText);
                if (filteredChild != null) {
                    filteredItem.getChildren().add(filteredChild);
                    hasMatchingChildren = true;
                }
            }

            if (itemText.contains(searchText) || hasMatchingChildren) {
                filteredItem.setExpanded(true);
                return filteredItem;
            }
        }

        return null;
    }

    private TreeItem<String> createFilteredUsersTree(TreeItem<String> original, String searchText) {
        if (original == null)
            return null;

        TreeItem<String> filteredItem = null;
        boolean hasMatchingChildren = false;

        if (original instanceof CustomTreeItem) {
            CustomTreeItem customOriginal = (CustomTreeItem) original;
            String itemText = customOriginal.getTitleLabel().getText().toLowerCase();

            CustomTreeItem filteredCustomItem = new CustomTreeItem(customOriginal.getTitleLabel().getText(),
                    new FontIcon(customOriginal.getIcon().getIconCode()));

            filteredItem = filteredCustomItem;

            for (TreeItem<String> child : original.getChildren()) {
                TreeItem<String> filteredChild = createFilteredUsersTree(child, searchText);
                if (filteredChild != null) {
                    filteredItem.getChildren().add(filteredChild);
                    hasMatchingChildren = true;
                }
            }

            if (itemText.contains(searchText) || hasMatchingChildren) {
                filteredItem.setExpanded(true);
                return filteredItem;
            }
        }

        return null;
    }

    private boolean isTableItem(CustomTreeItem item) {
        TreeItem<String> parent = item.getParent();
        if (parent instanceof CustomTreeItem) {
            CustomTreeItem parentCustom = (CustomTreeItem) parent;
            return parentCustom.getTitleLabel().getText().equals("Tables");
        }
        return false;
    }

    private boolean isDatabaseOrSchemaItem(CustomTreeItem item) {
        TreeItem<String> parent = item.getParent();
        if (parent instanceof CustomTreeItem) {
            CustomTreeItem parentCustom = (CustomTreeItem) parent;
            String parentText = parentCustom.getTitleLabel().getText();
            return parentText.equals("Databases") || parentText.equals("Schemas");
        }
        return false;
    }

    private boolean isColumnItem(CustomTreeItem item) {
        TreeItem<String> parent = item.getParent();
        if (parent instanceof CustomTreeItem) {
            TreeItem<String> grandparent = parent.getParent();
            if (grandparent instanceof CustomTreeItem) {
                CustomTreeItem grandparentCustom = (CustomTreeItem) grandparent;
                return grandparentCustom.getTitleLabel().getText().equals("Tables");
            }
        }
        return false;
    }

    private void expandFilteredTree(TreeItem<String> item) {
        if (item != null) {
            item.setExpanded(true);
            for (TreeItem<String> child : item.getChildren()) {
                expandFilteredTree(child);
            }
        }
    }

    private void clearFilter() {
        if (isUsersViewActive) {
            if (usersTree != null && usersTree.getRoot() != originalUsersRootItem) {
                usersTree.setRoot(originalUsersRootItem);
            }
        } else {
            if (databaseTree.getRoot() != originalRootItem) {
                databaseTree.setRoot(originalRootItem);

                if (selectedDatabase != null) {
                    Platform.runLater(() -> {
                        databaseTree.getSelectionModel().select(selectedDatabase);
                    });
                }
            }
        }
    }

    public HBox createHeaderBox() {
        headerBox = new HBox();
        headerBox.setSpacing(3);
        headerBox.setPadding(new Insets(5, 5, 5, 5));
        headerBox.setStyle("-fx-border-width: 0px 0px 2px 0px; -fx-border-color: -color-border-default;");
        headerBox.setAlignment(Pos.CENTER_LEFT);

        headerBox.getChildren().addAll(createNormalHeaderBox().getChildren());

        return headerBox;
    }

    public void addTablesAndColumns(Map<String, ArrayList<ColumnData>> tablesAndColumnsMap,
                                    TreeItem<String> tablesMainTreeItem, String databaseName) {
        for (Map.Entry<String, ArrayList<ColumnData>> entry : tablesAndColumnsMap.entrySet()) {

            String tableName = entry.getKey();
            ArrayList<ColumnData> columns = entry.getValue();
            CustomTreeItem tableItem = new CustomTreeItem(tableName, new FontIcon(MaterialDesignT.TABLE));
            tableItem.setDatabaseName(databaseName);
            tableItem.setupTableContextMenu();
            tablesMainTreeItem.getChildren().add(tableItem);

            for (ColumnData column : columns) {
                CustomTreeItem columnsTree;
                if (!column.isPrimaryKey()) {
                    columnsTree = new CustomTreeItem(column.getColumnName(), new FontIcon(MaterialDesignT.TABLE_COLUMN));
                } else {
                    columnsTree = new CustomTreeItem(column.getColumnName(), new FontIcon(MaterialDesignK.KEY));
                }
                columnsTree.setDatabaseName(databaseName);
                columnsTree.setupColumnContextMenu();
                tableItem.getChildren().add(columnsTree);
            }
        }
    }

    public TreeItem<String> createPostgresTreeItem() {
        CustomTreeItem databaseItem = new CustomTreeItem(
                DatabaseConnection.getInstance().getConnectionObject().getDatabaseName(),
                new FontIcon(MaterialDesignH.HOCKEY_PUCK));
        databaseItem.setDatabaseName(DatabaseConnection.getInstance().getConnectionObject().getDatabaseName());
        databaseItem.setExpanded(true);

        databaseTree = new TreeView<String>(databaseItem);

        CustomTreeItem schemaMainItem = new CustomTreeItem("Schemas", new FontIcon(MaterialDesignF.FILE_TABLE_BOX_MULTIPLE_OUTLINE));
        schemaMainItem.setExpanded(true);
        schemaMainItem.setupSchemasContextMenu();
        databaseItem.getChildren().add(schemaMainItem);

        PostgresConnection postgres = (PostgresConnection) DatabaseConnection.getInstance().getConnectionObject();
        ArrayList<String> schemasList = postgres.getSchemas();

        for (String schema : schemasList) {
            CustomTreeItem schemaItem = new CustomTreeItem(schema, new FontIcon(MaterialDesignF.FILE_TABLE_BOX_OUTLINE));
            schemaItem.setDatabaseName(schema);
            schemaItem.setupDatabaseContextMenu();
            schemaMainItem.getChildren().add(schemaItem);
            databasesMap.put(schema.toLowerCase(), schemaItem);

            CustomTreeItem tablesMainTreeItem = new CustomTreeItem("Tables", new FontIcon(MaterialDesignT.TABLE_MULTIPLE));
            tablesMainTreeItem.setDatabaseName(schema);
            tablesMainTreeItem.setupTablesContextMenu();
            schemaItem.getChildren().add(tablesMainTreeItem);

            LinkedHashMap<String, ArrayList<ColumnData>> tablesAndColumnsMap = postgres
                    .getAllTablesAndColumns(schema);
            structureCache.addSchema(schema, tablesAndColumnsMap);
            addTablesAndColumns(tablesAndColumnsMap, tablesMainTreeItem, schema);
        }
        return databaseItem;
    }

    public void selectDatabase(String database) {

        if (databasesMap.containsKey(database.toLowerCase())) {

            if (selectedDatabase != null) {
                selectedDatabase.setUnSelected();
            }

            CustomTreeItem item = databasesMap.get(database.toLowerCase());
            databaseTree.getSelectionModel().select(item);
            item.setSelected();
            selectedDatabase = item;
        }
    }

    public DatabaseStructureCache getStructureCache() {
        return structureCache;
    }
}