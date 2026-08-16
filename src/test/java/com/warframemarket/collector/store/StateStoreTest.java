package com.warframemarket.collector.store;

import com.warframemarket.collector.model.Category;
import com.warframemarket.collector.model.MarketItem;
import com.warframemarket.collector.model.PriceSnapshot;
import com.warframemarket.collector.service.MarketDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateStoreTest {

    @Test
    void savedDataIsRestoredExactly(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("nested").resolve("market-data.json");
        StateStore store = new StateStore(file);
        Instant fetchedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        PersistedState original = new PersistedState(
                PersistedState.CURRENT_SCHEMA_VERSION,
                fetchedAt,
                List.of(new MarketItem("id1", "vitality", "Vitality", List.of("mod"), Category.MODS),
                        new MarketItem("id2", "ash_prime_set", "Ash Prime Set", List.of("prime"), Category.PRIME)),
                Map.of("vitality", PriceSnapshot.of(12, 20, 5, fetchedAt),
                        "ash_prime_set", PriceSnapshot.failed("HTTP 503", fetchedAt)));

        store.save(original);
        assertTrue(Files.exists(file), "state file should have been created");

        PersistedState reloaded = store.load();
        assertEquals(fetchedAt, reloaded.catalogFetchedAt());
        assertEquals(2, reloaded.items().size());
        assertEquals(Category.MODS, reloaded.items().get(0).category());
        assertEquals(12, reloaded.prices().get("vitality").lowestPlatinum());
        assertEquals(20, reloaded.prices().get("vitality").highestPlatinum());
        assertTrue(reloaded.prices().get("ash_prime_set").isFailed());
    }

    @Test
    void missingFileYieldsEmptyState(@TempDir Path dir) {
        PersistedState state = new StateStore(dir.resolve("absent.json")).load();
        assertTrue(state.items().isEmpty());
        assertTrue(state.prices().isEmpty());
        assertNull(state.catalogFetchedAt());
    }

    @Test
    void corruptFileIsIgnoredRatherThanFatal(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("broken.json");
        Files.writeString(file, "{ this is not json");
        assertTrue(new StateStore(file).load().items().isEmpty());
    }

    @Test
    void refreshingTheCatalogueKeepsKnownPricesAndDropsDelistedOnes(@TempDir Path dir) {
        MarketDatabase database = MarketDatabase.from(PersistedState.empty());
        database.replaceCatalog(List.of(
                new MarketItem("1", "vitality", "Vitality", List.of("mod"), Category.MODS),
                new MarketItem("2", "gone", "Gone", List.of("mod"), Category.MODS)), Instant.now());
        database.putPrice("vitality", PriceSnapshot.of(12, 20, 5, Instant.now()));
        database.putPrice("gone", PriceSnapshot.of(1, 2, 3, Instant.now()));

        database.replaceCatalog(List.of(
                new MarketItem("1", "vitality", "Vitality", List.of("mod"), Category.MODS)), Instant.now());

        assertEquals(12, database.priceOf("vitality").lowestPlatinum());
        assertNull(database.priceOf("gone"), "prices of delisted items should be dropped");
        assertEquals(1, database.itemsIn(Category.MODS).size());
        assertTrue(database.itemsIn(Category.PRIME).isEmpty());
    }
}
