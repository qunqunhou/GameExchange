package com.game.servlet;

import com.alibaba.fastjson.JSONObject;
import com.game.entity.Player;
import com.game.service.TradeService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TradeServletTest {

    private static final int SESSION_PLAYER_ID = 17;
    private static final int MARKET_ID = 42;

    @Test
    void getReturnsMethodNotAllowedJsonWithoutCallingService() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            JSONObject json = invokeGet(fixture);

            assertJson(json, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "不支持GET请求");
            verifyNoInteractions(fixture.service);
            verify(fixture.response)
                    .setContentType("application/json;charset=utf-8");
        }
    }

    @Test
    void unauthenticatedPostIsRejectedWithoutCallingService() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            when(fixture.request.getSession(false)).thenReturn(null);

            JSONObject json = invokePost(fixture);

            assertJson(json, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            verifyNoInteractions(fixture.service);
        }
    }

    @Test
    void missingMarketIdIsRejectedWithoutCallingService() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            when(fixture.request.getParameter("marketId")).thenReturn(null);

            JSONObject json = invokePost(fixture);

            assertJson(json, HttpServletResponse.SC_BAD_REQUEST,
                    "缺少marketId参数");
            verifyNoInteractions(fixture.service);
        }
    }

    @Test
    void blankMarketIdIsRejectedWithoutCallingService() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            when(fixture.request.getParameter("marketId")).thenReturn("   ");

            JSONObject json = invokePost(fixture);

            assertJson(json, HttpServletResponse.SC_BAD_REQUEST,
                    "缺少marketId参数");
            verifyNoInteractions(fixture.service);
        }
    }

    @Test
    void nonNumericMarketIdIsRejectedWithoutCallingService() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            when(fixture.request.getParameter("marketId"))
                    .thenReturn("not-a-number");

            JSONObject json = invokePost(fixture);

            assertJson(json, HttpServletResponse.SC_BAD_REQUEST,
                    "marketId格式错误");
            verifyNoInteractions(fixture.service);
        }
    }

    @Test
    void successfulPostUsesSessionIdentityAndIgnoresClientPlayerParameters()
            throws Exception {
        try (ServletFixture fixture = createFixture()) {
            when(fixture.request.getParameter("buyerId")).thenReturn("999");
            when(fixture.request.getParameter("playerId")).thenReturn("999");
            when(fixture.service.buyItem(MARKET_ID, SESSION_PLAYER_ID))
                    .thenReturn("购买成功");

            JSONObject json = invokePost(fixture);

            assertJson(json, HttpServletResponse.SC_OK, "购买成功");
            verify(fixture.service).buyItem(MARKET_ID, SESSION_PLAYER_ID);
            verify(fixture.service, never()).buyItem(MARKET_ID, 999);
            verify(fixture.request, never()).getParameter("buyerId");
            verify(fixture.request, never()).getParameter("playerId");
            verify(fixture.request).setCharacterEncoding("UTF-8");
            verify(fixture.response)
                    .setContentType("application/json;charset=utf-8");
        }
    }

    @Test
    void businessFailureUsesCurrentInternalErrorJsonMapping() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            when(fixture.service.buyItem(MARKET_ID, SESSION_PLAYER_ID))
                    .thenReturn("金币不足");

            JSONObject json = invokePost(fixture);

            assertJson(json, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "金币不足");
            verify(fixture.service).buyItem(MARKET_ID, SESSION_PLAYER_ID);
        }
    }

    @Test
    void serviceExceptionPropagatesWithCurrentServletBehavior() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            IllegalStateException serviceFailure =
                    new IllegalStateException("controlled service failure");
            when(fixture.service.buyItem(MARKET_ID, SESSION_PLAYER_ID))
                    .thenThrow(serviceFailure);

            IllegalStateException actual = assertThrows(IllegalStateException.class,
                    () -> fixture.servlet.post(fixture.request, fixture.response));

            assertSame(serviceFailure, actual);
            assertTrue(fixture.responseBody.toString().isEmpty());
            verify(fixture.service).buyItem(MARKET_ID, SESSION_PLAYER_ID);
        }
    }

    private ServletFixture createFixture() throws IOException {
        MockedConstruction<TradeService> construction =
                mockConstruction(TradeService.class);
        try {
            TestableTradeServlet servlet = new TestableTradeServlet();
            TradeService service = construction.constructed().get(0);
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            HttpSession session = mock(HttpSession.class);
            StringWriter responseBody = new StringWriter();
            PrintWriter writer = new PrintWriter(responseBody);

            Player sessionPlayer = new Player();
            sessionPlayer.setId(SESSION_PLAYER_ID);
            sessionPlayer.setUsername("trade_api_buyer");

            when(request.getSession(false)).thenReturn(session);
            when(session.getAttribute("player")).thenReturn(sessionPlayer);
            when(request.getParameter("marketId"))
                    .thenReturn(String.valueOf(MARKET_ID));
            when(response.getWriter()).thenReturn(writer);

            return new ServletFixture(construction, servlet, service, request,
                    response, responseBody, writer);
        } catch (RuntimeException | Error e) {
            construction.close();
            throw e;
        }
    }

    private JSONObject invokeGet(ServletFixture fixture)
            throws ServletException, IOException {
        fixture.servlet.get(fixture.request, fixture.response);
        return parseResponse(fixture);
    }

    private JSONObject invokePost(ServletFixture fixture)
            throws ServletException, IOException {
        fixture.servlet.post(fixture.request, fixture.response);
        return parseResponse(fixture);
    }

    private JSONObject parseResponse(ServletFixture fixture) {
        fixture.writer.flush();
        return JSONObject.parseObject(fixture.responseBody.toString());
    }

    private static void assertJson(JSONObject json, int expectedCode,
                                   String expectedMessage) {
        assertEquals(expectedCode, json.getIntValue("code"));
        assertEquals(expectedMessage, json.getString("msg"));
        assertEquals(2, json.size());
    }

    private static final class TestableTradeServlet extends TradeServlet {
        private void get(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            doGet(request, response);
        }

        private void post(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            doPost(request, response);
        }
    }

    private static final class ServletFixture implements AutoCloseable {
        private final MockedConstruction<TradeService> construction;
        private final TestableTradeServlet servlet;
        private final TradeService service;
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final StringWriter responseBody;
        private final PrintWriter writer;

        private ServletFixture(MockedConstruction<TradeService> construction,
                               TestableTradeServlet servlet,
                               TradeService service,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               StringWriter responseBody,
                               PrintWriter writer) {
            this.construction = construction;
            this.servlet = servlet;
            this.service = service;
            this.request = request;
            this.response = response;
            this.responseBody = responseBody;
            this.writer = writer;
        }

        @Override
        public void close() {
            construction.close();
        }
    }
}
