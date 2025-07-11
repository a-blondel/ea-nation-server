package com.ea.enums;

public enum MohhMode {

    DOMINATION("1"), // Capture and hold
    INFILTRATION("2"), // Capture The Flag
    DEMOLITION("3"),
    HOLD_THE_LINE("4"), // King Of the Hill
    BATTLELINES("5"),
    TEAM_DEATHMATCH("7"),
    DEATHMATCH("8");

    public final String id;

    MohhMode(String id) {
        this.id = id;
    }

}
