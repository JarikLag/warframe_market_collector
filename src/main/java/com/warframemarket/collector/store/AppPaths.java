package com.warframemarket.collector.store;

import java.nio.file.Path;
import java.util.Locale;

/** Resolves the per-user data directory following each platform's convention. */
public final class AppPaths {

    private static final String APP_DIR_NAME = "WarframeMarketCollector";

    private AppPaths() {
    }

    /**
     * Windows: {@code %LOCALAPPDATA%\WarframeMarketCollector}<br>
     * Linux/other: {@code $XDG_DATA_HOME/warframe-market-collector} or
     * {@code ~/.local/share/warframe-market-collector}<br>
     * macOS: {@code ~/Library/Application Support/WarframeMarketCollector}
     */
    public static Path dataDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home", ".");

        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path base = (localAppData != null && !localAppData.isBlank())
                    ? Path.of(localAppData)
                    : Path.of(home, "AppData", "Local");
            return base.resolve(APP_DIR_NAME);
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return Path.of(home, "Library", "Application Support", APP_DIR_NAME);
        }
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        Path base = (xdgDataHome != null && !xdgDataHome.isBlank())
                ? Path.of(xdgDataHome)
                : Path.of(home, ".local", "share");
        return base.resolve("warframe-market-collector");
    }

    public static Path stateFile() {
        return dataDirectory().resolve("market-data.json");
    }
}
