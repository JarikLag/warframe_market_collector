package com.warframemarket.collector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One tradable item that survived the tag filter.
 *
 * @param id       warframe.market item id
 * @param slug     url-safe name, used both as the API path segment and as the local key
 * @param name     human-readable English name
 * @param tags     the item's raw tags, kept so the filtering decision stays auditable
 * @param category the group this item was sorted into
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketItem(String id, String slug, String name, List<String> tags, Category category) {

    public String marketUrl() {
        return "https://warframe.market/items/" + slug;
    }
}
