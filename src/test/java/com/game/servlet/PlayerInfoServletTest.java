package com.game.servlet;

import com.alibaba.fastjson.JSONObject;
import com.game.dao.PlayerDao;
import com.game.entity.Player;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerInfoServletTest {

    private static final int SESSION_PLAYER_ID = 23;

    @Test
    void unauthenticatedRequestIsForbiddenWithoutDaoLookup() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            when(fixture.request.getSession(false)).thenReturn(null);

            JSONObject json = invokeGet(fixture);

            assertJson(json, HttpServletResponse.SC_FORBIDDEN,
                    "无权访问该玩家信息", 2);
            verify(fixture.response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            verifyNoDaoLookup(fixture);
        }
    }

    @Test
    void sessionWithoutPlayerIsForbiddenWithoutDaoLookup() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            when(fixture.session.getAttribute("player")).thenReturn(null);

            JSONObject json = invokeGet(fixture);

            assertJson(json, HttpServletResponse.SC_FORBIDDEN,
                    "无权访问该玩家信息", 2);
            verify(fixture.response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            verifyNoDaoLookup(fixture);
        }
    }

    @Test
    void missingPlayerIdIsRejectedWithoutDaoLookup() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            when(fixture.request.getParameter("playerId")).thenReturn(null);

            JSONObject json = invokeGet(fixture);

            assertJson(json, HttpServletResponse.SC_BAD_REQUEST, "参数错误", 2);
            verifyNoDaoLookup(fixture);
        }
    }

    @Test
    void blankPlayerIdIsRejectedWithoutDaoLookup() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            when(fixture.request.getParameter("playerId")).thenReturn("   ");

            JSONObject json = invokeGet(fixture);

            assertJson(json, HttpServletResponse.SC_BAD_REQUEST, "参数错误", 2);
            verifyNoDaoLookup(fixture);
        }
    }

    @Test
    void nonNumericPlayerIdIsRejectedWithoutDaoLookup() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            when(fixture.request.getParameter("playerId"))
                    .thenReturn("not-a-number");

            JSONObject json = invokeGet(fixture);

            assertJson(json, HttpServletResponse.SC_BAD_REQUEST,
                    "参数格式错误", 2);
            verifyNoDaoLookup(fixture);
        }
    }

    @Test
    void otherPlayerIdIsForbiddenWithoutDaoLookup() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            when(fixture.request.getParameter("playerId"))
                    .thenReturn(String.valueOf(SESSION_PLAYER_ID + 1));

            JSONObject json = invokeGet(fixture);

            assertJson(json, HttpServletResponse.SC_FORBIDDEN,
                    "无权访问该玩家信息", 2);
            verify(fixture.response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            verifyNoDaoLookup(fixture);
        }
    }

    @Test
    void ownPlayerInfoReturnsOnlyApprovedFields() throws Exception {
        try (ServletFixture fixture = createFixture()) {
            Player storedPlayer = new Player();
            storedPlayer.setId(SESSION_PLAYER_ID);
            storedPlayer.setUsername("player_info_user");
            storedPlayer.setGold(3456L);
            storedPlayer.setPassword("password-must-not-leak");
            when(fixture.playerDao.findById(SESSION_PLAYER_ID))
                    .thenReturn(storedPlayer);

            JSONObject json = invokeGet(fixture);

            assertEquals(HttpServletResponse.SC_OK, json.getIntValue("code"));
            assertEquals("player_info_user", json.getString("username"));
            assertEquals(3456L, json.getLongValue("gold"));
            assertEquals(3, json.size());
            assertFalse(json.containsKey("password"));
            verify(fixture.playerDao).findById(SESSION_PLAYER_ID);
            verify(fixture.response)
                    .setContentType("application/json;charset=utf-8");
        }
    }

    private ServletFixture createFixture() throws IOException {
        MockedConstruction<PlayerDao> construction =
                mockConstruction(PlayerDao.class);
        try {
            TestablePlayerInfoServlet servlet = new TestablePlayerInfoServlet();
            PlayerDao playerDao = construction.constructed().get(0);
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            HttpSession session = mock(HttpSession.class);
            StringWriter responseBody = new StringWriter();
            PrintWriter writer = new PrintWriter(responseBody);

            Player sessionPlayer = new Player();
            sessionPlayer.setId(SESSION_PLAYER_ID);
            sessionPlayer.setUsername("session_player");

            when(request.getSession(false)).thenReturn(session);
            when(session.getAttribute("player")).thenReturn(sessionPlayer);
            when(request.getParameter("playerId"))
                    .thenReturn(String.valueOf(SESSION_PLAYER_ID));
            when(response.getWriter()).thenReturn(writer);

            return new ServletFixture(construction, servlet, playerDao, request,
                    response, session, responseBody, writer);
        } catch (RuntimeException | Error e) {
            construction.close();
            throw e;
        }
    }

    private JSONObject invokeGet(ServletFixture fixture)
            throws ServletException, IOException {
        fixture.servlet.get(fixture.request, fixture.response);
        fixture.writer.flush();
        return JSONObject.parseObject(fixture.responseBody.toString());
    }

    private void verifyNoDaoLookup(ServletFixture fixture) {
        verify(fixture.playerDao, never()).findById(anyInt());
    }

    private static void assertJson(JSONObject json, int expectedCode,
                                   String expectedMessage,
                                   int expectedFieldCount) {
        assertEquals(expectedCode, json.getIntValue("code"));
        assertEquals(expectedMessage, json.getString("msg"));
        assertEquals(expectedFieldCount, json.size());
    }

    private static final class TestablePlayerInfoServlet
            extends PlayerInfoServlet {
        private void get(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            doGet(request, response);
        }
    }

    private static final class ServletFixture implements AutoCloseable {
        private final MockedConstruction<PlayerDao> construction;
        private final TestablePlayerInfoServlet servlet;
        private final PlayerDao playerDao;
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final HttpSession session;
        private final StringWriter responseBody;
        private final PrintWriter writer;

        private ServletFixture(MockedConstruction<PlayerDao> construction,
                               TestablePlayerInfoServlet servlet,
                               PlayerDao playerDao,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               HttpSession session,
                               StringWriter responseBody,
                               PrintWriter writer) {
            this.construction = construction;
            this.servlet = servlet;
            this.playerDao = playerDao;
            this.request = request;
            this.response = response;
            this.session = session;
            this.responseBody = responseBody;
            this.writer = writer;
        }

        @Override
        public void close() {
            construction.close();
        }
    }
}
