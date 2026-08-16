package com.warframemarket.collector.store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.warframemarket.collector.model.MarketItem;
import com.warframemarket.collector.model.PriceSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * On-disk shape of the local cache.
 *
 * @param schemaVersion  bumped when the format changes so old files can be discarded
 * @param catalogFetchedAt when the item list was last downloaded
 * @param items          the filtered item catalogue
 * @param prices         price snapshots keyed by item slug
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PersistedState(
        int schemaVersion,
        Instant catalogFetchedAt,
        List<MarketItem> items,
        Map<String, PriceSnapshot> prices) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static PersistedState empty() {
        return new PersistedState(CURRENT_SCHEMA_VERSION, null, List.of(), Map.of());
    }
}
