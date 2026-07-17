package com.queryexe.model.data;

import javafx.collections.ObservableList;
import lombok.Data;

@Data
public class TableRowData {

    private final ObservableList<Object> originalData;
    private final ObservableList<String> stringData;
    private boolean isNewRow = false;
    private QueryData pendingInsert;

    public TableRowData(ObservableList<Object> originalData, ObservableList<String> stringData) {
        this.originalData = originalData;
        this.stringData = stringData;
    }
}