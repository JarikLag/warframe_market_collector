package com.warframemarket.collector.ui;

import com.warframemarket.collector.model.Category;
import com.warframemarket.collector.model.MarketItem;
import com.warframemarket.collector.service.CollectorListener;
import com.warframemarket.collector.service.CollectorService;
import com.warframemarket.collector.service.MarketDatabase;
import com.warframemarket.collector.service.UpdateRequest;
import com.warframemarket.collector.store.AppPaths;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application window: one tab per category, plus the controls that trigger
 * refreshes of everything, of one category, or of the selected items.
 *
 * <p>Implements {@link CollectorListener}; the service is constructed with
 * {@code SwingUtilities::invokeLater} as its callback executor, so every callback here
 * already runs on the event dispatch thread.
 */
public final class MainWindow extends JFrame implements CollectorListener {

    private static final Logger LOG = Logger.getLogger(MainWindow.class.getName());

    private static final DateTimeFormatter FOOTER_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final CollectorService service;
    private final MarketDatabase database;

    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<Category, CategoryPanel> panels = new EnumMap<>(Category.class);

    private final JButton updateAllButton = new JButton("Update all");
    private final JButton updateCategoryButton = new JButton("Update category");
    private final JButton updateSelectedButton = new JButton("Update selected");
    private final JButton cancelButton = new JButton("Cancel");
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel footerLabel = new JLabel();

    public MainWindow(CollectorService service) {
        super("Warframe Market Collector");
        this.service = service;
        this.database = service.database();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(760, 420));
        setLocationRelativeTo(null);

        for (Category category : Category.values()) {
            CategoryPanel panel = new CategoryPanel(category, database);
            panels.put(category, panel);
            tabs.addTab(category.displayName(), panel);
            installContextMenu(panel);
        }

        setJMenuBar(buildMenuBar());
        add(buildToolBar(), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        updateAllButton.addActionListener(e -> startUpdate(UpdateRequest.all()));
        updateCategoryButton.addActionListener(e -> startUpdate(UpdateRequest.category(currentPanel().category())));
        updateSelectedButton.addActionListener(e -> updateSelected());
        cancelButton.addActionListener(e -> service.cancel());
        tabs.addChangeListener(e -> refreshButtonLabels());

        service.addListener(this);
        setRunning(false);
        refreshButtonLabels();
        refreshFooter();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu dataMenu = new JMenu("Data");
        JMenuItem all = new JMenuItem("Update all items");
        all.addActionListener(e -> startUpdate(UpdateRequest.all()));
        JMenuItem category = new JMenuItem("Update current category");
        category.addActionListener(e -> startUpdate(UpdateRequest.category(currentPanel().category())));
        JMenuItem selected = new JMenuItem("Update selected items");
        selected.addActionListener(e -> updateSelected());
        JMenuItem cancel = new JMenuItem("Cancel running update");
        cancel.addActionListener(e -> service.cancel());
        JMenuItem openFolder = new JMenuItem("Open data folder");
        openFolder.addActionListener(e -> openDataFolder());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> shutdown());

        dataMenu.add(all);
        dataMenu.add(category);
        dataMenu.add(selected);
        dataMenu.addSeparator();
        dataMenu.add(cancel);
        dataMenu.addSeparator();
        dataMenu.add(openFolder);
        dataMenu.add(exit);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> showAbout());
        helpMenu.add(about);

        bar.add(dataMenu);
        bar.add(helpMenu);
        return bar;
    }

    private JComponent buildToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        toolBar.add(updateAllButton);
        toolBar.addSeparator();
        toolBar.add(updateCategoryButton);
        toolBar.add(updateSelectedButton);
        toolBar.addSeparator();
        toolBar.add(cancelButton);
        toolBar.add(Box.createHorizontalStrut(16));

        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(220, 20));
        progressBar.setMaximumSize(new Dimension(220, 20));
        toolBar.add(progressBar);
        toolBar.add(Box.createHorizontalStrut(12));
        toolBar.add(statusLabel);
        toolBar.add(Box.createHorizontalGlue());
        return toolBar;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 10));
        footerLabel.setToolTipText("Local cache: " + AppPaths.stateFile());
        footer.add(footerLabel, BorderLayout.WEST);
        return footer;
    }

    private void installContextMenu(CategoryPanel panel) {
        JTable table = panel.table();
        JPopupMenu menu = new JPopupMenu();

        JMenuItem update = new JMenuItem("Update selected items");
        update.addActionListener(e -> updateSelected());
        JMenuItem open = new JMenuItem("Open on warframe.market");
        open.addActionListener(e -> openSelectedInBrowser(panel));
        menu.add(update);
        menu.add(open);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMenu(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openSelectedInBrowser(panel);
                }
            }

            private void maybeShowMenu(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && !table.isRowSelected(row)) {
                    table.setRowSelectionInterval(row, row);
                }
                menu.show(table, e.getX(), e.getY());
            }
        });
    }

    private CategoryPanel currentPanel() {
        return (CategoryPanel) tabs.getSelectedComponent();
    }

    private void refreshButtonLabels() {
        CategoryPanel panel = currentPanel();
        if (panel != null) {
            updateCategoryButton.setText("Update " + panel.category().displayName().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private void updateSelected() {
        List<String> slugs = currentPanel().selectedSlugs();
        if (slugs.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Select one or more rows first.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        startUpdate(UpdateRequest.items(slugs));
    }

    private void startUpdate(UpdateRequest request) {
        if (!service.start(request)) {
            JOptionPane.showMessageDialog(this,
                    "An update is already running. Cancel it first or wait for it to finish.",
                    "Update in progress", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setRunning(true);
        statusLabel.setText("Starting update of " + request.describe() + "…");
    }

    private void setRunning(boolean running) {
        updateAllButton.setEnabled(!running);
        updateCategoryButton.setEnabled(!running);
        updateSelectedButton.setEnabled(!running);
        cancelButton.setEnabled(running);
        if (running) {
            progressBar.setIndeterminate(true);
            progressBar.setString("working…");
        } else {
            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            progressBar.setString("idle");
        }
    }

    private void openSelectedInBrowser(CategoryPanel panel) {
        int viewRow = panel.table().getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        MarketItem item = panel.itemAtViewRow(viewRow);
        browse(URI.create(item.marketUrl()));
    }

    private void openDataFolder() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(AppPaths.dataDirectory().toFile());
                return;
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not open the data folder", e);
        }
        JOptionPane.showMessageDialog(this,
                "Local data is stored in:\n" + AppPaths.stateFile(),
                "Data location", JOptionPane.INFORMATION_MESSAGE);
    }

    private void browse(URI uri) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not open the browser", e);
        }
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                """
                Warframe Market Collector

                Collects the top sell orders for every "mod" and "prime" item
                listed on warframe.market and shows the lowest and highest
                asking price of each.

                Requests are capped at 3 per second, as required by the API.

                Local cache: %s""".formatted(AppPaths.stateFile()),
                "About", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Offers a first full collection when there is no cached data to show. */
    public void promptForInitialCollectionIfEmpty() {
        if (!database.isEmpty()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                """
                No local data was found.

                Collecting every mod and prime item takes roughly 12-15 minutes,
                because the API allows only 3 requests per second.

                Start collecting now?""",
                "First run", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            startUpdate(UpdateRequest.all());
        }
    }

    private void refreshFooter() {
        Instant fetchedAt = database.catalogFetchedAt();
        String catalogue = fetchedAt == null ? "never" : FOOTER_FORMAT.format(fetchedAt);
        footerLabel.setText("Catalogue downloaded: %s  ·  %d items tracked  ·  cache: %s"
                .formatted(catalogue, database.items().size(), AppPaths.stateFile()));
    }

    private void shutdown() {
        service.cancel();
        service.saveQuietly();
        service.close();
        dispose();
        System.exit(0);
    }

    // --- CollectorListener (always invoked on the EDT) ---------------------------------

    @Override
    public void onCatalogChanged() {
        panels.values().forEach(CategoryPanel::reload);
        refreshFooter();
    }

    @Override
    public void onItemUpdated(String slug) {
        panels.values().forEach(panel -> panel.itemUpdated(slug));
    }

    @Override
    public void onProgress(int done, int total, String message) {
        if (total > 0) {
            progressBar.setIndeterminate(false);
            progressBar.setMaximum(total);
            progressBar.setValue(done);
            progressBar.setString(done * 100 / total + "%");
        } else {
            progressBar.setIndeterminate(true);
        }
        statusLabel.setText(message);
    }

    @Override
    public void onFinished(String summary, boolean cancelled, Throwable failure) {
        setRunning(false);
        statusLabel.setText(summary);
        refreshFooter();
        panels.values().forEach(CategoryPanel::reload);
        if (failure != null) {
            JOptionPane.showMessageDialog(this, summary, "Update failed", JOptionPane.ERROR_MESSAGE);
        }
        // Leave the final message up for a while, then fall back to "Ready".
        Timer timer = new Timer(15_000, e -> {
            if (!service.isRunning()) {
                statusLabel.setText("Ready");
            }
        });
        timer.setRepeats(false);
        timer.start();
    }
}
