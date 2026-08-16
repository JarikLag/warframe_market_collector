package com.warframemarket.collector.service;

import com.warframemarket.collector.model.Category;

import java.util.List;

/**
 * Describes one refresh job.
 *
 * @param scope          what to refresh
 * @param category       target category when {@code scope == CATEGORY}
 * @param slugs          target items when {@code scope == ITEMS}
 * @param refreshCatalog whether to re-download the item list first (1 extra request)
 */
public record UpdateRequest(Scope scope, Category category, List<String> slugs, boolean refreshCatalog) {

    public enum Scope {
        /** Every mod and every prime item. */
        ALL,
        /** Every item of one category. */
        CATEGORY,
        /** An explicit set of items. */
        ITEMS
    }

    public static UpdateRequest all() {
        return new UpdateRequest(Scope.ALL, null, List.of(), true);
    }

    public static UpdateRequest category(Category category) {
        return new UpdateRequest(Scope.CATEGORY, category, List.of(), false);
    }

    public static UpdateRequest items(List<String> slugs) {
        return new UpdateRequest(Scope.ITEMS, null, List.copyOf(slugs), false);
    }

    public String describe() {
        return switch (scope) {
            case ALL -> "all items";
            case CATEGORY -> category.displayName().toLowerCase(java.util.Locale.ROOT);
            case ITEMS -> slugs.size() == 1 ? slugs.get(0) : slugs.size() + " items";
        };
    }
}
