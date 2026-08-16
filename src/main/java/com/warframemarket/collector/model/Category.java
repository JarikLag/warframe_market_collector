package com.warframemarket.collector.model;

import java.util.Collection;
import java.util.Optional;

/** The two item groups the collector tracks, one per GUI tab. */
public enum Category {

    MODS("Mods", "mod"),
    PRIME("Prime", "prime");

    private final String displayName;
    private final String tag;

    Category(String displayName, String tag) {
        this.displayName = displayName;
        this.tag = tag;
    }

    public String displayName() {
        return displayName;
    }

    /** The warframe.market tag that puts an item into this category. */
    public String tag() {
        return tag;
    }

    /**
     * Classifies an item by its tags.
     *
     * <p>A handful of items carry both tags (primed mods). They are put in {@link #MODS}
     * so that every item belongs to exactly one group and is requested exactly once.
     */
    public static Optional<Category> fromTags(Collection<String> tags) {
        if (tags == null) {
            return Optional.empty();
        }
        if (tags.contains(MODS.tag)) {
            return Optional.of(MODS);
        }
        if (tags.contains(PRIME.tag)) {
            return Optional.of(PRIME);
        }
        return Optional.empty();
    }
}
