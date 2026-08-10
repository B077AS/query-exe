package com.queryexe.queryexe;

import lombok.extern.slf4j.Slf4j;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabDragPolicy;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import com.queryexe.components.home.ConnectionsPane;
import com.queryexe.components.menu.CustomMenuBar;
import com.queryexe.components.editor.CustomTab;
import com.queryexe.components.menu.CustomToolBar;
import com.queryexe.components.editor.SQLEditor;
import com.queryexe.components.tree.CustomTree;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.service.DatabaseConnection;
import com.queryexe.theme.ThemeManager;
import com.queryexe.update.LauncherUpdateService;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import atlantafx.base.controls.ModalPane;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.queryexe.utils.WindowsThemeUtil;
import com.queryexe.utils.TabScrollChevrons;

@Slf4j
public class App extends Application {

    private static Scene scene;
    private static StackPane stackPane;
    private static CustomToolBar toolbar;
    private static CustomMenuBar menuBar;
    private static TabPane tabPane;
    private static ConnectionsPane connectionsPane;
    private static SplitPane codeAndResultSplitPane;
    private static ModalPane modalPane;
    private static StackPane mainStackPane;
    private static VBox headerBox;
    private static CustomTree customTree;
    private static Map<ConnectionObject, CustomTree> databaseTreeCache = new HashMap<>();
    private static final ArrayDeque<ModalPane> modalPaneStack = new ArrayDeque<>();

    // Closing a modal removes its focused control (e.g. the X button) from the
    // scene, and JavaFX's focus-traversal engine then reassigns focus to the
    // first traversable control it finds - typically the home page's search
    // field. Restoring whatever had focus before the modal opened avoids that.
    private static Node baseModalPreviousFocus;
    private static final Map<ModalPane, Node> overlayPreviousFocus = new HashMap<>();

    @Override
    public void start(Stage stage) {

        Image appIcon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icon.png")));
        stage.getIcons().add(appIcon);

        stackPane = new StackPane();
        BorderPane borderPane = new BorderPane();

        headerBox = new VBox();
        headerBox.setAlignment(Pos.CENTER);

        toolbar = new CustomToolBar();

        menuBar = new CustomMenuBar();
        for (Menu menu : menuBar.getMenus()) {
            if (!isAlwaysEnabledMenu(menu)) {
                menu.setDisable(true);
            }
        }

        tabPane = new TabPane();
        tabPane.setTabDragPolicy(TabDragPolicy.REORDER);
        TabScrollChevrons.install(tabPane);

        modalPane = new ModalPane();
        modalPane.setPersistent(false);
        setupTransitions(modalPane);
        modalPane.displayProperty().addListener((obs, was, now) -> {
            if (!now && baseModalPreviousFocus != null) {
                Node toFocus = baseModalPreviousFocus;
                baseModalPreviousFocus = null;
                Platform.runLater(toFocus::requestFocus);
            }
        });

        connectionsPane = new ConnectionsPane();

        // The header's second row is a single shared slot: the connections
        // toolbar (search/new/refresh/view) while on the home page, swapped for
        // CustomToolBar (New/Open/Save/Run) once a connection is active. See
        // connect() and goHome().
        headerBox.getChildren().addAll(menuBar, connectionsPane.getToolbar());

        borderPane.setTop(headerBox);

        stackPane.getChildren().add(connectionsPane);
        borderPane.setCenter(stackPane);

        mainStackPane = new StackPane();
        mainStackPane.getChildren().addAll(borderPane, modalPane);

        scene = new Scene(mainStackPane, 1100, 500);
        Application.setUserAgentStylesheet(App.class.getClassLoader().getResource("style.css").toExternalForm());
        ThemeManager.get().applyFromSettings(scene);
        stage.setTitle("QueryExe");
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(640);
        stage.setMaximized(true);
        stage.setOpacity(0);
        stage.show();

        stage.setOnCloseRequest(event -> {
            try {
                DatabaseConnection.getInstance().shutdown();
                log.info("Connection Closed");
            } catch (Exception e) {
                log.warn("Error while shutting down connections", e);
            }
        });

        if (com.sun.jna.Platform.isWindows()) {
            Thread.ofVirtual().start(() -> {
                WindowsThemeUtil.enableDarkTitleBar(stage.getTitle());
                Platform.runLater(() -> stage.setOpacity(1));
            });
        } else {
            Platform.runLater(() -> stage.setOpacity(1));
        }

        new LauncherUpdateService().start();
    }

    public static void connect() {

        if (!tabPane.getTabs().isEmpty()) {
            Platform.runLater(() -> {
                tabPane.getTabs().clear();
            });
        }

        customTree = new CustomTree();
        SQLEditor codeArea = new SQLEditor();
        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<CodeArea>(codeArea);

        CustomTab<VirtualizedScrollPane<CodeArea>> queryTab = new CustomTab<>(scroll, tabPane, DatabaseConnection.getInstance().getCurrentConnectionObject());

        tabPane.getTabs().add(queryTab);
        tabPane.getSelectionModel().select(queryTab);
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            if (tabPane.getTabs().isEmpty()) {
                if (codeAndResultSplitPane.getItems().size() > 1) {
                    codeAndResultSplitPane.getItems().remove(1);
                }
            }
        });

        VBox.setVgrow(codeArea, Priority.ALWAYS);

        codeAndResultSplitPane = new SplitPane();
        codeAndResultSplitPane.setStyle("-fx-background-color: -color-bg-default;");
        codeAndResultSplitPane.setOrientation(Orientation.VERTICAL);
        codeAndResultSplitPane.getItems().add(tabPane);

        SplitPane splitpane = new SplitPane(customTree, codeAndResultSplitPane);
        splitpane.setOrientation(Orientation.HORIZONTAL);
        splitpane.setDividerPositions(0.15);
        splitpane.widthProperty().addListener((obs, oldVal, newVal) -> {
            double oldPosition = splitpane.getDividerPositions()[0];
            Platform.runLater(() -> {
                splitpane.setDividerPosition(0, oldPosition);
            });
        });

        Platform.runLater(() -> {
            for (Menu menu : menuBar.getMenus()) {
                menu.setDisable(false);
            }
            headerBox.getChildren().set(1, toolbar);
            stackPane.getChildren().clear();
            stackPane.getChildren().add(splitpane);
            codeArea.layout();
        });
    }

    public static CustomTree getDatabaseTreeForConnection(ConnectionObject connection) {
        customTree = databaseTreeCache.computeIfAbsent(connection, conn -> {
            return new CustomTree();
        });
        return customTree;
    }

    public static void removeDatabaseTreeForConnection(ConnectionObject connection) {
        databaseTreeCache.remove(connection);
    }

    /**
     * Displays a modal. If a modal is already showing, this stacks a new
     * {@link ModalPane} overlay on top (with a lower z-order than the previous
     * one) so modals can be layered indefinitely. Each overlay removes itself
     * from the stack and the scene when it is hidden.
     */
    public static void showModal(Node modal) {
        Node previousFocus = scene.getFocusOwner();

        if (modalPane.isDisplay() || !modalPaneStack.isEmpty()) {
            int nextOrder = ModalPane.Z_FRONT - (modalPaneStack.size() + 1) * 5;
            ModalPane overlay = new ModalPane(nextOrder);
            overlay.setPersistent(false);
            setupTransitions(overlay);
            overlayPreviousFocus.put(overlay, previousFocus);
            overlay.displayProperty().addListener((obs, was, now) -> {
                if (!now) {
                    modalPaneStack.remove(overlay);
                    mainStackPane.getChildren().remove(overlay);
                    Node toFocus = overlayPreviousFocus.remove(overlay);
                    if (toFocus != null) {
                        Platform.runLater(toFocus::requestFocus);
                    }
                }
            });
            mainStackPane.getChildren().add(overlay);
            overlay.applyCss();
            modalPaneStack.push(overlay);
            overlay.setContent(modal);
            modal.applyCss();
            Platform.runLater(() -> {
                overlay.setDisplay(true);
                modal.requestFocus();
            });
            return;
        }

        baseModalPreviousFocus = previousFocus;
        modalPane.setContent(modal);
        modal.applyCss();
        Platform.runLater(() -> {
            modalPane.setDisplay(true);
            modal.requestFocus();
        });
    }

    /**
     * Kept for source compatibility. Stacking is now automatic, so this simply
     * delegates to {@link #showModal(Node)}.
     */
    public static void showModalOnTop(Node modal) {
        showModal(modal);
    }

    /** Closes the top-most modal (a stacked overlay if present, otherwise the base modal). */
    public static void closeModal() {
        if (!modalPaneStack.isEmpty()) {
            modalPaneStack.peek().hide(true);
        } else {
            modalPane.hide(true);
        }
    }

    /** Closes every open modal, from the top of the stack down to the base modal. */
    public static void closeAllModals() {
        while (!modalPaneStack.isEmpty()) {
            modalPaneStack.pop().hide(true);
        }
        modalPane.hide(true);
    }

    /** The top-most modal pane currently in use (a stacked overlay, or the base pane). */
    public static ModalPane getTopModalPane() {
        return modalPaneStack.isEmpty() ? modalPane : modalPaneStack.peek();
    }

    private static void setupTransitions(ModalPane pane) {
        pane.setInTransitionFactory(node -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), node);
            scale.setFromX(0.92);
            scale.setFromY(0.92);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.setInterpolator(Interpolator.EASE_OUT);

            FadeTransition fade = new FadeTransition(Duration.millis(150), node);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.setInterpolator(Interpolator.EASE_OUT);

            return new ParallelTransition(scale, fade);
        });
        pane.setOutTransitionFactory(node -> {
            FadeTransition fade = new FadeTransition(Duration.millis(80), node);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setInterpolator(Interpolator.EASE_IN);
            return fade;
        });
    }

    public static void closeConnection() {
        goHome();

        try {
            DatabaseConnection.getInstance().closeAll();
        } catch (Exception e) {
        }
    }

    public static void goHome() {
        for (Menu menu : menuBar.getMenus()) {
            if (!isAlwaysEnabledMenu(menu)) {
                menu.setDisable(true);
            }
        }

        if (connectionsPane == null) {
            connectionsPane = new ConnectionsPane();
        }

        headerBox.getChildren().set(1, connectionsPane.getToolbar());

        if (!stackPane.getChildren().contains(connectionsPane)) {
            stackPane.getChildren().clear();
            stackPane.getChildren().add(connectionsPane);
        }
    }

    public static ConnectionsPane getConnectionsPane() {
        return connectionsPane;
    }

    public static StackPane getStackPane() {
        return stackPane;
    }

    public static TabPane getTabPane() {
        return tabPane;
    }

    public static SplitPane getCodeAndResultSplitPane() {
        return codeAndResultSplitPane;
    }

    public static VBox getHeaderBox() {
        return headerBox;
    }

    public static CustomTree getDatabaseTree() {
        return customTree;
    }

    public static ModalPane getModalPane() {
        return modalPane;
    }

    public static Scene getScene() {
        return scene;
    }

    public static void appStart(String[] args) {
        launch();
    }

    private static boolean isAlwaysEnabledMenu(Menu menu) {
        return "About".equals(menu.getText()) || "Settings".equals(menu.getText());
    }
}