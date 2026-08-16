package com.warframemarket.collector.ui;

import com.warframemarket.collector.model.Category;
import com.warframemarket.collector.model.MarketItem;
import com.warframemarket.collector.model.PriceSnapshot;
import com.warframemarket.collector.service.MarketDatabase;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** One tab: a searchable, sortable table of the items in a single category. */
public final class CategoryPanel extends JPanel {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Category category;
    private final MarketDatabase database;
    private final ItemTableModel model;
    private final JTable table;
    private final TableRowSorter<ItemTableModel> sorter;
    private final JTextField searchField = new JTextField(20);
    private final JLabel summaryLabel = new JLabel();

    public CategoryPanel(Category category, MarketDatabase database) {
        super(new BorderLayout(0, 6));
        this.category = category;
        this.database = database;
        this.model = new ItemTableModel(database);
        this.table = new JTable(model);
        this.sorter = new TableRowSorter<>(model);

        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        configureTable();

        add(buildSearchBar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(summaryLabel, BorderLayout.SOUTH);

        reload();
    }

    private void configureTable() {
        table.setRowSorter(sorter);
        table.setAutoCreateRowSorter(false);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        // Nulls are ordinary here (an item nobody sells has no price), so every
        // sortable column needs a null-tolerant comparator.
        Comparator<Integer> intComparator = Comparator.nullsFirst(Comparator.naturalOrder());
        for (int column : new int[] {ItemTableModel.COL_LOWEST, ItemTableModel.COL_HIGHEST,
                ItemTableModel.COL_SPREAD, ItemTableModel.COL_ORDERS}) {
            sorter.setComparator(column, intComparator);
        }
        sorter.setComparator(ItemTableModel.COL_UPDATED,
                Comparator.nullsFirst(Comparator.<Instant>naturalOrder()));
        sorter.setComparator(ItemTableModel.COL_NAME, String.CASE_INSENSITIVE_ORDER);

        table.setDefaultRenderer(Integer.class, new NumberRenderer());
        table.setDefaultRenderer(Instant.class, new TimestampRenderer());

        applyColumnWidths();
    }

    /**
     * Sizes the columns. {@code width} is set alongside {@code preferredWidth} because the
     * auto-resize pass only redistributes the difference between the total column width and
     * the viewport - it does not adopt preferred widths on its own.
     */
    private void applyColumnWidths() {
        int[] widths = {300, 95, 95, 85, 95, 165, 200};
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            var column = table.getColumnModel().getColumn(i);
            column.setPreferredWidth(widths[i]);
            column.setWidth(widths[i]);
            column.setMinWidth(60);
        }
    }

    private JComponent buildSearchBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.LINE_AXIS));
        JLabel label = new JLabel("Search:");
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        bar.add(label);
        searchField.setMaximumSize(new Dimension(320, searchField.getPreferredSize().height));
        searchField.putClientProperty("JTextField.placeholderText", "filter by name");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        bar.add(searchField);
        bar.add(Box.createHorizontalGlue());
        return bar;
    }

    private void applyFilter() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter(
                    "(?i)" + Pattern.quote(text), ItemTableModel.COL_NAME));
        }
        updateSummary();
    }

    /** Rebuilds this tab's rows from the catalogue (after the item list changed). */
    public void reload() {
        model.setRows(database.itemsIn(category));
        updateSummary();
    }

    public void itemUpdated(String slug) {
        model.itemUpdated(slug);
        updateSummary();
    }

    private void updateSummary() {
        int total = model.getRowCount();
        int priced = 0;
        int failed = 0;
        for (MarketItem item : model.items()) {
            PriceSnapshot price = database.priceOf(item.slug());
            if (price == null) {
                continue;
            }
            if (price.isFailed()) {
                failed++;
            } else if (price.lowestPlatinum() != null) {
                priced++;
            }
        }
        String shown = sorter.getRowFilter() == null ? "" : "  ·  showing " + table.getRowCount();
        summaryLabel.setText("%d items  ·  %d priced  ·  %d failed%s"
                .formatted(total, priced, failed, shown));
    }

    public Category category() {
        return category;
    }

    public JTable table() {
        return table;
    }

    /** Slugs of the currently selected rows, in view order. */
    public List<String> selectedSlugs() {
        List<String> slugs = new ArrayList<>();
        for (int viewRow : table.getSelectedRows()) {
            slugs.add(model.itemAt(table.convertRowIndexToModel(viewRow)).slug());
        }
        return slugs;
    }

    public MarketItem itemAtViewRow(int viewRow) {
        return model.itemAt(table.convertRowIndexToModel(viewRow));
    }

    /** Right-aligned numbers, with an em dash where there is no value. */
    private static final class NumberRenderer extends DefaultTableCellRenderer {
        private NumberRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (value == null) {
                setText("—");
            }
            return c;
        }
    }

    private static final class TimestampRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Object text = value instanceof Instant instant ? TIMESTAMP_FORMAT.format(instant) : "—";
            return super.getTableCellRendererComponent(table, text, selected, focused, row, column);
        }
    }
}
