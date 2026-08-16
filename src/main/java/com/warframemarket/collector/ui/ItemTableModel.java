package com.warframemarket.collector.ui;

import com.warframemarket.collector.model.MarketItem;
import com.warframemarket.collector.model.PriceSnapshot;
import com.warframemarket.collector.service.MarketDatabase;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Table model backed directly by {@link MarketDatabase}: rows are items, and price cells
 * are looked up live, so refreshing one item only needs a row-repaint event.
 */
public final class ItemTableModel extends AbstractTableModel {

    public static final int COL_NAME = 0;
    public static final int COL_LOWEST = 1;
    public static final int COL_HIGHEST = 2;
    public static final int COL_SPREAD = 3;
    public static final int COL_ORDERS = 4;
    public static final int COL_UPDATED = 5;
    public static final int COL_STATUS = 6;

    private static final String[] COLUMN_NAMES = {
            "Item", "Lowest, pl", "Highest, pl", "Spread", "Sell orders", "Updated", "Status"
    };

    private final MarketDatabase database;
    private List<MarketItem> rows = List.of();
    private Map<String, Integer> rowBySlug = Map.of();

    public ItemTableModel(MarketDatabase database) {
        this.database = database;
    }

    public void setRows(List<MarketItem> items) {
        this.rows = List.copyOf(items);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            index.put(rows.get(i).slug(), i);
        }
        this.rowBySlug = Map.copyOf(index);
        fireTableDataChanged();
    }

    /** Repaints the single row showing {@code slug}, if this tab contains it. */
    public void itemUpdated(String slug) {
        Integer row = rowBySlug.get(slug);
        if (row != null) {
            fireTableRowsUpdated(row, row);
        }
    }

    public MarketItem itemAt(int modelRow) {
        return rows.get(modelRow);
    }

    public List<MarketItem> items() {
        return rows;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (column) {
            case COL_LOWEST, COL_HIGHEST, COL_SPREAD, COL_ORDERS -> Integer.class;
            case COL_UPDATED -> Instant.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        MarketItem item = rows.get(rowIndex);
        PriceSnapshot price = database.priceOf(item.slug());

        if (columnIndex == COL_NAME) {
            return item.name();
        }
        if (price == null) {
            return columnIndex == COL_STATUS ? "not collected yet" : null;
        }
        return switch (columnIndex) {
            case COL_LOWEST -> price.lowestPlatinum();
            case COL_HIGHEST -> price.highestPlatinum();
            case COL_SPREAD -> price.spread();
            case COL_ORDERS -> price.sellOrderCount();
            case COL_UPDATED -> price.fetchedAt();
            case COL_STATUS -> statusOf(price);
            default -> null;
        };
    }

    private static String statusOf(PriceSnapshot price) {
        if (price.isFailed()) {
            return "error: " + price.error();
        }
        return price.sellOrderCount() == 0 ? "no sell orders" : "ok";
    }
}
