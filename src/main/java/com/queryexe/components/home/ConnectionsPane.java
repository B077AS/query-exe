package com.queryexe.components.home;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Pagination;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import com.queryexe.model.connections.ConnectionObject;

import java.util.ArrayList;
import java.util.List;

import atlantafx.base.controls.Card;
import com.queryexe.service.ConnectionService;

public class ConnectionsPane extends VBox {

    private Pagination pagination;
    private List<Card> allCards;
    private int cardsPerPage;
    private BorderPane currentPage;

    private double availableWidth;
    private double availableHeight;
    private int cardsPerRow;
    private int cardsPerColumn;

    public ConnectionsPane() {
        this.setAlignment(Pos.CENTER);
        this.allCards = new ArrayList<>();

        this.allCards.add(new ConnectionCard());

        pagination = new Pagination();
        currentPage = new BorderPane();

        VBox.setVgrow(this, Priority.ALWAYS);
        this.setFillWidth(true);

        pagination.setMaxPageIndicatorCount(5);
        pagination.setStyle("-fx-page-information-visible: false;");
        pagination.getStyleClass().add(Pagination.STYLE_CLASS_BULLET);

        createConnectionCards();

        this.parentProperty().addListener((obs, oldParent, newParent) -> {
            if (newParent != null && newParent instanceof StackPane) {
                ((StackPane) newParent).widthProperty().addListener((obsWidth, oldWidth, newWidth) -> updatePagination());
                ((StackPane) newParent).heightProperty().addListener((obsHeight, oldHeight, newHeight) -> updatePagination());
            }
        });

        VBox.setVgrow(pagination, Priority.ALWAYS);
        pagination.setMaxWidth(Double.MAX_VALUE);
        pagination.setMaxHeight(Double.MAX_VALUE);

        pagination.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() < 0) {
                if (pagination.getCurrentPageIndex() < pagination.getPageCount() - 1) {
                    pagination.setCurrentPageIndex(pagination.getCurrentPageIndex() + 1);
                }
            } else if (event.getDeltaY() > 0) {
                if (pagination.getCurrentPageIndex() > 0) {
                    pagination.setCurrentPageIndex(pagination.getCurrentPageIndex() - 1);
                }
            }
            event.consume();
        });

        pagination.setPageFactory(this::buildPage);

        this.getChildren().add(pagination);

        Platform.runLater(() -> updatePagination(0));
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

        int totalPages = (int) Math.ceil((double) allCards.size() / cardsPerPage);
        int clampedPage = Math.min(Math.max(targetPage, 0), totalPages - 1);

        pagination.setPageCount(totalPages);
        pagination.setCurrentPageIndex(clampedPage);
    }

    private BorderPane buildPage(int pageIndex) {
        currentPage = new BorderPane(buildGrid(pageIndex));
        BorderPane.setAlignment(currentPage.getCenter(), Pos.CENTER);
        return currentPage;
    }

    private void updatePagination() {
        updatePagination(pagination.getCurrentPageIndex());
    }

    private void createConnectionCards() {
        List<ConnectionObject> connections = ConnectionService.getInstance().loadConnections();
        for (ConnectionObject connectionObject : connections) {
            allCards.add(new ConnectionCard(connectionObject));
        }
    }

    public void filterCards(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            updatePagination();
            return;
        }

        String searchLower = searchText.toLowerCase().trim();
        List<Card> filteredCards = new ArrayList<>();
        filteredCards.add(allCards.get(0));

        for (int i = 1; i < allCards.size(); i++) {
            Card card = allCards.get(i);
            if (card instanceof ConnectionCard connCard && connCard.getConnection() != null) {
                if (connCard.getConnection().getConnectionName().toLowerCase().contains(searchLower)) {
                    filteredCards.add(card);
                }
            }
        }

        List<Card> originalCards = allCards;
        allCards = filteredCards;
        updatePagination(0);
        allCards = originalCards;
    }

    /**
     * Full reload of all cards from disk. Use only for the explicit "Refresh"
     * action; routine add/edit/delete should use the incremental methods below
     * so the in-memory order is preserved and the user is not catapulted away
     * from the page they are on.
     */
    public void refresh(int targetPage) {
        allCards.clear();
        allCards.add(new ConnectionCard());
        createConnectionCards();
        showPage(targetPage);
    }

    /**
     * Re-renders the grid for {@code targetPage} (clamped to a valid range)
     * without touching the underlying card list. This is the single place that
     * updates the pagination page count and the visible grid.
     */
    private void showPage(int targetPage) {
        if (cardsPerPage == 0) cardsPerPage = 1;

        int totalPages = Math.max((int) Math.ceil((double) allCards.size() / cardsPerPage), 1);
        final int finalPage = Math.min(Math.max(targetPage, 0), totalPages - 1);

        pagination.setPageCount(totalPages);
        if (pagination.getCurrentPageIndex() != finalPage) {
            pagination.setCurrentPageIndex(finalPage);
        }

        if (currentPage != null) {
            currentPage.setCenter(buildGrid(finalPage));
        }
    }

    /** Index of the card backing the given connection id, or -1. Index 0 is the manager card. */
    private int indexOfConnection(String connectionId) {
        if (connectionId == null) return -1;
        for (int i = 1; i < allCards.size(); i++) {
            Card card = allCards.get(i);
            if (card instanceof ConnectionCard connCard && connCard.getConnection() != null
                    && connectionId.equals(connCard.getConnection().getId())) {
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
        allCards.add(new ConnectionCard(connection));

        if (cardsPerPage == 0) cardsPerPage = 1;
        int pageOfNewCard = (allCards.size() - 1) / cardsPerPage;
        showPage(pageOfNewCard);
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
        showPage(getCurrentPageIndex());
    }

    /** Removes a deleted connection's card, staying on the current page (clamped if it emptied). */
    public void removeConnection(String connectionId) {
        int index = indexOfConnection(connectionId);
        if (index < 0) {
            refresh();
            return;
        }
        allCards.remove(index);
        showPage(getCurrentPageIndex());
    }

    private GridPane buildGrid(int pageIndex) {
        int startIndex = pageIndex * cardsPerPage;
        int endIndex = Math.min(startIndex + cardsPerPage, allCards.size());

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setPadding(new Insets(10));
        grid.setHgap(20);
        grid.setVgap(20);

        double cardWidth = Math.min(500, cardsPerRow > 1 ? (availableWidth - (cardsPerRow - 1) * 20) / cardsPerRow : availableWidth);
        double cardHeight = Math.min(170, cardsPerColumn > 1 ? (availableHeight - (cardsPerColumn - 1) * 20) / cardsPerColumn : availableHeight);

        for (int i = startIndex, row = 0, col = 0; i < endIndex; i++) {
            Card card = allCards.get(i);
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

    public void refresh() {
        refresh(getCurrentPageIndex());
    }

    public int getCurrentPageIndex() {
        return pagination.getCurrentPageIndex();
    }
}