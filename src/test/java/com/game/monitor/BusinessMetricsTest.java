package com.game.monitor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessMetricsTest {

    @Test
    void initRegistersEachProductionMeterOnlyOnce() {
        BusinessMetrics.init();
        BusinessMetrics.init();

        var registry = MetricsConfig.getRegistry();
        assertEquals(1, registry.find("gameexchange_players_online")
                .meters().size());
        assertEquals(1, registry.find("gameexchange_trade_total")
                .meters().size());
    }

    @Test
    void samplesLatestOnlinePlayerCountAndRecordsTrades() {
        var registry = new SimpleMeterRegistry();
        try {
            var onlinePlayers = new AtomicInteger(2);
            var metrics = new BusinessMetrics(registry, onlinePlayers::get);

            assertEquals(2.0, onlinePlayers(registry));

            onlinePlayers.set(5);
            metrics.recordTradeSuccess();
            metrics.recordTradeSuccess();

            assertEquals(5.0, onlinePlayers(registry));
            assertEquals(2.0, registry.get("gameexchange_trade_total")
                    .counter().count());
        } finally {
            registry.close();
        }
    }

    @Test
    void reportsUnknownOnlinePlayerCountAsNan() {
        var registry = new SimpleMeterRegistry();
        try {
            new BusinessMetrics(registry, () -> Double.NaN);

            assertTrue(Double.isNaN(onlinePlayers(registry)));
        } finally {
            registry.close();
        }
    }

    private double onlinePlayers(SimpleMeterRegistry registry) {
        return registry.get("gameexchange_players_online").gauge().value();
    }
}
