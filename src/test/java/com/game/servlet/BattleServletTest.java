package com.game.servlet;

import com.alibaba.fastjson.JSONObject;
import com.game.entity.Player;
import com.game.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BattleServletTest {

    private static final int PLAYER_ID = 7;
    private static final Map<String, String> ORIGINAL_DB_PROPERTIES =
            new HashMap<>();

    @BeforeAll
    static void configureDbUtilWithoutOpeningConnections() {
        setSystemProperty("db.username", "battle-servlet-test");
        setSystemProperty("db.password", "unused-test-value");
        setSystemProperty("db.pool.initialSize", "0");
        setSystemProperty("db.pool.minIdle", "0");
    }

    @AfterAll
    static void restoreSystemProperties() {
        ORIGINAL_DB_PROPERTIES.forEach((name, value) -> {
            if (value == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, value);
            }
        });
    }

    @Test
    void rejectsUnauthenticatedRequestBeforeBusinessLogic() throws Exception {
        Harness harness = authenticatedHarness();
        when(harness.request.getSession(false)).thenReturn(null);

        JSONObject json = invokeWithoutBusinessCall(harness);

        assertJson(json, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
        verify(harness.response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsMissingOrBlankRequestIdBeforeBusinessLogic(String requestId)
            throws Exception {
        Harness harness = authenticatedHarness();
        when(harness.request.getParameter("requestId")).thenReturn(requestId);

        JSONObject json = invokeWithoutBusinessCall(harness);

        assertJson(json, HttpServletResponse.SC_BAD_REQUEST, "参数错误");
    }

    @Test
    void rejectsOverlongRequestIdBeforeBusinessLogic() throws Exception {
        Harness harness = authenticatedHarness();
        when(harness.request.getParameter("requestId")).thenReturn("x".repeat(37));

        JSONObject json = invokeWithoutBusinessCall(harness);

        assertJson(json, HttpServletResponse.SC_BAD_REQUEST, "参数格式错误");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsMissingOrBlankPlayerIdBeforeBusinessLogic(String playerId)
            throws Exception {
        Harness harness = authenticatedHarness();
        when(harness.request.getParameter("playerId")).thenReturn(playerId);

        JSONObject json = invokeWithoutBusinessCall(harness);

        assertJson(json, HttpServletResponse.SC_BAD_REQUEST, "参数错误");
    }

    @Test
    void rejectsMalformedPlayerIdBeforeBusinessLogic() throws Exception {
        Harness harness = authenticatedHarness();
        when(harness.request.getParameter("playerId")).thenReturn("not-a-number");

        JSONObject json = invokeWithoutBusinessCall(harness);

        assertJson(json, HttpServletResponse.SC_BAD_REQUEST, "参数格式错误");
    }

    @Test
    void rejectsPlayerIdThatDoesNotMatchSessionIdentity() throws Exception {
        Harness harness = authenticatedHarness();
        when(harness.request.getParameter("playerId"))
                .thenReturn(String.valueOf(PLAYER_ID + 1));

        JSONObject json = invokeWithoutBusinessCall(harness);

        assertJson(json, HttpServletResponse.SC_FORBIDDEN,
                "无权提交其他玩家的战斗结果");
        verify(harness.response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsMissingOrBlankMonsterNameBeforeBusinessLogic(String monsterName)
            throws Exception {
        Harness harness = authenticatedHarness();
        when(harness.request.getParameter("monsterName")).thenReturn(monsterName);

        JSONObject json = invokeWithoutBusinessCall(harness);

        assertJson(json, HttpServletResponse.SC_BAD_REQUEST, "参数错误");
    }

    @Test
    void rejectsUnknownMonsterBeforeBusinessLogic() throws Exception {
        Harness harness = authenticatedHarness();
        when(harness.request.getParameter("monsterName")).thenReturn("不存在的怪物");

        JSONObject json = invokeWithoutBusinessCall(harness);

        assertJson(json, HttpServletResponse.SC_BAD_REQUEST, "未知怪物");
    }

    @Test
    void validRequestCrossesServletBoundaryIntoBusinessLogic() throws Exception {
        Harness harness = authenticatedHarness();
        Logger servletLogger = Logger.getLogger(BattleServlet.class.getName());
        Level originalLevel = servletLogger.getLevel();
        servletLogger.setLevel(Level.OFF);

        try {
            try (MockedStatic<DBUtil> dbUtil = mockStatic(DBUtil.class)) {
                dbUtil.when(DBUtil::getConnection)
                        .thenThrow(new SQLException("controlled business boundary"));

                JSONObject json = invoke(harness);

                dbUtil.verify(DBUtil::getConnection, times(1));
                assertJson(json, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "保存失败");
            }
        } finally {
            servletLogger.setLevel(originalLevel);
        }

        verify(harness.request).setCharacterEncoding("UTF-8");
        verify(harness.response)
                .setContentType("application/json;charset=utf-8");
    }

    private JSONObject invokeWithoutBusinessCall(Harness harness)
            throws Exception {
        try (MockedStatic<DBUtil> dbUtil = mockStatic(DBUtil.class)) {
            JSONObject json = invoke(harness);
            dbUtil.verify(DBUtil::getConnection, never());
            return json;
        }
    }

    private JSONObject invoke(Harness harness)
            throws ServletException, IOException {
        new TestableBattleServlet().post(harness.request, harness.response);
        harness.writer.flush();
        return JSONObject.parseObject(harness.responseBody.toString());
    }

    private Harness authenticatedHarness() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        StringWriter responseBody = new StringWriter();
        PrintWriter writer = new PrintWriter(responseBody);

        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setUsername("battle_api_user");

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("player")).thenReturn(player);
        when(request.getParameter("requestId"))
                .thenReturn(UUID.randomUUID().toString());
        when(request.getParameter("playerId"))
                .thenReturn(String.valueOf(PLAYER_ID));
        when(request.getParameter("monsterName")).thenReturn(legalMonsterName());
        when(response.getWriter()).thenReturn(writer);

        return new Harness(request, response, responseBody, writer);
    }

    @SuppressWarnings("unchecked")
    private String legalMonsterName() throws Exception {
        Field rewardsField = BattleServlet.class.getDeclaredField("MONSTER_REWARDS");
        rewardsField.setAccessible(true);
        Map<String, ?> rewards = (Map<String, ?>) rewardsField.get(null);
        assertTrue(!rewards.isEmpty());
        return rewards.keySet().iterator().next();
    }

    private static void assertJson(JSONObject json, int expectedCode,
                                   String expectedMessage) {
        assertEquals(expectedCode, json.getIntValue("code"));
        assertEquals(expectedMessage, json.getString("msg"));
        assertEquals(2, json.size());
    }

    private static void setSystemProperty(String name, String value) {
        ORIGINAL_DB_PROPERTIES.put(name, System.getProperty(name));
        System.setProperty(name, value);
    }

    private static final class TestableBattleServlet extends BattleServlet {
        private void post(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            doPost(request, response);
        }
    }

    private record Harness(HttpServletRequest request,
                           HttpServletResponse response,
                           StringWriter responseBody,
                           PrintWriter writer) {
    }
}
