package com.queryexe.components.home;

import com.google.gson.*;
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

    public ConnectionsPane() {
        this.setAlignment(Pos.CENTER);
        this.allCards = new ArrayList<Card>();

        ConnectionCard addConnectionCard = new ConnectionCard();

        this.allCards.add(addConnectionCard);

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

        this.getChildren().add(pagination);

        Platform.runLater(this::updatePagination);
    }

    private void updatePagination() {
        if (this.getScene() == null || this.getScene().getWindow() == null) {
            return;
        }

        double availableWidth = this.getScene().getWindow().getWidth() - 40;
        double availableHeight = this.getScene().getWindow().getHeight() - 220;

        int cardsPerRow = Math.max((int) (availableWidth / 500), 1);
        int cardsPerColumn = Math.max((int) (availableHeight / 170), 1);

        cardsPerPage = cardsPerRow * cardsPerColumn;

        int totalPages = (int) Math.ceil((double) allCards.size() / cardsPerPage);
        pagination.setPageCount(totalPages);

        if (pagination.getCurrentPageIndex() >= totalPages) {
            pagination.setCurrentPageIndex(totalPages - 1);
        }

        pagination.setPageFactory(pageIndex -> {
            int startIndex = pageIndex * cardsPerPage;
            int endIndex = Math.min(startIndex + cardsPerPage, allCards.size());

            GridPane grid = new GridPane();
            grid.setAlignment(Pos.CENTER);
            grid.setPadding(new Insets(10));
            grid.setHgap(20);
            grid.setVgap(20);

            double cardWidth = Math.min(500, (availableWidth - (cardsPerRow - 1) * 20) / cardsPerRow);
            double cardHeight = Math.min(170, (availableHeight - (cardsPerColumn - 1) * 20) / cardsPerColumn);

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

            currentPage = new BorderPane(grid);
            BorderPane.setAlignment(grid, Pos.CENTER);
            return currentPage;
        });
    }

    private void createConnectionCards() {
        List<ConnectionObject> connections = ConnectionService.getInstance().loadConnections();
        for (ConnectionObject connectionObject : connections) {
            ConnectionCard connectionCard = new ConnectionCard(connectionObject);
            allCards.add(connectionCard);
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
            if (card instanceof ConnectionCard) {
                ConnectionCard connCard = (ConnectionCard) card;
                if (connCard.getConnection() != null) {
                    String connectionName = connCard.getConnection().getConnectionName().toLowerCase();
                    if (connectionName.contains(searchLower)) {
                        filteredCards.add(card);
                    }
                }
            }
        }
        List<Card> originalCards = allCards;
        allCards = filteredCards;
        updatePagination();
        allCards = originalCards;
    }
}