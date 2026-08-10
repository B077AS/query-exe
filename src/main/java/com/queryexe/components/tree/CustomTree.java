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
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignK;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import com.queryexe.utils.IconColorUtil;
import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import com.queryexe.model.data.ColumnData;
import com.queryexe.service.Async;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.model.connections.PostgresConnection;

public class CustomTree extends VBox {

    private TreeView<String> databaseTree = new TreeView<String>();
    private TreeView<String> usersTree;
    private LinkedHashMap<String, DatabaseTreeItem> databasesMap;
    private DatabaseTreeItem selectedDatabase;
    private TextField searchField;
    private Button searchButton;
    private HBox headerBox;
    private TreeItem<String> originalRootItem;
    private TreeItem<String> originalUsersRootItem;
    private boolean isSearchMode = false;
    private VBox usersView;
    private boolean isUsersViewActive = false;
    private DatabaseStructureCache structureCache;

    private ProgressIndicator searchProgress;
    private Button closeSearchButton;
    private StackPane searchControlStack;

    // Restartable search: every restart() cancels the previous filtering task,
    // so only the latest search text ever reaches the tree.
    private boolean searchTargetIsUsers;
    private final Service<TreeItem<String>> searchService = new Service<>() {
        @Override
        protected Task<TreeItem<String>> createTask() {
            String searchText = searchField.getText().trim().toLowerCase();
            searchTargetIsUsers = isUsersViewActive;
            TreeItem<String> root = searchTargetIsUsers ? originalUsersRootItem : originalRootItem;

            return new Task<>() {
                @Override
                protected TreeItem<String> call() {
                    return createFilteredTree(root, searchText, this);
                }
            };
        }
    };

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
        setupSearchService();
        initialize();
    }

    private void setupSearchService() {
        searchService.setExecutor(Async.VIRTUAL_EXECUTOR);
        searchService.setOnSucceeded(event -> {
            TreeItem<String> filteredRoot = searchService.getValue();
            if (filteredRoot != null) {
                if (searchTargetIsUsers) {
                    usersTree.setRoot(filteredRoot);
                } else {
                    databaseTree.setRoot(filteredRoot);
                }
                expandFilteredTree(filteredRoot);
            } else {
                clearFilter();
            }
        });
    }

    public void initialize() {
        this.structureCache = new DatabaseStructureCache();
        this.databasesMap = new LinkedHashMap<>();
        this.getChildren().clear();
        this.setStyle("-fx-background-color: -color-bg-default;");

        ArrayList<String> databaseStringList = DatabaseConnection.getInstance().getConnectionObject()
                .getDatabases(DatabaseConnection.getInstance().getConnectionObject().getDatabaseName());

        if (DatabaseConnection.getInstance().getConnectionObject().getDbType().equals(ConnectionTypes.PostgreSQL.toString())) {

            if (!DatabaseConnection.getInstance().getConnectionObject().getDatabaseName().isEmpty()) {
                createPostgresTreeItem();

                try {
                    String useDb = "SHOW search_path;";
                    PreparedStatement pathStatement = DatabaseConnection.getInstance().getConnection().prepareStatement(useDb);
                    ResultSet path = pathStatement.executeQuery();
                    path.next();

                    String defaultSchema = path.getString(1).split(",")[1].trim();
                    DatabaseTreeItem schemaItem = databasesMap.get(defaultSchema.toLowerCase());

                    if (schemaItem != null) {
                        databaseTree.getSelectionModel().select(schemaItem);
                        selectedDatabase = schemaItem;
                        selectedDatabase.setSelected();
                    }
                } catch (SQLException e) {
                }
            } else {
                LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, ArrayList<ColumnData>>>> structure =
                        ((PostgresConnection) DatabaseConnection.getInstance().getConnectionObject()).getCompleteHierarchy();

                RootTreeItem databasesMainTreeItem = new RootTreeItem(
                        DatabaseConnection.getInstance().getConnectionObject().getConnectionName(),
                        new FontIcon(MaterialDesignD.DATABASE));
                databasesMainTreeItem.setExpanded(true);

                databaseTree = new TreeView<>(databasesMainTreeItem);

                for (Map.Entry<String, LinkedHashMap<String, LinkedHashMap<String, ArrayList<ColumnData>>>> database : structure.entrySet()) {
                    CustomTreeItem databaseItem = new CustomTreeItem(database.getKey(), new FontIcon(MaterialDesignH.HOCKEY_PUCK));
                    databasesMainTreeItem.setDatabaseName(database.getKey());
                    databasesMainTreeItem.getChildren().add(databaseItem);

                    SchemasTreeItem schemaMainItem = new SchemasTreeItem("Schemas", new FontIcon(MaterialDesignF.FILE_TABLE_BOX_MULTIPLE_OUTLINE));
                    databaseItem.getChildren().add(schemaMainItem);

                    for (Map.Entry<String, LinkedHashMap<String, ArrayList<ColumnData>>> schema : database.getValue().entrySet()) {
                        CustomTreeItem schemaItem = new CustomTreeItem(schema.getKey(), new FontIcon(MaterialDesignF.FILE_TABLE_BOX_OUTLINE));
                        schemaMainItem.getChildren().add(schemaItem);

                        TablesTreeItem tablesMainTreeItem = new TablesTreeItem("Tables", new FontIcon(MaterialDesignT.TABLE_MULTIPLE));
                        tablesMainTreeItem.setDatabaseName(schema.getKey());
                        schemaItem.getChildren().add(tablesMainTreeItem);

                        for (Map.Entry<String, ArrayList<ColumnData>> table : schema.getValue().entrySet()) {
                            TableTreeItem tableItem = new TableTreeItem(table.getKey(), new FontIcon(MaterialDesignT.TABLE));
                            tablesMainTreeItem.getChildren().add(tableItem);

                            for (ColumnData column : table.getValue()) {
                                ColumnTreeItem columnItem = new ColumnTreeItem(column.getColumnName(),
                                        new FontIcon(column.isPrimaryKey() ? MaterialDesignK.KEY : MaterialDesignT.TABLE_COLUMN));
                                columnItem.setDatabaseName(schema.getKey());
                                tableItem.getChildren().add(columnItem);
                            }
                        }
                    }
                }
            }
            this.getChildren().addAll(createHeaderBox(), databaseTree);

        } else {
            RootTreeItem databasesMainTreeItem = new RootTreeItem(
                    DatabaseConnection.getInstance().getConnectionObject().getConnectionName(),
                    new FontIcon(MaterialDesignD.DATABASE));
            databasesMainTreeItem.setExpanded(true);

            databaseTree = new TreeView<>(databasesMainTreeItem);

            for (String database : databaseStringList) {
                DatabaseTreeItem databaseItem = new DatabaseTreeItem(database, new FontIcon(MaterialDesignH.HOCKEY_PUCK));
                databaseItem.setDatabaseName(database);
                databasesMainTreeItem.getChildren().add(databaseItem);
                databasesMap.put(database.toLowerCase(), databaseItem);

                TablesTreeItem tablesMainTreeItem = new TablesTreeItem("Tables", new FontIcon(MaterialDesignT.TABLE_MULTIPLE));
                tablesMainTreeItem.setDatabaseName(database);

                Map<String, ArrayList<ColumnData>> tablesAndColumnsMap = DatabaseConnection.getInstance()
                        .getConnectionObject().getAllTablesAndColumns(database);
                structureCache.addDatabase(database, tablesAndColumnsMap);
                addTablesAndColumns(tablesAndColumnsMap, tablesMainTreeItem, database);

                databaseItem.getChildren().add(tablesMainTreeItem);
            }
            this.getChildren().addAll(createHeaderBox(), databaseTree);

            if (DatabaseConnection.getInstance().getConnectionObject().getDatabaseName() != null
                    && !DatabaseConnection.getInstance().getConnectionObject().getDatabaseName().isEmpty()) {
                try {
                    String useDb = "USE " + DatabaseConnection.getInstance().getConnectionObject().getDatabaseName();
                    PreparedStatement tableStatement = DatabaseConnection.getInstance().getConnection().prepareStatement(useDb);
                    tableStatement.execute();
                } catch (SQLException e) {
                }
                databaseTree.getSelectionModel().select(1);
                selectedDatabase = databasesMap.get(
                        DatabaseConnection.getInstance().getConnectionObject().getDatabaseName());
                selectedDatabase.setSelected();

            } else if (DatabaseConnection.getInstance().getConnectionObject().getDatabaseName() == null
                    && DatabaseConnection.getInstance().getConnectionObject().getDbType().equals(ConnectionTypes.H2.toString())) {
                try {
                    String useDb = "SELECT SCHEMA();";
                    PreparedStatement pathStatement = DatabaseConnection.getInstance().getConnection().prepareStatement(useDb);
                    ResultSet path = pathStatement.executeQuery();
                    path.next();

                    String defaultSchema = path.getString(1);
                    DatabaseTreeItem schemaItem = databasesMap.get(defaultSchema.toLowerCase());

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
                            selectedItem.getContextMenu().show(selectedItem.getGraphic(),
                                    event.getScreenX(), event.getScreenY());
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
                    searchService.restart();
                } else {
                    searchService.cancel();
                    clearFilter();
                }
            }
        });

        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hideSearchField();
            }
        });
    }

    private HBox createSearchHeaderBox() {
        HBox searchHeaderBox = new HBox();
        searchHeaderBox.setSpacing(5);
        searchHeaderBox.setPadding(new Insets(5, 5, 5, 5));
        searchHeaderBox.setStyle("-fx-border-width: 0px 0px 2px 0px; -fx-border-color: -color-border-default;");
        searchHeaderBox.setAlignment(Pos.CENTER_LEFT);

        searchControlStack = new StackPane();
        searchControlStack.setAlignment(Pos.CENTER);

        searchProgress = new ProgressIndicator();
        searchProgress.setMinSize(20, 20);
        searchProgress.setMaxSize(20, 20);
        searchProgress.visibleProperty().bind(searchService.runningProperty());

        closeSearchButton = new Button();
        closeSearchButton.setGraphic(new FontIcon(org.kordamp.ikonli.materialdesign2.MaterialDesignC.CLOSE));
        closeSearchButton.getStyleClass().addAll(Styles.FLAT);
        closeSearchButton.setPadding(new Insets(5, 5, 5, 5));
        closeSearchButton.setOnAction(event -> hideSearchField());
        closeSearchButton.visibleProperty().bind(searchService.runningProperty().not());

        searchControlStack.getChildren().addAll(searchProgress, closeSearchButton);

        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchHeaderBox.getChildren().addAll(searchField, searchControlStack);
        return searchHeaderBox;
    }

    private HBox createNormalHeaderBox() {
        HBox normalHeaderBox = new HBox();
        normalHeaderBox.setSpacing(5);
        normalHeaderBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon databaseViewIcon = new FontIcon(MaterialDesignD.DATABASE);
        IconColorUtil.apply(databaseViewIcon, "-color-fg-default", 20);
        Button databaseViewButton = new Button("Databases", databaseViewIcon);
        databaseViewButton.getStyleClass().addAll(Styles.FLAT);
        databaseViewButton.setPadding(new Insets(5, 5, 5, 5));
        if (!isUsersViewActive) {
            databaseViewButton.setStyle("-fx-background-color: -color-accent-subtle;");
        }
        databaseViewButton.setOnAction(event -> switchToDatabaseView());

        FontIcon usersViewIcon = new FontIcon(MaterialDesignA.ACCOUNT_MULTIPLE);
        IconColorUtil.apply(usersViewIcon, "-color-fg-default", 20);
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
        IconColorUtil.apply(refreshIcon, "-color-fg-default", 20);
        Button refresh = new Button(null, refreshIcon);
        refresh.getStyleClass().addAll(Styles.FLAT);
        refresh.setPadding(new Insets(5, 5, 5, 5));
        refresh.setOnAction(event -> {
            searchService.cancel();
            this.initialize();
        });

        FontIcon searchIcon = new FontIcon(MaterialDesignM.MAGNIFY);
        IconColorUtil.apply(searchIcon, "-color-fg-default", 20);
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

            CustomTreeItem usersRootItem = new CustomTreeItem(
                    DatabaseConnection.getInstance().getConnectionObject().getConnectionName(),
                    new FontIcon(MaterialDesignA.ACCOUNT_MULTIPLE));
            usersRootItem.setExpanded(true);

            if (users != null) {
                for (String user : users) {
                    usersRootItem.getChildren().add(new UserTreeItem(user, new FontIcon(MaterialDesignA.ACCOUNT)));
                }
            }

            usersTree = new TreeView<>(usersRootItem);
            VBox.setVgrow(usersTree, Priority.ALWAYS);

        } catch (SQLException e) {
            usersTree = new TreeView<>();
        }
        usersContainer.getChildren().add(usersTree);
        return usersContainer;
    }

    private void showSearchField() {
        if (!isSearchMode) {
            headerBox.getChildren().clear();
            headerBox.getChildren().addAll(createSearchHeaderBox().getChildren());

            searchField.setPromptText(isUsersViewActive ? "Search users..." : "Search tables and columns...");
            searchField.requestFocus();
            isSearchMode = true;
        } else {
            hideSearchField();
        }
    }

    private void hideSearchField() {
        if (isSearchMode) {
            searchField.clear();
            searchService.cancel();
            clearFilter();

            headerBox.getChildren().clear();
            headerBox.getChildren().addAll(createNormalHeaderBox().getChildren());

            isSearchMode = false;
        }
    }

    private TreeItem<String> createFilteredTree(TreeItem<String> original, String searchText, Task<?> task) {
        if (original == null || task.isCancelled()) return null;

        if (original instanceof CustomTreeItem customOriginal) {
            String itemText = customOriginal.getTitleLabel().getText().toLowerCase();

            CustomTreeItem filteredItem = cloneTreeItemType(customOriginal);

            boolean hasMatchingChildren = false;
            for (TreeItem<String> child : original.getChildren()) {
                if (task.isCancelled()) return null;
                TreeItem<String> filteredChild = createFilteredTree(child, searchText, task);
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

    private CustomTreeItem cloneTreeItemType(CustomTreeItem original) {
        String name = original.getTitleLabel().getText();
        FontIcon icon = new FontIcon(original.getIcon().getIconCode());

        CustomTreeItem clone;
        if (original instanceof RootTreeItem) {
            clone = new RootTreeItem(name, icon);
        } else if (original instanceof DatabaseTreeItem) {
            clone = new DatabaseTreeItem(name, icon);
        } else if (original instanceof SchemasTreeItem) {
            clone = new SchemasTreeItem(name, icon);
        } else if (original instanceof TablesTreeItem) {
            clone = new TablesTreeItem(name, icon);
        } else if (original instanceof TableTreeItem) {
            clone = new TableTreeItem(name, icon);
        } else if (original instanceof ColumnTreeItem) {
            clone = new ColumnTreeItem(name, icon);
        } else if (original instanceof UserTreeItem) {
            clone = new UserTreeItem(name, icon);
        } else {
            clone = new CustomTreeItem(name, icon);
        }

        clone.setDatabaseName(original.getDatabaseName());
        return clone;
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
                    Platform.runLater(() -> databaseTree.getSelectionModel().select(selectedDatabase));
                }
            }
        }
    }

    public HBox createHeaderBox() {
        headerBox = new HBox();
        headerBox.setSpacing(5);
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

            TableTreeItem tableItem = new TableTreeItem(tableName, new FontIcon(MaterialDesignT.TABLE));
            tableItem.setDatabaseName(databaseName);
            tablesMainTreeItem.getChildren().add(tableItem);

            for (ColumnData column : columns) {
                ColumnTreeItem columnItem = new ColumnTreeItem(column.getColumnName(),
                        new FontIcon(!column.isPrimaryKey() ? MaterialDesignT.TABLE_COLUMN : MaterialDesignK.KEY));
                columnItem.setDatabaseName(databaseName);
                tableItem.getChildren().add(columnItem);
            }
        }
    }

    public TreeItem<String> createPostgresTreeItem() {
        DatabaseTreeItem databaseItem = new DatabaseTreeItem(
                DatabaseConnection.getInstance().getConnectionObject().getDatabaseName(),
                new FontIcon(MaterialDesignH.HOCKEY_PUCK));
        databaseItem.setDatabaseName(DatabaseConnection.getInstance().getConnectionObject().getDatabaseName());
        databaseItem.setExpanded(true);

        databaseTree = new TreeView<>(databaseItem);

        SchemasTreeItem schemaMainItem = new SchemasTreeItem("Schemas", new FontIcon(MaterialDesignF.FILE_TABLE_BOX_MULTIPLE_OUTLINE));
        schemaMainItem.setExpanded(true);
        databaseItem.getChildren().add(schemaMainItem);

        PostgresConnection postgres = (PostgresConnection) DatabaseConnection.getInstance().getConnectionObject();
        ArrayList<String> schemasList = postgres.getSchemas();

        for (String schema : schemasList) {
            DatabaseTreeItem schemaItem = new DatabaseTreeItem(schema, new FontIcon(MaterialDesignF.FILE_TABLE_BOX_OUTLINE));
            schemaItem.setDatabaseName(schema);
            schemaMainItem.getChildren().add(schemaItem);
            databasesMap.put(schema.toLowerCase(), schemaItem);

            TablesTreeItem tablesMainTreeItem = new TablesTreeItem("Tables", new FontIcon(MaterialDesignT.TABLE_MULTIPLE));
            tablesMainTreeItem.setDatabaseName(schema);
            schemaItem.getChildren().add(tablesMainTreeItem);

            LinkedHashMap<String, ArrayList<ColumnData>> tablesAndColumnsMap = postgres.getAllTablesAndColumns(schema);
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
            DatabaseTreeItem item = databasesMap.get(database.toLowerCase());
            databaseTree.getSelectionModel().select(item);
            item.setSelected();
            selectedDatabase = item;
        }
    }

    public DatabaseStructureCache getStructureCache() {
        return structureCache;
    }
}