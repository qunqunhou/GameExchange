package com.game.simulator;

public class GameEvent {

    // 事件类型枚举
    public enum EventType {
        KILL_MONSTER,  // 击杀怪物
        ITEM_DROP,     // 装备掉落
        PLAYER_ONLINE, // 玩家上线
        PLAYER_OFFLINE // 玩家下线
    }

    private EventType type;
    private Integer playerId;
    private String description;
    private Long goldReward;
    private String itemName;
    private String rarity;

    public GameEvent(EventType type, Integer playerId, String description) {
        this.type = type;
        this.playerId = playerId;
        this.description = description;
    }

    public EventType getType() { return type; }
    public Integer getPlayerId() { return playerId; }
    public String getDescription() { return description; }
    public Long getGoldReward() { return goldReward; }
    public void setGoldReward(Long goldReward) { this.goldReward = goldReward; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
}