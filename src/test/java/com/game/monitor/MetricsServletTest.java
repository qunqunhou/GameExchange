package com.game.monitor;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsServletTest {

    @Test
    void exposesPrometheusPayloadOnInternalEndpoint() throws Exception {
        String payload = "# TYPE gameexchange_metrics_servlet_test_total counter\n"
                + "gameexchange_metrics_servlet_test_total 1.0\n";
        PrometheusMeterRegistry registry = mock(PrometheusMeterRegistry.class);
        when(registry.scrape()).thenReturn(payload);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        try (MockedStatic<MetricsConfig> metricsConfig = mockStatic(MetricsConfig.class)) {
            metricsConfig.when(MetricsConfig::getRegistry).thenReturn(registry);

            new MetricsServlet().doGet(request, response);

            verify(response).setContentType("text/plain; version=0.0.4");
            verify(registry).scrape();
            assertEquals(payload, body.toString());
        }
    }
}
