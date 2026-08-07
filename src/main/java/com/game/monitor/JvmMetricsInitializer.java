package com.game.monitor;



import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;




public class JvmMetricsInitializer {

    private static boolean initialized = false;


    public static synchronized void init(){

        if(initialized){
            return;
        }


        var registry = MetricsConfig.getRegistry();


        new JvmMemoryMetrics()
                .bindTo(registry);


        new JvmGcMetrics()
                .bindTo(registry);


        new JvmThreadMetrics()
                .bindTo(registry);


        new ProcessorMetrics()
                .bindTo(registry);


        initialized = true;
    }
}