package com.warframemarket.collector.service;

import com.warframemarket.collector.model.Category;
import com.warframemarket.collector.model.MarketItem;
import com.warframemarket.collector.model.PriceSnapshot;
import com.warframemarket.collector.store.PersistedState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory view of the collected data, shared between the worker threads that write
 * prices and the Swing thread that reads them.
 */
public final class MarketDatabase {

    private volatile List<MarketItem> items = List.of();
    private volatile Instant catalogFetchedAt;
    private final Map<String, PriceSnapshot> prices = new ConcurrentHashMap<>();

    public static MarketDatabase from(PersistedState state) {
        MarketDatabase db = new MarketDatabase();
        db.items = List.copyOf(state.items());
        db.catalogFetchedAt = state.catalogFetchedAt();
        db.prices.putAll(state.prices());
        return db;
    }

    public PersistedState toPersistedState() {
        return new PersistedState(
                PersistedState.CURRENT_SCHEMA_VERSION,
                catalogFetchedAt,
                items,
                new LinkedHashMap<>(prices));
    }

    public List<MarketItem> items() {
        return items;
    }

    public List<MarketItem> itemsIn(Category category) {
        return items.stream().filter(i -> i.category() == category).toList();
    }

    public Optional<MarketItem> findBySlug(String slug) {
        return items.stream().filter(i -> i.slug().equals(slug)).findFirst();
    }

    public PriceSnapshot priceOf(String slug) {
        return prices.get(slug);
    }

    public void putPrice(String slug, PriceSnapshot snapshot) {
        prices.put(slug, snapshot);
    }

    public Instant catalogFetchedAt() {
        return catalogFetchedAt;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Installs a freshly downloaded catalogue. Prices of items that are still on the
     * market are preserved; prices of items that disappeared are dropped so the cache
     * does not grow forever.
     */
    public void replaceCatalog(List<MarketItem> newItems, Instant fetchedAt) {
        List<MarketItem> sorted = new ArrayList<>(newItems);
        sorted.sort(Comparator.comparing(MarketItem::name, String.CASE_INSENSITIVE_ORDER));
        this.items = List.copyOf(sorted);
        this.catalogFetchedAt = fetchedAt;

        java.util.Set<String> live = new java.util.HashSet<>();
        for (MarketItem item : sorted) {
            live.add(item.slug());
        }
        prices.keySet().retainAll(live);
    }
}
