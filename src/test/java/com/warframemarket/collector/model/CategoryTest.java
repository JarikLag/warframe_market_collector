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
        assertTrue(Category.fromTags(List.of("set", "weapon")).isEmpty(), "a non-prime set is not tracked");
        assertTrue(Category.fromTags(List.of()).isEmpty());
        assertTrue(Category.fromTags(null).isEmpty());
    }

    @Test
    void primeSetsGoToTheirOwnGroupRatherThanPrime() {
        // e.g. frost_prime_set -> [set, prime, warframe]
        assertEquals(Optional.of(Category.PRIME_SETS),
                Category.fromTags(List.of("set", "prime", "warframe")));
        assertEquals(Optional.of(Category.PRIME_SETS),
                Category.fromTags(List.of("weapon", "prime", "set", "secondary")));
    }

    @Test
    void primePartsStayInPrime() {
        // e.g. ash_prime_blueprint -> prime but not a set
        assertEquals(Optional.of(Category.PRIME),
                Category.fromTags(List.of("prime", "blueprint", "warframe")));
    }

    @Test
    void itemsCarryingBothModAndPrimeGoToModsOnly() {
        // e.g. molecular_fission -> [nova, mod, warframe, prime, augment, rare]
        assertEquals(Optional.of(Category.MODS), Category.fromTags(List.of("mod", "prime")));
        assertEquals(Optional.of(Category.MODS),
                Category.fromTags(List.of("nova", "mod", "warframe", "prime", "augment", "rare")));
    }

    @Test
    void everyCategoryHasADistinctTabName() {
        long distinct = java.util.Arrays.stream(Category.values())
                .map(Category::displayName)
                .distinct()
                .count();
        assertEquals(Category.values().length, distinct);
    }
}
