package com.warframemarket.collector.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Result of inspecting one item's top sell orders.
 *
 * <p>The API's {@code /orders/item/{slug}/top} endpoint returns the five best (cheapest)
 * visible sell orders. Those are sorted by price, and the two ends are kept.
 *
 * @param lowestPlatinum  cheapest of the top sell orders, {@code null} if nobody is selling
 * @param highestPlatinum dearest of the top sell orders, {@code null} if nobody is selling
 * @param sellOrderCount  how many sell orders the sample contained
 * @param fetchedAt       when this snapshot was taken
 * @param error           failure message if the lookup did not succeed, otherwise {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PriceSnapshot(
        Integer lowestPlatinum,
        Integer highestPlatinum,
        int sellOrderCount,
        Instant fetchedAt,
        String error) {

    public static PriceSnapshot of(Integer lowest, Integer highest, int count, Instant fetchedAt) {
        return new PriceSnapshot(lowest, highest, count, fetchedAt, null);
    }

    public static PriceSnapshot failed(String message, Instant fetchedAt) {
        return new PriceSnapshot(null, null, 0, fetchedAt, message);
    }

    @JsonIgnore
    public boolean isFailed() {
        return error != null;
    }

    /** Difference between the dearest and cheapest sampled sell order, if both are known. */
    @JsonIgnore
    public Integer spread() {
        if (lowestPlatinum == null || highestPlatinum == null) {
            return null;
        }
        return highestPlatinum - lowestPlatinum;
    }
}
