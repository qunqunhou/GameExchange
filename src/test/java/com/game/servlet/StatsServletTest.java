package com.game.servlet;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsServletTest {

    private static final String LEASE_SQL = "SELECT COUNT(*) FROM player "
            + "WHERE last_seen_at >= CURRENT_TIMESTAMP - INTERVAL 60 SECOND";

    @Test
    void getLeaseOnlineCountUsesApprovedQuery() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(LEASE_SQL)).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(1)).thenReturn(4);

        int count = (int) invoke("getLeaseOnlineCount",
                new Class<?>[]{Connection.class}, conn);

        assertEquals(4, count);
        verify(conn).prepareStatement(LEASE_SQL);
        verify(rs).close();
        verify(ps).close();
    }

    private Object invoke(String methodName, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = StatsServlet.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(new StatsServlet(), args);
    }

}
