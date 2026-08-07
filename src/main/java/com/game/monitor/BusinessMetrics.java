package com.game.monitor;

import com.game.service.PresenceService;
import com.game.util.DBUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BusinessMetrics {

    private static final Logger LOGGER = Logger.getLogger(BusinessMetrics.class.getName());
    private static final PresenceService PRESENCE_SERVICE = new PresenceService();

    private final DoubleSupplier onlinePlayersSupplier;
    private final Counter tradeCounter;

    BusinessMetrics(MeterRegistry registry, DoubleSupplier onlinePlayersSupplier) {
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier);

        Gauge.builder("gameexchange_players_online", this.onlinePlayersSupplier,
                        DoubleSupplier::getAsDouble)
                .description("Current online players with an active lease")
                .register(registry);

        tradeCounter = Counter.builder("gameexchange_trade_total")
                .description("Total successful trades")
                .register(registry);
    }

    public static void init() {
        getInstance();
    }

    public static void tradeSuccess() {
        getInstance().recordTradeSuccess();
    }

    void recordTradeSuccess() {
        tradeCounter.increment();
    }

    private static double queryOnlinePlayers() {
        try (Connection conn = DBUtil.getConnection()) {
            return PRESENCE_SERVICE.countOnlinePlayers(conn);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "采集在线玩家指标失败", e);
            return Double.NaN;
        }
    }

    private static BusinessMetrics getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final BusinessMetrics INSTANCE = new BusinessMetrics(
                MetricsConfig.getRegistry(), BusinessMetrics::queryOnlinePlayers);
    }
}
