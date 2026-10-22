package com.queryexe.components.modals;

import atlantafx.base.theme.Styles;
import com.queryexe.components.extra.CustomNotification;
import com.queryexe.components.modals.shell.ModalUi;
import com.queryexe.components.modals.shell.SidebarModal;
import com.queryexe.components.modals.shell.SimpleTab;
import com.queryexe.model.data.DetailedColumnData;
import com.queryexe.queryexe.App;
import com.queryexe.service.DatabaseConnection;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Read-only overview of one column: its type and constraints, and the indexes and foreign keys it takes part in. */
@Slf4j
public class ColumnPropertiesModal extends SidebarModal {

    public ColumnPropertiesModal(String databaseName, String tableName, String columnName) {
        super(MaterialDesignT.TABLE_COLUMN, columnName, databaseName + "." + tableName, "Column", 1100, 660);

        try {
            DetailedColumnData info = DatabaseConnection.getInstance().getCurrentConnectionObject()
                    .getDetailedColumnInfo(databaseName, tableName, columnName);

            int relations = count(info.getIndexes()) + count(info.getForeignKeyReferences()) + count(info.getReferencedByForeignKeys());
            addTab(new SimpleTab("Details", "Type and constraints", MaterialDesignI.INFORMATION_OUTLINE, detailsPane(info)));
            addTab(new SimpleTab("Relationships", relations + (relations == 1 ? " index or key" : " indexes and keys"),
                    MaterialDesignL.LINK_VARIANT, relationshipsPane(info)));
        } catch (SQLException e) {
            log.error("Could not load column {}.{}.{}", databaseName, tableName, columnName, e);
            Platform.runLater(App::closeModal);
            new CustomNotification("Load Failed", "Could not load column data: " + e.getMessage(),
                    new FontIcon(MaterialDesignT.TABLE_CANCEL)).showNotification();
        }

        setFooter(closeFooterButton("Close"));
    }

    private static int count(List<?> list) {
        return list == null ? 0 : list.size();
    }

    // ── Details ───────────────────────────────────────────────────────────────

    /**
     * One quiet page: the type and its flags as the heading line, then a single key/value grid with even spacing and
     * no dividers or group titles. Rows without a value are left out instead of shown as dashes.
     */
    private VBox detailsPane(DetailedColumnData info) {
        Label type = ModalUi.mono(info.getDataType() == null ? "—" : info.getDataType());
        type.getStyleClass().add("modal-type");
        type.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        HBox heading = new HBox(12, type);
        heading.setAlignment(Pos.CENTER_LEFT);
        if (info.isPrimaryKey()) heading.getChildren().add(ModalUi.accentBadge("PK"));
        if (!info.isNullable()) heading.getChildren().add(ModalUi.badge("NOT NULL"));
        if (info.isUnique()) heading.getChildren().add(ModalUi.badge("UNIQUE"));
        if (info.isAutoIncrement()) heading.getChildren().add(ModalUi.badge("AUTO INC"));

        GridPane grid = new GridPane();
        grid.setHgap(32);
        grid.setVgap(14);
        int row = 0;
        row = add(grid, row, "Table", info.getTableName(), false);
        row = add(grid, row, ModalUi.containerLabel(), info.getSchemaName(), false);
        row = add(grid, row, "Position", String.valueOf(info.getOrdinalPosition()), false);
        if (info.getColumnType() != null && !info.getColumnType().equalsIgnoreCase(info.getDataType())) {
            row = add(grid, row, "Full type", info.getColumnType(), true);
        }
        row = add(grid, row, "Max length", info.getCharacterMaximumLength(), false);
        row = add(grid, row, "Precision", info.getNumericPrecision(), false);
        row = add(grid, row, "Scale", info.getNumericScale(), false);
        row = add(grid, row, "Character set", info.getCharacterSetName(), false);
        row = add(grid, row, "Collation", info.getCollationName(), false);
        row = add(grid, row, "Default", info.getColumnDefault(), true);
        add(grid, row, "Comment", info.getColumnComment(), false);

        VBox content = new VBox(28, heading, grid);
        content.setPadding(new Insets(28, 28, 24, 28));
        return new VBox(ModalUi.scroll(content));
    }

    /** Adds a key/value row unless the value is empty; returns the next free row index. */
    private static int add(GridPane grid, int row, String key, Object value, boolean mono) {
        if (value == null || value.toString().isEmpty()) return row;
        Label name = new Label(key);
        name.getStyleClass().add(Styles.TEXT_MUTED);
        name.setMinWidth(130);
        Label text = mono ? ModalUi.mono(value.toString()) : new Label(value.toString());
        text.setWrapText(true);
        grid.add(name, 0, row);
        grid.add(text, 1, row);
        GridPane.setValignment(name, VPos.TOP);
        GridPane.setHgrow(text, Priority.ALWAYS);
        return row + 1;
    }


    /** A titled group of rows; the small caps title sits above its rows. */
    private static VBox section(String title) {
        Label label = ModalUi.sectionLabel(title);
        label.setPadding(new Insets(10, 24, 6, 24));
        VBox box = new VBox(label);
        return box;
    }

    // ── Relationships ─────────────────────────────────────────────────────────

    private VBox relationshipsPane(DetailedColumnData info) {
        VBox list = new VBox(16);

        List<Map<String, String>> indexes = info.getIndexes();
        if (indexes != null && !indexes.isEmpty()) {
            VBox section = section("INDEXES");
            for (Map<String, String> index : indexes) {
                HBox badges = new HBox(6);
                badges.setAlignment(Pos.CENTER_RIGHT);
                String type = index.get("INDEX_TYPE");
                if (type != null) badges.getChildren().add(ModalUi.badge(type.toUpperCase()));
                badges.getChildren().add(ModalUi.badge("0".equals(index.get("NON_UNIQUE")) ? "UNIQUE" : "NON-UNIQUE"));
                section.getChildren().add(ModalUi.block(index.get("INDEX_NAME"), badges, ModalUi.mono(info.getTableName() + "(" + info.getColumnName() + ")")));
            }
            list.getChildren().add(section);
        }

        List<Map<String, String>> references = info.getForeignKeyReferences();
        if (references != null && !references.isEmpty()) {
            VBox section = section("REFERENCES");
            for (Map<String, String> fk : references) {
                section.getChildren().add(ModalUi.block(fk.get("CONSTRAINT_NAME"), new HBox(),
                        relation(info.getTableName() + "." + info.getColumnName(),
                                fk.get("REFERENCED_TABLE_NAME") + "." + fk.get("REFERENCED_COLUMN_NAME"))));
            }
            list.getChildren().add(section);
        }

        List<Map<String, String>> referencedBy = info.getReferencedByForeignKeys();
        if (referencedBy != null && !referencedBy.isEmpty()) {
            VBox section = section("REFERENCED BY");
            for (Map<String, String> fk : referencedBy) {
                section.getChildren().add(ModalUi.block(fk.get("CONSTRAINT_NAME"), new HBox(),
                        relation(fk.get("TABLE_NAME") + "." + fk.get("COLUMN_NAME"),
                                info.getTableName() + "." + info.getColumnName())));
            }
            list.getChildren().add(section);
        }

        if (list.getChildren().isEmpty()) {
            VBox content = ModalUi.content();
            content.getChildren().add(ModalUi.emptyState(MaterialDesignL.LINK_VARIANT_OFF, "This column has no indexes or foreign keys"));
            return content;
        }
        return ModalUi.listTab(list);
    }

    /** {@code from → to} as wrapping monospace pieces, the target in the accent color. */
    private static Node relation(String from, String to) {
        Label source = ModalUi.mono(from);
        Label arrow = new Label("→");
        arrow.getStyleClass().add(Styles.TEXT_MUTED);
        Label target = ModalUi.mono(to);
        target.getStyleClass().add("modal-type");
        FlowPane relation = new FlowPane(8, 2, source, arrow, target);
        relation.setAlignment(Pos.CENTER_LEFT);
        return relation;
    }
}
