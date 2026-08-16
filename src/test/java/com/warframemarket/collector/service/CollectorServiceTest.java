package com.warframemarket.collector.service;

import com.warframemarket.collector.api.ApiDtos;
import com.warframemarket.collector.model.PriceSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CollectorServiceTest {

    private static ApiDtos.Order sell(int platinum, Boolean visible) {
        return new ApiDtos.Order("id" + platinum, "sell", platinum, 1, visible);
    }

    @Test
    void keepsLowestAndHighestOfTheSampledSellOrders() {
        ApiDtos.TopOrders top = new ApiDtos.TopOrders(
                List.of(sell(80, true), sell(78, true), sell(95, true), sell(79, true)),
                List.of());

        PriceSnapshot snapshot = CollectorService.summarise(top);

        assertEquals(78, snapshot.lowestPlatinum());
        assertEquals(95, snapshot.highestPlatinum());
        assertEquals(4, snapshot.sellOrderCount());
        assertEquals(17, snapshot.spread());
    }

    @Test
    void ignoresHiddenOrdersAndOrdersWithoutAPrice() {
        ApiDtos.TopOrders top = new ApiDtos.TopOrders(
                List.of(sell(10, false), sell(50, true), new ApiDtos.Order("x", "sell", null, 1, true)),
                List.of());

        PriceSnapshot snapshot = CollectorService.summarise(top);

        assertEquals(50, snapshot.lowestPlatinum());
        assertEquals(50, snapshot.highestPlatinum());
        assertEquals(1, snapshot.sellOrderCount());
    }

    @Test
    void reportsNoPriceWhenNobodyIsSelling() {
        PriceSnapshot snapshot = CollectorService.summarise(new ApiDtos.TopOrders(List.of(), List.of()));

        assertNull(snapshot.lowestPlatinum());
        assertNull(snapshot.highestPlatinum());
        assertEquals(0, snapshot.sellOrderCount());
        assertNull(snapshot.spread());
    }

    @Test
    void toleratesAMissingSellArray() {
        PriceSnapshot snapshot = CollectorService.summarise(new ApiDtos.TopOrders(null, null));

        assertNull(snapshot.lowestPlatinum());
        assertEquals(0, snapshot.sellOrderCount());
    }
}
