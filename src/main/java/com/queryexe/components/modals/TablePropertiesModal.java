package com.queryexe.components.modals;

import com.queryexe.components.modals.shell.ModalUi;
import com.queryexe.components.modals.shell.SidebarModal;
import com.queryexe.components.modals.shell.SimpleTab;
import com.queryexe.model.data.ColumnData;
import com.queryexe.model.data.ForeignKeyData;
import com.queryexe.service.DatabaseConnection;
import javafx.geometry.Pos;
import atlantafx.base.theme.Styles;
import com.queryexe.utils.IconColorUtil;
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
        for (ColumnData column : columns) {
            List<String> details = new ArrayList<>();
            if (column.isPrimaryKey()) details.add("Primary key");
            if (column.isAutoIncrement()) details.add("Auto increment");
            details.add(column.isNotNull() ? "Not null" : "Nullable");
            if (column.isUnique()) details.add("Unique");
            list.getChildren().add(ModalUi.entry(column.getColumnName(), column.getDataType(), column.isPrimaryKey(), details));
        }
        return ModalUi.listTab(list);
    }

    // ── Foreign keys ──────────────────────────────────────────────────────────

    private VBox foreignKeysPane(List<ForeignKeyData> foreignKeys) {
        if (foreignKeys.isEmpty()) return emptyPane(MaterialDesignT.TABLE_KEY, "No foreign keys defined");

        VBox list = new VBox();
        for (ForeignKeyData fk : foreignKeys) {
            String target = fk.getReferenceSchema() != null && !fk.getReferenceSchema().equals(databaseName)
                    ? fk.getReferenceSchema() + "." + fk.getReferenceTable()
                    : fk.getReferenceTable();
            List<String> details = new ArrayList<>();
            details.add(String.join(", ", fk.allLocalColumns()));
            if (fk.getOnDelete() != null) details.add("On delete " + fk.getOnDelete().toLowerCase());
            if (fk.getOnUpdate() != null) details.add("On update " + fk.getOnUpdate().toLowerCase());
            list.getChildren().add(ModalUi.entry(fk.getConstraintName(),
                    target + "(" + String.join(", ", fk.allReferenceColumns()) + ")", false, details));
        }
        return ModalUi.listTab(list);
    }

    private static VBox emptyPane(Ikon icon, String text) {
        VBox content = ModalUi.content();
        content.getChildren().add(ModalUi.emptyState(icon, text));
        return content;
    }

    // ── Details ───────────────────────────────────────────────────────────────

    /** The three numbers that matter as even stat tiles (big value, small label), then where the table lives. */
    private VBox detailsPane(int columnCount, int foreignKeyCount) {
        String size;
        try {
            size = String.format("%.2f MB", DatabaseConnection.getInstance().getConnectionObject().getTableSize(databaseName, tableName));
        } catch (SQLException e) {
            log.error("getTableSize failed for {}.{}", databaseName, tableName, e);
            size = "N/A";
        }

        HBox stats = new HBox(12,
                stat(MaterialDesignT.TABLE_COLUMN, String.valueOf(columnCount), columnCount == 1 ? "Column" : "Columns"),
                stat(MaterialDesignT.TABLE_KEY, String.valueOf(foreignKeyCount), foreignKeyCount == 1 ? "Foreign key" : "Foreign keys"),
                stat(MaterialDesignH.HARDDISK, size, "Size on disk"));

        Label location = ModalUi.mono(databaseName + "." + tableName);
        location.setStyle("-fx-font-size: 13px;");
        location.setWrapText(true);
        VBox where = new VBox(8, ModalUi.sectionLabel(ModalUi.containerLabel().toUpperCase() + " AND TABLE"), location);

        VBox content = new VBox(32, stats, where);
        content.setPadding(new javafx.geometry.Insets(24));
        return wrap(content);
    }

    private static Node stat(Ikon icon, String value, String label) {
        Label number = new Label(value);
        number.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
        Label caption = new Label(label);
        caption.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        VBox tile = new VBox(10, IconColorUtil.colored(icon, "-color-fg-muted", 18), new VBox(2, number, caption));
        tile.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 10px; -fx-padding: 16 18 16 18;");
        tile.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.setPrefWidth(0);
        return tile;
    }

    private static VBox wrap(VBox content) {
        return new VBox(ModalUi.scroll(content));
    }
}
