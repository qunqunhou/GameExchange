package com.game.entity;

public class Player {
    private Integer id;
    private String username;
    private String password;
    private Long gold;

    public Player(){}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getGold() {
        return gold;
    }

    public void setGold(Long gold) {
        this.gold = gold;
    }

    @Override
    public String toString() {
        return "Player{id=" + id
                + ", username='" + username + "'"
                + ", gold=" + gold
                + "}";
    }
}
