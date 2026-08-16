package com.warframemarket.collector.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Wire format of the warframe.market v2 API. Only the fields the collector needs are
 * mapped; everything else in the payload is ignored.
 */
public final class ApiDtos {

    private ApiDtos() {
    }

    /** {@code GET /v2/items} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemsResponse(String apiVersion, List<ApiItem> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiItem(String id, String slug, List<String> tags, Map<String, Localization> i18n) {

        /** Display name, preferring English and falling back to any available locale. */
        public String displayName() {
            if (i18n != null) {
                Localization en = i18n.get("en");
                if (en != null && en.name() != null) {
                    return en.name();
                }
                for (Localization other : i18n.values()) {
                    if (other != null && other.name() != null) {
                        return other.name();
                    }
                }
            }
            return slug;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Localization(String name, String icon, String thumb) {
    }

    /** {@code GET /v2/orders/item/{slug}/top} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopOrdersResponse(String apiVersion, TopOrders data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopOrders(List<Order> sell, List<Order> buy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(String id, String type, Integer platinum, Integer quantity, Boolean visible) {
    }
}
