package com.queryexe.components.home;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import com.queryexe.model.connections.ConnectionObject;
import com.queryexe.service.AppSettings;

import java.util.ArrayList;
import java.util.List;

import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import atlantafx.base.theme.Styles;
import com.queryexe.components.modals.AddConnectionModal;
import com.queryexe.service.ConnectionService;
import com.queryexe.queryexe.App;

public class ConnectionsPane extends VBox {

    public enum ViewMode { CARDS, LIST }

    private Pagination pagination;
    private BorderPane currentPage;
    private VBox listBox;
    private ScrollPane listScrollPane;
    private StackPane contentStack;
    private ToolBar toolbar;

    private final List<ConnectionCard> allCards = new ArrayList<>();
    private List<ConnectionCard> visible = allCards;
    private String lastSearch = "";

    private ViewMode viewMode;
    private ToggleButton cardsToggle;
    private ToggleButton listToggle;

    private double availableWidth;
    private double availableHeight;
    private int cardsPerRow;
    private int cardsPerColumn;
    private int cardsPerPage;

    public ConnectionsPane() {
        this.setAlignment(Pos.TOP_CENTER);
        this.setFillWidth(true);
        this.setFocusTraversable(true);
        VBox.setVgrow(this, Priority.ALWAYS);

        viewMode = loadViewMode();

        createConnectionCards();

        toolbar = buildToolbar();

        pagination = buildPagination();
        listScrollPane = buildListScrollPane();

        contentStack = new StackPane(pagination, listScrollPane);
        VBox.setVgrow(contentStack, Priority.ALWAYS);
        this.getChildren().add(contentStack);

        this.parentProperty().addListener((obs, oldParent, newParent) -> {
            if (newParent != null && newParent instanceof StackPane) {
                ((StackPane) newParent).widthProperty().addListener((obsWidth, oldWidth, newWidth) -> updatePagination());
                ((StackPane) newParent).heightProperty().addListener((obsHeight, oldHeight, newHeight) -> updatePagination());
            }
        });

        applyViewMode();
        Platform.runLater(() -> {
            updatePagination(0);
            // The search field would otherwise grab the scene's initial focus
            // just by being the first traversable control; steal it back.
            this.requestFocus();
        });
    }

    /**
     * The toolbar lives outside this pane: {@code App} swaps it into the shared
     * header slot in place of {@code CustomToolBar} while the home page is
     * showing, and back again once a connection is made. See {@code App.goHome()}
     * / {@code App.connect()}.
     */
    public ToolBar getToolbar() {
        return toolbar;
    }

    private ViewMode loadViewMode() {
        try {
            return ViewMode.valueOf(AppSettings.get().getConnectionsViewMode());
        } catch (Exception e) {
            return ViewMode.CARDS;
        }
    }

    private Pagination buildPagination() {
        Pagination p = new Pagination();
        p.setMaxPageIndicatorCount(5);
        p.setStyle("-fx-page-information-visible: false;");
        p.getStyleClass().add(Pagination.STYLE_CLASS_BULLET);
        p.setMaxWidth(Double.MAX_VALUE);
        p.setMaxHeight(Double.MAX_VALUE);

        p.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() < 0) {
                if (p.getCurrentPageIndex() < p.getPageCount() - 1) {
                    p.setCurrentPageIndex(p.getCurrentPageIndex() + 1);
                }
            } else if (event.getDeltaY() > 0) {
                if (p.getCurrentPageIndex() > 0) {
                    p.setCurrentPageIndex(p.getCurrentPageIndex() - 1);
                }
            }
            event.consume();
        });

        p.setPageFactory(this::buildPage);
        return p;
    }

    private ScrollPane buildListScrollPane() {
        listBox = new VBox(10);
        listBox.setAlignment(Pos.TOP_CENTER);
        listBox.setPadding(new Insets(0, 20, 20, 20));
        listBox.setFillWidth(true);
        listBox.setMaxWidth(900);

        StackPane centering = new StackPane(listBox);
        centering.setPadding(new Insets(10, 0, 0, 0));

        ScrollPane sp = new ScrollPane(centering);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent;");
        return sp;
    }

    /**
     * A real {@link ToolBar} rather than a Card: it reuses style.css's existing
     * flat, single-row `.tool-bar` styling (the same one {@code CustomToolBar}
     * uses), so this reads as a continuation of the app's chrome instead of a
     * heavy, separately-elevated box competing with the cards/rows below it.
     */
    private ToolBar buildToolbar() {
        FontIcon searchIcon = new FontIcon(Feather.SEARCH);
        searchIcon.getStyleClass().add(Styles.TEXT_MUTED);

        TextField searchField = new TextField();
        searchField.setPromptText("Search connections...");
        searchField.setStyle("-fx-background-color: transparent;");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterCards(newVal));

        HBox searchBox = new HBox(6, searchIcon, searchField);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPadding(new Insets(0, 0, 0, 8));
        HBox.setHgrow(searchBox, Priority.ALWAYS);
        searchBox.setMaxWidth(Double.MAX_VALUE);

        cardsToggle = new ToggleButton();
        cardsToggle.setGraphic(new FontIcon(Feather.GRID));
        cardsToggle.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.LEFT_PILL);
        cardsToggle.setTooltip(new Tooltip("Card view"));
        cardsToggle.setOnAction(e -> setViewMode(ViewMode.CARDS));

        listToggle = new ToggleButton();
        listToggle.setGraphic(new FontIcon(Feather.LIST));
        listToggle.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.RIGHT_PILL);
        listToggle.setTooltip(new Tooltip("List view"));
        listToggle.setOnAction(e -> setViewMode(ViewMode.LIST));

        Button addButton = new Button("New", new FontIcon(Feather.PLUS));
        addButton.setOnAction(e -> {
            StackPane tempPane = new StackPane();
            tempPane.setOnMouseClicked(eventStack -> App.closeModal());

            AddConnectionModal modal = new AddConnectionModal();
            modal.addEventFilter(MouseEvent.MOUSE_CLICKED, eventModal -> eventModal.consume());

            tempPane.getChildren().add(modal);

            App.showModal(tempPane);
            modal.requestFocus();
        });

        Button refreshButton = new Button("Refresh", new FontIcon(MaterialDesignR.REFRESH));
        refreshButton.setOnAction(e -> refresh());

        ToolBar toolBar = new ToolBar(
                searchBox,
                new Separator(Orientation.VERTICAL),
                cardsToggle, listToggle,
                new Separator(Orientation.VERTICAL),
                addButton, refreshButton
        );
        return toolBar;
    }

    private void setViewMode(ViewMode mode) {
        this.viewMode = mode;
        AppSettings.get().setConnectionsViewMode(mode.name());
        applyViewMode();
    }

    private void applyViewMode() {
        boolean cards = viewMode == ViewMode.CARDS;
        cardsToggle.setSelected(cards);
        listToggle.setSelected(!cards);

        pagination.setVisible(cards);
        pagination.setManaged(cards);
        listScrollPane.setVisible(!cards);
        listScrollPane.setManaged(!cards);

        if (cards) {
            updatePagination();
        } else {
            renderList();
        }
    }

    private void updatePagination(int targetPage) {
        if (this.getScene() == null || this.getScene().getWindow() == null) {
            return;
        }

        availableWidth = this.getScene().getWindow().getWidth() - 40;
        availableHeight = this.getScene().getWindow().getHeight() - 220;

        cardsPerRow = Math.max((int) (availableWidth / 500), 1);
        cardsPerColumn = Math.max((int) (availableHeight / 170), 1);

        cardsPerPage = cardsPerRow * cardsPerColumn;

        int totalPages = Math.max((int) Math.ceil((double) visible.size() / cardsPerPage), 1);
        int clampedPage = Math.min(Math.max(targetPage, 0), totalPages - 1);

        pagination.setPageCount(totalPages);
        pagination.setCurrentPageIndex(clampedPage);
    }

    private BorderPane buildPage(int pageIndex) {
        Node content = visible.isEmpty() ? buildEmptyState() : buildGrid(pageIndex);
        currentPage = new BorderPane(content);
        BorderPane.setAlignment(currentPage.getCenter(), Pos.CENTER);
        return currentPage;
    }

    private void updatePagination() {
        if (viewMode == ViewMode.CARDS) {
            updatePagination(pagination.getCurrentPageIndex());
        }
    }

    private void createConnectionCards() {
        List<ConnectionObject> connections = ConnectionService.getInstance().loadConnections();
        for (ConnectionObject connectionObject : connections) {
            allCards.add(new ConnectionCard(connectionObject));
        }
    }

    private void recomputeVisible() {
        if (lastSearch.isBlank()) {
            visible = allCards;
            return;
        }

        String searchLower = lastSearch.toLowerCase().trim();
        List<ConnectionCard> filtered = new ArrayList<>();
        for (ConnectionCard card : allCards) {
            if (card.getConnection() != null && card.getConnection().getConnectionName().toLowerCase().contains(searchLower)) {
                filtered.add(card);
            }
        }
        visible = filtered;
    }

    public void filterCards(String searchText) {
        lastSearch = searchText == null ? "" : searchText;
        recomputeVisible();
        if (viewMode == ViewMode.CARDS) {
            showPage(0);
        } else {
            renderList();
        }
    }

    /**
     * Full reload of all cards from disk. Use only for the explicit "Refresh"
     * action; routine add/edit/delete should use the incremental methods below
     * so the in-memory order is preserved and the user is not catapulted away
     * from the page they are on.
     */
    public void refresh(int targetPage) {
        allCards.clear();
        createConnectionCards();
        recomputeVisible();
        if (viewMode == ViewMode.CARDS) {
            showPage(targetPage);
        } else {
            renderList();
        }
    }

    /**
     * Re-renders the grid for {@code targetPage} (clamped to a valid range)
     * without touching the underlying card list. This is the single place that
     * updates the pagination page count and the visible grid.
     */
    private void showPage(int targetPage) {
        if (cardsPerPage == 0) cardsPerPage = 1;

        int totalPages = Math.max((int) Math.ceil((double) visible.size() / cardsPerPage), 1);
        final int finalPage = Math.min(Math.max(targetPage, 0), totalPages - 1);

        pagination.setPageCount(totalPages);
        if (pagination.getCurrentPageIndex() != finalPage) {
            pagination.setCurrentPageIndex(finalPage);
        } else if (currentPage != null) {
            currentPage.setCenter(visible.isEmpty() ? buildEmptyState() : buildGrid(finalPage));
        }
    }

    /** Index of the card backing the given connection id, or -1. */
    private int indexOfConnection(String connectionId) {
        if (connectionId == null) return -1;
        for (int i = 0; i < allCards.size(); i++) {
            ConnectionCard card = allCards.get(i);
            if (card.getConnection() != null && connectionId.equals(card.getConnection().getId())) {
                return i;
            }
        }
        return -1;
    }

    /** Appends a newly created/cloned connection and navigates to the page that now contains it. */
    public void addConnection(String connectionId) {
        ConnectionObject connection = ConnectionService.getInstance().loadConnection(connectionId);
        if (connection == null) {
            refresh();
            return;
        }
        ConnectionCard newCard = new ConnectionCard(connection);
        allCards.add(newCard);
        recomputeVisible();

        if (viewMode == ViewMode.CARDS) {
            if (cardsPerPage == 0) cardsPerPage = 1;
            int indexInVisible = visible.indexOf(newCard);
            int pageOfNewCard = indexInVisible < 0 ? getCurrentPageIndex() : indexInVisible / cardsPerPage;
            showPage(pageOfNewCard);
        } else {
            renderList();
        }
    }

    /** Replaces an edited connection's card in place, staying on the current page. */
    public void updateConnection(String connectionId) {
        int index = indexOfConnection(connectionId);
        ConnectionObject connection = ConnectionService.getInstance().loadConnection(connectionId);
        if (index < 0 || connection == null) {
            refresh();
            return;
        }
        allCards.set(index, new ConnectionCard(connection));
        recomputeVisible();

        if (viewMode == ViewMode.CARDS) {
            showPage(getCurrentPageIndex());
        } else {
            renderList();
        }
    }

    /** Removes a deleted connection's card, staying on the current page (clamped if it emptied). */
    public void removeConnection(String connectionId) {
        int index = indexOfConnection(connectionId);
        if (index < 0) {
            refresh();
            return;
        }
        allCards.remove(index);
        recomputeVisible();

        if (viewMode == ViewMode.CARDS) {
            showPage(getCurrentPageIndex());
        } else {
            renderList();
        }
    }

    private GridPane buildGrid(int pageIndex) {
        int startIndex = pageIndex * cardsPerPage;
        int endIndex = Math.min(startIndex + cardsPerPage, visible.size());

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setPadding(new Insets(10));
        grid.setHgap(20);
        grid.setVgap(20);

        double cardWidth = Math.min(500, cardsPerRow > 1 ? (availableWidth - (cardsPerRow - 1) * 20) / cardsPerRow : availableWidth);
        double cardHeight = Math.min(170, cardsPerColumn > 1 ? (availableHeight - (cardsPerColumn - 1) * 20) / cardsPerColumn : availableHeight);

        for (int i = startIndex, row = 0, col = 0; i < endIndex; i++) {
            ConnectionCard card = visible.get(i);
            card.setMaxSize(cardWidth, cardHeight);
            card.setPrefSize(cardWidth, cardHeight);
            card.setMinSize(cardWidth, cardHeight);
            grid.add(card, col, row);
            col++;
            if (col >= cardsPerRow) {
                col = 0;
                row++;
            }
        }
        return grid;
    }

    private void renderList() {
        listBox.getChildren().clear();
        if (visible.isEmpty()) {
            listBox.getChildren().add(buildEmptyState());
            return;
        }
        for (ConnectionCard card : visible) {
            listBox.getChildren().add(new ConnectionListRow(card.getConnection()));
        }
    }

    private VBox buildEmptyState() {
        boolean searching = !lastSearch.isBlank();

        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60, 20, 60, 20));

        FontIcon icon = new FontIcon(searching ? Feather.SEARCH : MaterialDesignD.DATABASE);
        icon.setIconSize(38);
        icon.getStyleClass().add(Styles.TEXT_MUTED);

        Label title = new Label(searching ? "No connections match your search" : "No connections yet");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Label subtitle = new Label(searching ? "Try a different name." : "Click \"New\" above to add your first database connection.");
        subtitle.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        box.getChildren().addAll(icon, title, subtitle);
        return box;
    }

    public void refresh() {
        refresh(getCurrentPageIndex());
    }

    public int getCurrentPageIndex() {
        return pagination.getCurrentPageIndex();
    }
}
