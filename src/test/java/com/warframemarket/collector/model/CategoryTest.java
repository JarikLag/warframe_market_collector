package com.warframemarket.collector.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryTest {

    @Test
    void keepsModsAndPrimes() {
        assertEquals(Optional.of(Category.MODS), Category.fromTags(List.of("mod", "rare")));
        assertEquals(Optional.of(Category.PRIME), Category.fromTags(List.of("prime", "component")));
    }

    @Test
    void dropsEverythingElse() {
        assertTrue(Category.fromTags(List.of("weapon", "secondary", "syndicate")).isEmpty());
        assertTrue(Category.fromTags(List.of()).isEmpty());
        assertTrue(Category.fromTags(null).isEmpty());
    }

    @Test
    void itemsCarryingBothTagsGoToModsOnly() {
        assertEquals(Optional.of(Category.MODS), Category.fromTags(List.of("mod", "prime")));
    }
}
