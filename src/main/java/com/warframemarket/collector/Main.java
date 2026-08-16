package com.warframemarket.collector;

import com.warframemarket.collector.api.WarframeMarketClient;
import com.warframemarket.collector.service.CollectorService;
import com.warframemarket.collector.service.MarketDatabase;
import com.warframemarket.collector.store.PersistedState;
import com.warframemarket.collector.store.StateStore;
import com.warframemarket.collector.ui.MainWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Entry point: restores the local cache, then shows the window. */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("""
                    Warframe Market Collector needs a graphical display.
                    On a headless Linux machine, run it through X11/Wayland forwarding or a virtual display
                    (for example: xvfb-run java -jar warframe-market-collector-1.0.0-all.jar).""");
            System.exit(2);
        }

        applyNativeLookAndFeel();

        StateStore store = new StateStore();
        PersistedState persisted = store.load();
        MarketDatabase database = MarketDatabase.from(persisted);
        LOG.log(Level.INFO, "Loaded {0} cached items from {1}",
                new Object[] {database.items().size(), store.file()});

        WarframeMarketClient client = new WarframeMarketClient();
        // Callbacks are delivered on the event dispatch thread, so listeners can touch Swing directly.
        CollectorService service = new CollectorService(client, store, database, SwingUtilities::invokeLater);

        Runtime.getRuntime().addShutdownHook(new Thread(service::saveQuietly, "save-on-exit"));

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow(service);
            window.setVisible(true);
            window.promptForInitialCollectionIfEmpty();
        });
    }

    private static void applyNativeLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LOG.log(Level.FINE, "Falling back to the default look and feel", e);
        }
    }

    private Main() {
    }
}
