package com.queryexe.components.modals;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.io.*;
import java.util.*;

import com.queryexe.queryexe.Launcher;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignE;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignU;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import com.google.gson.JsonObject;
import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.home.ConnectionsPane;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.model.drivers.DriverAPIs;
import com.queryexe.model.drivers.DriverInfo;
import com.queryexe.queryexe.App;
import com.queryexe.service.ConnectionService;

@Slf4j
public class AddConnectionModal extends VBox {

    private TextField portField;
    private ComboBox<ConnectionTypes> dbTypeComboBox;
    private TextField nameField;
    private TextField hostField;
    private TextField databaseNameField;
    private TextField usernameField;
    private PasswordTextField passwordField;
    private CheckBox savePasswordCheckBox;
    private TextField jdbcUrlField;
    private ConnectionObject connection;
    private CheckBox useCustomUrlCheckBox;

    private Label typeLabel;
    private Label nameLabel;
    private Label hostLabel;
    private Label portLabel;
    private Label databaseNameLabel;
    private Label usernameLabel;
    private Label passwordLabel;
    private Label jdbcUrlLabel;

    private GridPane grid;
    private Button hidePasswordButton;
    private FontIcon hidePasswordIcon;

    private StackPane contentPane;
    private HBox tabHeaders;
    private VBox connectionTab;
    private VBox driversTab;

    private DriverInfo selectedDriverInfo = null;

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

    public AddConnectionModal() {
        this.setAlignment(Pos.CENTER);
        this.setStyle("-fx-background-color: -color-bg-overlay; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-border-color: -color-border-default; -fx-border-width: 1px;");
        this.setMaxSize(710, 540);
        this.setMinSize(710, 540);
        this.setPrefSize(710, 540);
        this.setTranslateY(10);

        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_RIGHT);
        headerBox.setPadding(new Insets(10, 10, 0, 0));

        Region headerFillerRegion = new Region();

        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(event -> {
            App.closeModal();
        });

        headerBox.getChildren().addAll(headerFillerRegion, closeButton);
        HBox.setHgrow(headerFillerRegion, Priority.ALWAYS);

        initializeFields();
        initializeLabels();

        setupGrid();

        setupTabs();

        Button saveConnection = new Button();
        saveConnection.setText("Save");
        saveConnection.getStyleClass().add(Styles.SMALL);
        saveConnection.setDefaultButton(true);
        saveConnection.setOnAction(event -> {
            saveConnectionAction();
        });

        Button testConnection = new Button();
        testConnection.setText("Test Connection");
        testConnection.getStyleClass().add(Styles.SMALL);
        testConnection.addEventFilter(ActionEvent.ACTION, event -> {
            testConnectionAction();
            event.consume();
        });

        HBox buttonsBox = new HBox();
        buttonsBox.setPadding(new Insets(0, 10, 10, 0));
        buttonsBox.setSpacing(10);
        buttonsBox.setAlignment(Pos.CENTER_RIGHT);
        buttonsBox.getChildren().addAll(saveConnection, testConnection);

        VBox.setVgrow(contentPane, Priority.ALWAYS);

        this.getChildren().addAll(headerBox, tabHeaders, contentPane, buttonsBox);

        ConnectionTypes selectedType = dbTypeComboBox.getSelectionModel().getSelectedItem();
        if (selectedType.isFileBased()) {
            showFileBasedFields();
        } else {
            showServerBasedFields();
        }

        updateDriversTab();
    }

    private void setupTabs() {
        tabHeaders = new HBox();
        tabHeaders.setAlignment(Pos.CENTER);
        tabHeaders.setSpacing(5);
        tabHeaders.setPadding(new Insets(0, 5, 0, 5));

        contentPane = new StackPane();

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        connectionTab = new VBox(scrollPane);
        connectionTab.setPadding(new Insets(10, 0, 0, 0));
        connectionTab.setAlignment(Pos.CENTER);

        driversTab = new VBox();
        driversTab.setPadding(new Insets(20));
        driversTab.setAlignment(Pos.TOP_CENTER);

        contentPane.getChildren().add(connectionTab);

        Button connectionTabButton = createTabHeader("Connection", connectionTab, new FontIcon(MaterialDesignC.CONNECTION));
        connectionTabButton.setStyle(activeTab);

        Button driversTabButton = createTabHeader("Drivers", driversTab, new FontIcon(MaterialDesignP.PACKAGE_VARIANT));
        driversTabButton.setStyle(inactiveTab);

        tabHeaders.getChildren().addAll(connectionTabButton, driversTabButton);
    }

    private Button createTabHeader(String title, Node content, FontIcon icon) {
        Button tabHeader = new Button(title, icon);
        tabHeader.setMinWidth(685 / 2);
        tabHeader.setMaxWidth(685 / 2);

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

    private void initializeFields() {
        ConnectionTypes[] connectionTypes = ConnectionTypes.values();
        portField = new TextField();
        portField.setMinWidth(400);
        portField.setMaxWidth(400);
        portField.setText(connectionTypes[0].getDefaultPort());

        dbTypeComboBox = new ComboBox<ConnectionTypes>();
        dbTypeComboBox.setMinWidth(400);
        dbTypeComboBox.setMaxWidth(400);
        dbTypeComboBox.getItems().addAll(connectionTypes);
        dbTypeComboBox.getSelectionModel().selectFirst();
        dbTypeComboBox.setOnAction(event -> {
            ConnectionTypes selectedType = dbTypeComboBox.getSelectionModel().getSelectedItem();
            boolean wasCustomMode = useCustomUrlCheckBox.isSelected();

            if (selectedType.isFileBased()) {
                showFileBasedFields();
                jdbcUrlField.setText(selectedType.getJdbcPrefix());
            } else {
                if (wasCustomMode) {
                    showServerBasedCustomUrlFields();
                    useCustomUrlCheckBox.setSelected(true);
                    // Update JDBC URL when changing database type in custom mode
                    if (this.connection == null || !this.connection.getDbType().equals(selectedType.toString())) {
                        jdbcUrlField.setText(selectedType.getJdbcPrefix());
                    }
                } else {
                    showServerBasedFields();
                    portField.setText(selectedType.getDefaultPort());
                }
            }

            updateDriversTab();
        });

        nameField = new TextField();
        nameField.setMinWidth(400);
        nameField.setMaxWidth(400);

        hostField = new TextField();
        hostField.setMinWidth(400);
        hostField.setMaxWidth(400);

        databaseNameField = new TextField();
        databaseNameField.setMinWidth(400);
        databaseNameField.setMaxWidth(400);

        usernameField = new TextField();
        usernameField.setMinWidth(400);
        usernameField.setMaxWidth(400);

        passwordField = new PasswordTextField();
        passwordField.setMinWidth(400);
        passwordField.setMaxWidth(400);

        jdbcUrlField = new TextField();
        jdbcUrlField.setMinWidth(400);
        jdbcUrlField.setMaxWidth(400);
        jdbcUrlField.setText("jdbc:");

        savePasswordCheckBox = new CheckBox("Save Password");

        useCustomUrlCheckBox = new CheckBox("Use Custom JDBC URL");
        useCustomUrlCheckBox.setOnAction(e -> {
            ConnectionTypes selectedType = dbTypeComboBox.getSelectionModel().getSelectedItem();
            if (selectedType != null && !selectedType.isFileBased()) {
                if (useCustomUrlCheckBox.isSelected()) {
                    // Generate JDBC URL from current fields before switching
                    if (this.connection != null && !this.connection.isCustomJdbc()) {
                        String baseUrl = selectedType.getBaseUrl();
                        String host = hostField.getText().isEmpty() ? "localhost" : hostField.getText();
                        String port = portField.getText().isEmpty() ? selectedType.getDefaultPort() : portField.getText();
                        String dbName = databaseNameField.getText().isEmpty() ? "" : databaseNameField.getText();
                        jdbcUrlField.setText(String.format(baseUrl, host, port, dbName));
                    } else if (this.connection != null && this.connection.isCustomJdbc()) {
                        jdbcUrlField.setText(this.connection.getFullUrl());
                    } else {
                        jdbcUrlField.setText(selectedType.getJdbcPrefix());
                    }
                    showServerBasedCustomUrlFields();
                } else {
                    showServerBasedFields();
                }
            }
        });

        hidePasswordIcon = new FontIcon(MaterialDesignE.EYE_OFF);
        hidePasswordButton = new Button(null, hidePasswordIcon);
        hidePasswordButton.setStyle("-fx-padding: 0 0 0 0;");
        hidePasswordButton.getStyleClass().addAll(Styles.FLAT, "no-hover");
        hidePasswordButton.setOnAction(e -> {
            hidePasswordIcon.setIconCode(passwordField.getRevealPassword() ? MaterialDesignE.EYE_OFF : MaterialDesignE.EYE);
            passwordField.setRevealPassword(!passwordField.getRevealPassword());
        });
    }

    private void initializeLabels() {
        typeLabel = new Label("Database Type");
        typeLabel.setMinWidth(Region.USE_PREF_SIZE);
        typeLabel.setMinHeight(Region.USE_PREF_SIZE);
        typeLabel.setStyle("-fx-font-weight: bold;");

        nameLabel = new Label("Connection Name");
        nameLabel.setMinWidth(Region.USE_PREF_SIZE);
        nameLabel.setMinHeight(Region.USE_PREF_SIZE);
        nameLabel.setStyle("-fx-font-weight: bold;");

        hostLabel = new Label("Host");
        hostLabel.setMinWidth(Region.USE_PREF_SIZE);
        hostLabel.setMinHeight(Region.USE_PREF_SIZE);
        hostLabel.setStyle("-fx-font-weight: bold;");

        portLabel = new Label("Port");
        portLabel.setMinWidth(Region.USE_PREF_SIZE);
        portLabel.setMinHeight(Region.USE_PREF_SIZE);
        portLabel.setStyle("-fx-font-weight: bold;");

        databaseNameLabel = new Label("Database Name");
        databaseNameLabel.setMinWidth(Region.USE_PREF_SIZE);
        databaseNameLabel.setMinHeight(Region.USE_PREF_SIZE);
        databaseNameLabel.setStyle("-fx-font-weight: bold;");

        usernameLabel = new Label("Username");
        usernameLabel.setMinWidth(Region.USE_PREF_SIZE);
        usernameLabel.setMinHeight(Region.USE_PREF_SIZE);
        usernameLabel.setStyle("-fx-font-weight: bold;");

        passwordLabel = new Label("Password");
        passwordLabel.setMinWidth(Region.USE_PREF_SIZE);
        passwordLabel.setMinHeight(Region.USE_PREF_SIZE);
        passwordLabel.setStyle("-fx-font-weight: bold;");

        jdbcUrlLabel = new Label("JDBC URL");
        jdbcUrlLabel.setMinWidth(Region.USE_PREF_SIZE);
        jdbcUrlLabel.setMinHeight(Region.USE_PREF_SIZE);
        jdbcUrlLabel.setStyle("-fx-font-weight: bold;");
    }

    private void setupGrid() {
        grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setMinHeight(Region.USE_PREF_SIZE);
        grid.setMaxHeight(Region.USE_PREF_SIZE);
        grid.setAlignment(Pos.CENTER);
        grid.setPadding(new Insets(10, 0, 10, 0));

        ColumnConstraints col0 = new ColumnConstraints();
        col0.setHalignment(HPos.RIGHT);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHalignment(HPos.LEFT);

        grid.getColumnConstraints().addAll(col0, col1);
    }

    private void showFileBasedFields() {
        grid.getChildren().clear();

        ConnectionTypes selectedType = dbTypeComboBox.getSelectionModel().getSelectedItem();

        grid.add(new FontIcon(MaterialDesignD.DATABASE_COG), 0, 0);
        grid.add(typeLabel, 1, 0);
        grid.add(dbTypeComboBox, 2, 0);

        grid.add(new FontIcon(MaterialDesignC.CONNECTION), 0, 1);
        grid.add(nameLabel, 1, 1);
        grid.add(nameField, 2, 1);

        grid.add(new FontIcon(MaterialDesignL.LINK_VARIANT), 0, 2);
        grid.add(jdbcUrlLabel, 1, 2);
        grid.add(jdbcUrlField, 2, 2);

        if (selectedType == ConnectionTypes.H2) {
            grid.add(new FontIcon(MaterialDesignA.ACCOUNT), 0, 3);
            grid.add(usernameLabel, 1, 3);
            grid.add(usernameField, 2, 3);

            grid.add(new FontIcon(MaterialDesignD.DATABASE_LOCK), 0, 4);
            grid.add(passwordLabel, 1, 4);
            grid.add(passwordField, 2, 4);
            grid.add(hidePasswordButton, 3, 4);

            grid.add(savePasswordCheckBox, 2, 5);
        }
    }

    private void showServerBasedFields() {
        grid.getChildren().clear();

        grid.add(new FontIcon(MaterialDesignD.DATABASE_COG), 0, 0);
        grid.add(typeLabel, 1, 0);
        grid.add(dbTypeComboBox, 2, 0);

        grid.add(new FontIcon(MaterialDesignC.CONNECTION), 0, 1);
        grid.add(nameLabel, 1, 1);
        grid.add(nameField, 2, 1);

        grid.add(new FontIcon(MaterialDesignS.SERVER_NETWORK), 0, 2);
        grid.add(hostLabel, 1, 2);
        grid.add(hostField, 2, 2);

        grid.add(new FontIcon(MaterialDesignE.ETHERNET), 0, 3);
        grid.add(portLabel, 1, 3);
        grid.add(portField, 2, 3);

        grid.add(new FontIcon(MaterialDesignD.DATABASE_EDIT), 0, 4);
        grid.add(databaseNameLabel, 1, 4);
        grid.add(databaseNameField, 2, 4);

        grid.add(new FontIcon(MaterialDesignA.ACCOUNT), 0, 5);
        grid.add(usernameLabel, 1, 5);
        grid.add(usernameField, 2, 5);

        grid.add(new FontIcon(MaterialDesignD.DATABASE_LOCK), 0, 6);
        grid.add(passwordLabel, 1, 6);
        grid.add(passwordField, 2, 6);
        grid.add(hidePasswordButton, 3, 6);

        grid.add(savePasswordCheckBox, 2, 7);
        grid.add(useCustomUrlCheckBox, 2, 8);
    }

    private void showServerBasedCustomUrlFields() {
        grid.getChildren().clear();

        grid.add(new FontIcon(MaterialDesignD.DATABASE_COG), 0, 0);
        grid.add(typeLabel, 1, 0);
        grid.add(dbTypeComboBox, 2, 0);

        grid.add(new FontIcon(MaterialDesignC.CONNECTION), 0, 1);
        grid.add(nameLabel, 1, 1);
        grid.add(nameField, 2, 1);

        grid.add(new FontIcon(MaterialDesignL.LINK_VARIANT), 0, 2);
        grid.add(jdbcUrlLabel, 1, 2);
        grid.add(jdbcUrlField, 2, 2);

        grid.add(new FontIcon(MaterialDesignA.ACCOUNT), 0, 3);
        grid.add(usernameLabel, 1, 3);
        grid.add(usernameField, 2, 3);

        grid.add(new FontIcon(MaterialDesignD.DATABASE_LOCK), 0, 4);
        grid.add(passwordLabel, 1, 4);
        grid.add(passwordField, 2, 4);
        grid.add(hidePasswordButton, 3, 4);

        grid.add(savePasswordCheckBox, 2, 5);
        grid.add(useCustomUrlCheckBox, 2, 6);
    }

    private void saveConnectionAction() {
        String connectionName = nameField.getText();
        String dbType = dbTypeComboBox.getSelectionModel().getSelectedItem().toString();
        String host = null;
        String port = null;
        String databaseName = null;
        String customUrl = null;

        ConnectionTypes selectedType = dbTypeComboBox.getSelectionModel().getSelectedItem();

        if (selectedType.isFileBased() || (useCustomUrlCheckBox.isSelected() && !selectedType.isFileBased())) {
            customUrl = jdbcUrlField.getText();
        } else {
            host = hostField.getText();
            port = portField.getText();
            databaseName = databaseNameField.getText();
        }

        String username = usernameField.getText();
        String password = passwordField.getPassword();

        if (selectedType.equals(ConnectionTypes.SQLServer) && customUrl != null) {
            if (!customUrl.toLowerCase().contains("selectmethod=cursor")) {
                customUrl += (customUrl.contains("?") ? ";" : ";") + "SelectMethod=cursor";
            }
        }

        JsonObject connectionObj = (connection == null)
                ? new JsonObject()
                : ConnectionService.getInstance().getConnection(connection.getId());

        connectionObj.addProperty("connectionName", connectionName);
        connectionObj.addProperty("dbType", dbType);
        connectionObj.addProperty("host", host);
        connectionObj.addProperty("port", port);
        connectionObj.addProperty("databaseName", databaseName);
        connectionObj.addProperty("username", username);

        if (savePasswordCheckBox.isSelected()) {
            connectionObj.addProperty("password", password);
        } else {
            connectionObj.remove("password");
            if (dbType.equals(ConnectionTypes.SQLite.toString())) {
                connectionObj.addProperty("password", "");
            }
        }

        connectionObj.addProperty("customUrl", customUrl);

        if (selectedDriverInfo != null) {
            JsonObject driverObj = new JsonObject();
            driverObj.addProperty("name", selectedDriverInfo.getName());
            driverObj.addProperty("version", selectedDriverInfo.getVersion());
            driverObj.addProperty("driverClassName", selectedDriverInfo.getDriverClass());
            driverObj.addProperty("downloadUrl", selectedDriverInfo.getDownloadUrl());
            driverObj.addProperty("jarFileName", selectedDriverInfo.getFileName());

            connectionObj.add("customDriver", driverObj);
        } else if (connection != null && connection.getDriverInfo() != null) {
            DriverInfo existingDriver = connection.getDriverInfo();
            ConnectionTypes defaultType = ConnectionTypes.valueOf(dbType);

            if (!existingDriver.equals(defaultType.getDefaultDriverInfo())) {
                JsonObject driverObj = new JsonObject();
                driverObj.addProperty("name", existingDriver.getName());
                driverObj.addProperty("version", existingDriver.getVersion());
                driverObj.addProperty("driverClassName", existingDriver.getDriverClass());
                driverObj.addProperty("downloadUrl", existingDriver.getDownloadUrl());
                driverObj.addProperty("jarFileName", existingDriver.getFileName());
                connectionObj.add("customDriver", driverObj);
            } else {
                connectionObj.remove("customDriver");
            }
        } else {
            connectionObj.remove("customDriver");
        }

        boolean isNew = (connection == null);
        String id = isNew ? UUID.randomUUID().toString() : connection.getId();
        ConnectionService.getInstance().saveConnection(id, connectionObj);

        App.closeModal();

        ConnectionsPane connectionsPane = App.getConnectionsPane();
        if (connectionsPane != null) {
            if (isNew) {
                connectionsPane.addConnection(id);
            } else {
                connectionsPane.updateConnection(id);
            }
        }

        CustomNotification notification = new CustomNotification("Connection Saved", "Your database connection was saved.", new FontIcon(MaterialDesignI.INFORMATION_OUTLINE));
        notification.showNotification();
    }


    private void testConnectionAction() {
        String username = usernameField.getText();
        String password = passwordField.getPassword();

        try {
            String fullUrl;
            ConnectionTypes selectedType = dbTypeComboBox.getSelectionModel().getSelectedItem();

            if (selectedType.isFileBased() || (useCustomUrlCheckBox.isSelected() && !selectedType.isFileBased())) {
                fullUrl = jdbcUrlField.getText();
            } else {
                String baseUrl = selectedType.getBaseUrl();
                String host = hostField.getText();
                String port = portField.getText();
                String databaseName = databaseNameField.getText();
                fullUrl = String.format(baseUrl, host, port, databaseName);
            }

            Connection connection = DriverManager.getConnection(fullUrl, username, password);
            if (connection != null) {
                connection.close();
                CustomNotification notification = new CustomNotification("Connection Successful", "The database connection was established correctly.", new FontIcon(MaterialDesignC.CHECK_CIRCLE_OUTLINE));
                notification.showNotificationOnCustomPane((StackPane) this.getParent());
            } else {
                CustomNotification notification = new CustomNotification("Connection Failed", "Could not establish a connection.", new FontIcon(MaterialDesignL.LAN_DISCONNECT));
                notification.showNotificationOnCustomPane((StackPane) this.getParent());
            }
        } catch (Exception e) {
            CustomNotification notification = new CustomNotification("Connection Failed", e.getMessage(), new FontIcon(MaterialDesignL.LAN_DISCONNECT));
            notification.showNotificationOnCustomPane((StackPane) this.getParent());
        }
    }

    public void setupFields(ConnectionObject connection) {
        this.connection = connection;
        ConnectionTypes connectionType = ConnectionTypes.valueOf(connection.getDbType());
        this.dbTypeComboBox.getSelectionModel().select(connectionType);

        this.nameField.setText(connection.getConnectionName());

        if (connectionType.isFileBased()) {
            showFileBasedFields();
            this.jdbcUrlField.setText(connection.getFullUrl());
            this.usernameField.setText(connection.getUsername());
            String password = connection.getPassword();
            if (password != null && !password.isEmpty()) {
                this.passwordField.setText(password);
                this.savePasswordCheckBox.setSelected(true);
            }
        } else {
            // Check if using custom URL by looking at host/port/databaseName
            if (connection.getHost() == null || connection.getHost().isEmpty()) {
                useCustomUrlCheckBox.setSelected(true);
                showServerBasedCustomUrlFields();
                this.jdbcUrlField.setText(connection.getFullUrl());
            } else {
                showServerBasedFields();
                this.portField.setText(connection.getPort());
                this.hostField.setText(connection.getHost());
                this.databaseNameField.setText(connection.getDatabaseName());
            }

            this.usernameField.setText(connection.getUsername());

            String password = connection.getPassword();
            if (password != null && !password.isEmpty()) {
                this.passwordField.setText(password);
                this.savePasswordCheckBox.setSelected(true);
            }
        }

        updateDriversTab();
    }

    private void updateDriversTab() {
        ConnectionTypes selectedType = dbTypeComboBox.getSelectionModel().getSelectedItem();
        if (selectedType == null) {
            return;
        }

        populateDriversTab(selectedType, selectedType.getDefaultDriverInfo());
    }

    private void populateDriversTab(ConnectionTypes connectionType, DriverInfo currentDriverInfo) {
        try {
            driversTab.getChildren().clear();
            driversTab.setSpacing(15);

            VBox currentDriverBox = new VBox(8);
            currentDriverBox.setStyle("-fx-border-color: -color-border-default; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 15px; -fx-background-color: -color-accent-7-alpha10;");

            Label currentDriverTitle = new Label("Current Driver:");
            currentDriverTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            boolean isUsingDefault = currentDriverInfo.equals(connectionType.getDefaultDriverInfo());

            String driverStatus = isUsingDefault ? "Default Driver" : "Custom Driver";
            String driverVersion = currentDriverInfo.getVersion();

            Label currentDriverLabel = new Label(driverStatus + " - " + driverVersion);
            currentDriverLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (isUsingDefault ? "gray" : "-color-border-default") + ";");

            currentDriverBox.getChildren().addAll(currentDriverTitle, currentDriverLabel);

            Region separator = new Region();
            separator.setMinHeight(1);
            separator.setMaxHeight(1);
            separator.setStyle("-fx-background-color: -color-border-default;");

            VBox driverSelectionBox = new VBox(10);
            driverSelectionBox.setMaxWidth(Double.MAX_VALUE);
            driverSelectionBox.setAlignment(Pos.TOP_LEFT);

            Label selectionTitle = new Label("Change Driver Version:");
            selectionTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            ComboBox<DriverInfo> driverInfoComboBox = new ComboBox<>();
            driverInfoComboBox.setPromptText("Select driver version...");
            driverInfoComboBox.setMaxWidth(Double.MAX_VALUE);

            driverInfoComboBox.setCellFactory(param -> new ListCell<DriverInfo>() {
                @Override
                protected void updateItem(DriverInfo item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        File driverFile = Launcher.getJdbcDriversDirectory().resolve(item.getFileName()).toFile();
                        boolean isDownloaded = driverFile.exists();
                        String status = isDownloaded ? " ✓" : "";
                        setText(item.getVersion() + status);

                        String defaultStyle = isDownloaded ? "-fx-text-fill: -color-accent-emphasis;" : "-fx-text-fill: -color-fg-default;";
                        String selectedStyle = "-fx-text-fill: -color-fg-default; -fx-background-color: -color-accent-subtle;";

                        setStyle(defaultStyle);

                        setOnMouseEntered(e -> setStyle(selectedStyle));
                        setOnMouseExited(e -> setStyle(isSelected() ? selectedStyle : defaultStyle));

                        selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                            if (isNowSelected) {
                                setStyle(selectedStyle);
                            } else if (!isHover()) {
                                setStyle(defaultStyle);
                            }
                        });
                    }
                }
            });

            driverInfoComboBox.setButtonCell(new ListCell<DriverInfo>() {
                @Override
                protected void updateItem(DriverInfo item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        File driverFile = Launcher.getJdbcDriversDirectory().resolve(item.getFileName()).toFile();
                        boolean isDownloaded = driverFile.exists();
                        String status = isDownloaded ? " ✓" : "";
                        setText(item.getVersion() + status);
                    }
                }
            });

            HBox buttonBox = new HBox(10);
            buttonBox.setAlignment(Pos.CENTER_LEFT);
            buttonBox.setMaxWidth(Double.MAX_VALUE);

            Button loadDriversButton = new Button("Check Available Drivers", new FontIcon(MaterialDesignU.UPLOAD));
            HBox.setHgrow(loadDriversButton, Priority.ALWAYS);
            loadDriversButton.setMaxWidth(Double.MAX_VALUE);

            Button downloadButton = new Button("Download Driver", new FontIcon(MaterialDesignD.DOWNLOAD));
            HBox.setHgrow(downloadButton, Priority.ALWAYS);
            downloadButton.setMaxWidth(Double.MAX_VALUE);
            downloadButton.setDisable(true);

            Button applyDriverButton = new Button("Apply Driver", new FontIcon(MaterialDesignC.CHECK));
            HBox.setHgrow(applyDriverButton, Priority.ALWAYS);
            applyDriverButton.setMaxWidth(Double.MAX_VALUE);
            applyDriverButton.setDisable(true);
            applyDriverButton.setStyle("-fx-background-color: -color-border-default;");

            buttonBox.getChildren().addAll(loadDriversButton, downloadButton, applyDriverButton);

            Label statusLabel = new Label("Click 'Check Available Drivers' to see available versions");
            statusLabel.setWrapText(true);
            statusLabel.setStyle("-fx-text-fill: gray;");

            loadDriversButton.setOnAction(e -> {
                loadDriversButton.setDisable(true);
                statusLabel.setText("Loading drivers from Maven Central...");

                DriverAPIs.getAllDriversForConnectionTypeAsync(
                        connectionType,
                        drivers -> {
                            Platform.runLater(() -> {
                                ObservableList<DriverInfo> driversList = FXCollections.observableArrayList(drivers);
                                driverInfoComboBox.setItems(driversList);
                                statusLabel.setText("Found " + drivers.size() + " driver versions. Select one to download or apply.");
                                statusLabel.setStyle("-fx-text-fill: gray;");
                                loadDriversButton.setDisable(false);
                                if (!drivers.isEmpty()) {
                                    driverInfoComboBox.getSelectionModel().selectFirst();
                                }
                            });
                        },
                        ex -> {
                            Platform.runLater(() -> {
                                statusLabel.setText("Failed to load drivers: " + ex.getMessage());
                                statusLabel.setStyle("-fx-text-fill: red;");
                                loadDriversButton.setDisable(false);
                            });
                        }
                );
            });

            driverInfoComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    File driverFile = Launcher.getJdbcDriversDirectory().resolve(newVal.getFileName()).toFile();
                    boolean isDownloaded = driverFile.exists();

                    downloadButton.setDisable(isDownloaded);
                    applyDriverButton.setDisable(!isDownloaded);

                    if (isDownloaded) {
                        statusLabel.setText("Driver already downloaded. Click 'Apply Driver' to use version " + newVal.getVersion());
                        statusLabel.setStyle("-fx-text-fill: -color-success-emphasis;");
                    } else {
                        statusLabel.setText("Click 'Download Driver' to download version " + newVal.getVersion());
                        statusLabel.setStyle("-fx-text-fill: gray;");
                    }
                } else {
                    downloadButton.setDisable(true);
                    applyDriverButton.setDisable(true);
                }
            });

            downloadButton.setOnAction(e -> {
                DriverInfo selectedDriver = driverInfoComboBox.getSelectionModel().getSelectedItem();
                if (selectedDriver != null) {
                    File driverFile = Launcher.getJdbcDriversDirectory().resolve(selectedDriver.getFileName()).toFile();

                    downloadButton.setDisable(true);
                    statusLabel.setText("Downloading " + selectedDriver.getVersion() + "...");

                    DriverAPIs.downloadDriverAsync(
                            selectedDriver,
                            driverFile,
                            () -> {
                                Platform.runLater(() -> {
                                    statusLabel.setText("Successfully downloaded " + selectedDriver.getVersion() + ". Click 'Apply Driver' to use it.");
                                    statusLabel.setStyle("-fx-text-fill: -color-success-emphasis;");
                                    applyDriverButton.setDisable(false);
                                    driverInfoComboBox.setCellFactory(driverInfoComboBox.getCellFactory());
                                });
                            },
                            ex -> {
                                Platform.runLater(() -> {
                                    statusLabel.setText("Download failed: " + ex.getMessage());
                                    statusLabel.setStyle("-fx-text-fill: red;");
                                    downloadButton.setDisable(false);
                                });
                            }
                    );
                }
            });

            applyDriverButton.setOnAction(e -> {
                DriverInfo selectedDriver = driverInfoComboBox.getSelectionModel().getSelectedItem();
                if (selectedDriver != null) {
                    this.selectedDriverInfo = selectedDriver;

                    statusLabel.setText("Driver " + selectedDriver.getVersion() + " will be applied when you save the connection.");
                    statusLabel.setStyle("-fx-text-fill: -color-border-default; -fx-font-weight: bold;");

                    currentDriverLabel.setText("Custom Driver - " + selectedDriver.getVersion() + " (pending save)");
                    currentDriverLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -color-border-default;");
                }
            });

            driverSelectionBox.getChildren().addAll(
                    selectionTitle,
                    driverInfoComboBox,
                    buttonBox,
                    statusLabel
            );

            ScrollPane scrollPane = new ScrollPane(new VBox(15, currentDriverBox, separator, driverSelectionBox));
            scrollPane.setFitToWidth(true);
            scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
            scrollPane.setPadding(new Insets(10));
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            driversTab.getChildren().addAll(scrollPane);
        } catch (Exception e) {
            log.error("updateItem failed", e);
        }
    }
}