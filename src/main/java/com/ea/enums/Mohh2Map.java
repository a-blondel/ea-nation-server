package com.ea.enums;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ea.enums.MohhMode.*;

public enum Mohh2Map {

    PORT_DM("MAP1", "101", DEATHMATCH.id),
    PORT_TDM("MAP2", "101", TEAM_DEATHMATCH.id),
    CITY_DM("MAP3", "201", DEATHMATCH.id),
    CITY_TDM("MAP4", "201", TEAM_DEATHMATCH.id),
    SEWERS_DM("MAP5", "301", DEATHMATCH.id),
    SEWERS_TDM("MAP6", "301", TEAM_DEATHMATCH.id),
    SEWERS_INF("MAP7", "301", INFILTRATION.id),
    VILLAGE_DM("MAP8", "401", DEATHMATCH.id),
    VILLAGE_TDM("MAP9", "401", TEAM_DEATHMATCH.id),
    VILLAGE_INF("MAP10", "401", INFILTRATION.id),
    MONASTERY_DM("MAP11", "501", DEATHMATCH.id),
    MONASTERY_TDM("MAP12", "501", TEAM_DEATHMATCH.id),
    BASE_DM("MAP13", "601", DEATHMATCH.id),
    BASE_TDM("MAP14", "601", TEAM_DEATHMATCH.id);

    public final String code;
    public final String decimalId;
    public final String modeId;
    private static final Map<String, List<String>> mapStatId = new HashMap<>();

    static {
        mapStatId.put("0", List.of("101")); // Port
        mapStatId.put("2", List.of("201")); // City
        mapStatId.put("4", List.of("301")); // Sewers
        mapStatId.put("7", List.of("401")); // Village
        mapStatId.put("10", List.of("501")); // Monastery
        mapStatId.put("12", List.of("601")); // Base
    }

    Mohh2Map(String code, String decimalId, String modeId) {
        this.code = code;
        this.decimalId = decimalId;
        this.modeId = modeId;
    }

    public static String getMapStatId(String key) {
        for (Map.Entry<String, List<String>> entry : mapStatId.entrySet()) {
            if (entry.getValue().contains(key)) {
                return entry.getKey();
            }
        }
        return "0";
    }
}