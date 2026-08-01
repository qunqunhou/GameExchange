package com.game.entity;

import java.sql.Timestamp;

public class BattleRecord {
    private Long id;
    private Integer playerId;
    private String requestId;
    private String monsterName;
    private Long goldReward;
    private Integer lootItemId;
    private Timestamp createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Integer playerId) {
        this.playerId = playerId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getMonsterName() {
        return monsterName;
    }

    public void setMonsterName(String monsterName) {
        this.monsterName = monsterName;
    }

    public Long getGoldReward() {
        return goldReward;
    }

    public void setGoldReward(Long goldReward) {
        this.goldReward = goldReward;
    }

    public Integer getLootItemId() {
        return lootItemId;
    }

    public void setLootItemId(Integer lootItemId) {
        this.lootItemId = lootItemId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
