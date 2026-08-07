package com.game.monitor;


import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;


public class MetricsConfig {


    private static final PrometheusMeterRegistry registry =
            new PrometheusMeterRegistry(
                    PrometheusConfig.DEFAULT
            );


    public static PrometheusMeterRegistry getRegistry(){

        return registry;
    }
}