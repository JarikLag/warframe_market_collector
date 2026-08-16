package com.warframemarket.collector.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** The item groups the collector tracks, one per GUI tab. */
public enum Category {

    MODS("Mods", "mod"),
    PRIME("Prime", "prime"),
    PRIME_SETS("Prime sets", "prime", "set");

    /**
     * The most specific category wins, so "prime"+"set" lands in {@link #PRIME_SETS}
     * rather than {@link #PRIME}. Ties are broken by declaration order, which keeps the
     * two "prime" augment mods (tagged both {@code mod} and {@code prime}) in
     * {@link #MODS} - the order tabs appear in is therefore free to change without
     * changing how items are classified.
     */
    private static final Comparator<Category> MOST_SPECIFIC_FIRST =
            Comparator.comparingInt((Category c) -> c.requiredTags.size()).reversed()
                    .thenComparingInt(Category::ordinal);

    private final String displayName;
    private final List<String> requiredTags;

    Category(String displayName, String... requiredTags) {
        this.displayName = displayName;
        this.requiredTags = List.of(requiredTags);
    }

    public String displayName() {
        return displayName;
    }

    /** Every tag an item must carry to fall into this category. */
    public List<String> requiredTags() {
        return requiredTags;
    }

    /**
     * Classifies an item by its tags, or returns empty if it belongs to no tracked group.
     * Each item matches exactly one category, so it is requested exactly once.
     */
    public static Optional<Category> fromTags(Collection<String> tags) {
        if (tags == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(category -> tags.containsAll(category.requiredTags))
                .min(MOST_SPECIFIC_FIRST);
    }
}
