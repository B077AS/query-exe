package com.queryexe.components.modals;

import com.queryexe.components.modals.shell.ModalUi;
import com.queryexe.components.modals.shell.SidebarModal;
import com.queryexe.components.modals.shell.SimpleTab;
import com.queryexe.model.data.ColumnData;
import com.queryexe.model.data.ForeignKeyData;
import com.queryexe.service.DatabaseConnection;
import javafx.geometry.Pos;
import atlantafx.base.theme.Styles;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.materialdesign2.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Read-only overview of a table: columns, foreign keys and general details. */
@Slf4j
public class TablePropertiesModal extends SidebarModal {


    private final String databaseName;
    private final String tableName;

    public TablePropertiesModal(String databaseName, String tableName) {
        super(MaterialDesignT.TABLE, tableName, databaseName, "Table", 1100, 660);
        this.databaseName = databaseName;
        this.tableName = tableName;

        try {
            ArrayList<ColumnData> columns = DatabaseConnection.getInstance().getConnectionObject().getColumnsForTable(databaseName, tableName);
            Map<String, ForeignKeyData> foreignKeys = DatabaseConnection.getInstance().getConnectionObject().extractForeignKeys(databaseName, tableName);

            addTab(new SimpleTab("Columns", columns.size() + (columns.size() == 1 ? " column" : " columns"),
                    MaterialDesignT.TABLE_COLUMN, columnsPane(columns)));
            addTab(new SimpleTab("Foreign Keys", foreignKeys.size() + (foreignKeys.size() == 1 ? " foreign key" : " foreign keys"),
                    MaterialDesignT.TABLE_KEY, foreignKeysPane(new ArrayList<>(foreignKeys.values()))));
            addTab(new SimpleTab("Details", "General information and statistics",
                    MaterialDesignI.INFORMATION_OUTLINE, detailsPane(columns.size(), foreignKeys.size())));
        } catch (SQLException e) {
            log.error("Could not load properties of {}.{}", databaseName, tableName, e);
            addTab(new SimpleTab("Columns", "", MaterialDesignT.TABLE_COLUMN,
                    ModalUi.emptyState(MaterialDesignA.ALERT_CIRCLE_OUTLINE, "Could not load table data: " + e.getMessage())));
        }

        setFooter(closeFooterButton("Close"));
    }

    // ── Columns ───────────────────────────────────────────────────────────────

    private VBox columnsPane(List<ColumnData> columns) {
        if (columns.isEmpty()) return emptyPane(MaterialDesignT.TABLE_COLUMN, "No columns found");

        VBox list = new VBox();
        ModalUi.markLast(list);
        for (ColumnData column : columns) {
            list.getChildren().add(columnItem(column));
        }
        return ModalUi.listTab(list);
    }

    private Node columnItem(ColumnData column) {
        HBox badges = new HBox(6);
        badges.setAlignment(Pos.CENTER_RIGHT);
        if (column.isPrimaryKey()) badges.getChildren().add(ModalUi.accentBadge("PK"));
        if (column.isNotNull()) badges.getChildren().add(ModalUi.badge("NOT NULL"));
        if (column.isUnique()) badges.getChildren().add(ModalUi.badge("UNIQUE"));
        if (column.isAutoIncrement()) badges.getChildren().add(ModalUi.badge("AUTO INC"));

        Label type = ModalUi.mono(column.getDataType());
        type.getStyleClass().add("modal-type");
        type.setWrapText(true);
        return block(column.getColumnName(), badges, type);
    }

    /**
     * A list entry as a small block, since names, types and relations are too long for fixed columns: the name in
     * bold with its badges on the right (a name that still doesn't fit is cut with the full text in a tooltip),
     * and the detail underneath, which wraps instead of being cut off.
     */
    private static Node block(String title, HBox badges, Node detail) {
        Label name = ModalUi.boldLabel(title);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTooltip(new Tooltip(title));
        HBox.setHgrow(name, Priority.ALWAYS);
        badges.setMinWidth(Region.USE_PREF_SIZE);

        VBox block = new VBox(5, new HBox(12, name, badges), detail);
        block.getStyleClass().add("modal-item");
        block.setMaxWidth(Double.MAX_VALUE);
        return block;
    }

    // ── Foreign keys ──────────────────────────────────────────────────────────

    private VBox foreignKeysPane(List<ForeignKeyData> foreignKeys) {
        if (foreignKeys.isEmpty()) return emptyPane(MaterialDesignT.TABLE_KEY, "No foreign keys defined");

        VBox list = new VBox();
        ModalUi.markLast(list);
        for (ForeignKeyData fk : foreignKeys) {
            list.getChildren().add(foreignKeyItem(fk));
        }
        return ModalUi.listTab(list);
    }

    /**
     * A foreign key: the constraint name with its actions, and under it the relation as wrapping pieces,
     * local columns → referenced table.columns.
     */
    private Node foreignKeyItem(ForeignKeyData fk) {

        HBox badges = new HBox(6);
        badges.setAlignment(Pos.CENTER_RIGHT);
        if (fk.getOnDelete() != null) badges.getChildren().add(ModalUi.badge("ON DELETE " + fk.getOnDelete()));
        if (fk.getOnUpdate() != null) badges.getChildren().add(ModalUi.badge("ON UPDATE " + fk.getOnUpdate()));
        badges.setMinWidth(Region.USE_PREF_SIZE);

        String target = fk.getReferenceSchema() != null && !fk.getReferenceSchema().equals(databaseName)
                ? fk.getReferenceSchema() + "." + fk.getReferenceTable()
                : fk.getReferenceTable();
        Label local = ModalUi.mono(tableName + "(" + String.join(", ", fk.allLocalColumns()) + ")");
        Label arrow = new Label("→");
        arrow.getStyleClass().add(Styles.TEXT_MUTED);
        Label reference = ModalUi.mono(target + "(" + String.join(", ", fk.allReferenceColumns()) + ")");
        reference.getStyleClass().add("modal-type");
        FlowPane relation = new FlowPane(8, 2, local, arrow, reference);
        relation.setAlignment(Pos.CENTER_LEFT);

        return block(fk.getConstraintName(), badges, relation);
    }

    private static VBox emptyPane(Ikon icon, String text) {
        VBox content = ModalUi.content();
        content.getChildren().add(ModalUi.emptyState(icon, text));
        return content;
    }

    // ── Details ───────────────────────────────────────────────────────────────

    private VBox detailsPane(int columnCount, int foreignKeyCount) {
        VBox general = ModalUi.card("General", MaterialDesignI.INFORMATION_OUTLINE);
        GridPane generalGrid = ModalUi.infoGrid();
        ModalUi.infoRow(generalGrid, 0, "Database", databaseName);
        ModalUi.infoRow(generalGrid, 1, "Table name", tableName);
        ModalUi.infoRow(generalGrid, 2, "Columns", String.valueOf(columnCount));
        ModalUi.infoRow(generalGrid, 3, "Foreign keys", String.valueOf(foreignKeyCount));
        general.getChildren().add(generalGrid);

        VBox stats = ModalUi.card("Statistics", MaterialDesignC.CHART_BAR);
        GridPane statsGrid = ModalUi.infoGrid();
        String size;
        try {
            size = String.format("%.2f MB", DatabaseConnection.getInstance().getConnectionObject().getTableSize(databaseName, tableName));
        } catch (SQLException e) {
            log.error("getTableSize failed for {}.{}", databaseName, tableName, e);
            size = "N/A";
        }
        ModalUi.infoRow(statsGrid, 0, "Table size", size);
        stats.getChildren().add(statsGrid);

        VBox content = ModalUi.content();
        content.getChildren().addAll(general, stats);
        return wrap(content);
    }

    private static VBox wrap(VBox content) {
        return new VBox(ModalUi.scroll(content));
    }
}
