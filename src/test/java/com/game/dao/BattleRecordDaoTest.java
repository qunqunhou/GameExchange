package com.game.dao;

import com.game.entity.BattleRecord;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BattleRecordDaoTest {

    private static final String SELECT_SQL = "SELECT id, player_id, request_id, monster_name, "
            + "gold_reward, loot_item_id, created_at "
            + "FROM battle_record WHERE player_id = ? AND request_id = ?";
    private static final String INSERT_SQL = "INSERT INTO battle_record("
            + "player_id, request_id, monster_name, gold_reward, loot_item_id) "
            + "VALUES(?, ?, ?, ?, ?)";

    private final BattleRecordDao battleRecordDao = new BattleRecordDao();

    @Test
    void findByPlayerAndRequestIdMapsExistingRecordAndClosesResources() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        Timestamp createdAt = Timestamp.valueOf("2026-07-31 12:34:56.123");

        when(conn.prepareStatement(SELECT_SQL)).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("id")).thenReturn(101L);
        when(rs.getInt("player_id")).thenReturn(7);
        when(rs.getString("request_id")).thenReturn("11111111-2222-4333-8444-555555555555");
        when(rs.getString("monster_name")).thenReturn("哥布林");
        when(rs.getLong("gold_reward")).thenReturn(88L);
        when(rs.getInt("loot_item_id")).thenReturn(19);
        when(rs.wasNull()).thenReturn(false);
        when(rs.getTimestamp("created_at")).thenReturn(createdAt);

        BattleRecord result = battleRecordDao.findByPlayerAndRequestId(
                conn, 7, "11111111-2222-4333-8444-555555555555");

        assertAll(
                () -> assertEquals(Long.valueOf(101L), result.getId()),
                () -> assertEquals(Integer.valueOf(7), result.getPlayerId()),
                () -> assertEquals("11111111-2222-4333-8444-555555555555",
                        result.getRequestId()),
                () -> assertEquals("哥布林", result.getMonsterName()),
                () -> assertEquals(Long.valueOf(88L), result.getGoldReward()),
                () -> assertEquals(Integer.valueOf(19), result.getLootItemId()),
                () -> assertEquals(createdAt, result.getCreatedAt())
        );
        verify(ps).setInt(1, 7);
        verify(ps).setString(2, "11111111-2222-4333-8444-555555555555");
        verify(rs).close();
        verify(ps).close();
        verifyExternalConnectionNotOwned(conn);
    }

    @Test
    void findByPlayerAndRequestIdReturnsNullWhenRecordDoesNotExist() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(SELECT_SQL)).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        BattleRecord result = battleRecordDao.findByPlayerAndRequestId(
                conn, 7, "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

        assertNull(result);
        verify(rs).close();
        verify(ps).close();
        verifyExternalConnectionNotOwned(conn);
    }

    @Test
    void findByPlayerAndRequestIdMapsSqlNullLootItemIdToJavaNull() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(SELECT_SQL)).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt("loot_item_id")).thenReturn(0);
        when(rs.wasNull()).thenReturn(true);

        BattleRecord result = battleRecordDao.findByPlayerAndRequestId(
                conn, 8, "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff");

        assertNull(result.getLootItemId());
        verify(rs).wasNull();
        verify(rs).close();
        verify(ps).close();
        verifyExternalConnectionNotOwned(conn);
    }

    @Test
    void insertBindsParametersReturnsRowCountAndAssignsGeneratedId() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet generatedKeys = mock(ResultSet.class);
        BattleRecord record = createRecord(21);

        when(conn.prepareStatement(INSERT_SQL, PreparedStatement.RETURN_GENERATED_KEYS))
                .thenReturn(ps);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(generatedKeys);
        when(generatedKeys.next()).thenReturn(true);
        when(generatedKeys.getLong(1)).thenReturn(301L);

        int rows = battleRecordDao.insert(conn, record);

        assertAll(
                () -> assertEquals(1, rows),
                () -> assertEquals(Long.valueOf(301L), record.getId())
        );
        verify(ps).setInt(1, 9);
        verify(ps).setString(2, "cccccccc-dddd-4eee-8fff-000000000000");
        verify(ps).setString(3, "骷髅卫士");
        verify(ps).setLong(4, 120L);
        verify(ps).setInt(5, 21);
        verify(generatedKeys).close();
        verify(ps).close();
        verifyExternalConnectionNotOwned(conn);
    }

    @Test
    void insertWithoutLootBindsSqlNullAndAssignsGeneratedId() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet generatedKeys = mock(ResultSet.class);
        BattleRecord record = createRecord(null);

        when(conn.prepareStatement(INSERT_SQL, PreparedStatement.RETURN_GENERATED_KEYS))
                .thenReturn(ps);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(generatedKeys);
        when(generatedKeys.next()).thenReturn(true);
        when(generatedKeys.getLong(1)).thenReturn(302L);

        int rows = battleRecordDao.insert(conn, record);

        assertAll(
                () -> assertEquals(1, rows),
                () -> assertEquals(Long.valueOf(302L), record.getId())
        );
        verify(ps).setNull(5, Types.INTEGER);
        verify(ps, never()).setInt(eq(5), anyInt());
        verify(generatedKeys).close();
        verify(ps).close();
        verifyExternalConnectionNotOwned(conn);
    }

    private BattleRecord createRecord(Integer lootItemId) {
        BattleRecord record = new BattleRecord();
        record.setPlayerId(9);
        record.setRequestId("cccccccc-dddd-4eee-8fff-000000000000");
        record.setMonsterName("骷髅卫士");
        record.setGoldReward(120L);
        record.setLootItemId(lootItemId);
        return record;
    }

    private void verifyExternalConnectionNotOwned(Connection conn) throws Exception {
        verify(conn, never()).commit();
        verify(conn, never()).rollback();
        verify(conn, never()).close();
    }
}
