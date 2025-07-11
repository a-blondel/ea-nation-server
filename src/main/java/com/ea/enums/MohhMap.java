package com.ea.enums;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ea.enums.MohhMode.*;

public enum MohhMap {

    ITALY_CITY_DOM("MAP1", "115", "73", DOMINATION.id, "Italy City: Domination"),
    ITALY_MT_VILLAGE_INF("MAP2", "124", "7c", INFILTRATION.id, "Mnt. Village Day: Infiltration"),
    ITALY_BEACH_DEM("MAP3", "131", "83", DEMOLITION.id, "The Beachhead: Demolition"),
    ITALY_AIRFIELD_DEM("MAP4", "132", "84", DEMOLITION.id, "The Airfield: Demolition"),
    ITALY_MT_VILLAGE_NIGHT_BL("MAP5", "153", "99", BATTLELINES.id, "Mnt. Village Night: Battlelines"),
    ITALY_BEACH_DM("MAP6", "181", "b5", DEATHMATCH.id, "The Beachhead: Deathmatch"),
    ITALY_AIRFIELD_DM("MAP7", "182", "b6", DEATHMATCH.id, "The Airfield: Deathmatch"),
    ITALY_MT_VILLAGE_NIGHT_DM("MAP8", "183", "b7", DEATHMATCH.id, "Mnt. Village Night: Deathmatch"),
    ITALY_MT_VILLAGE_DM("MAP9", "184", "b8", DEATHMATCH.id, "Mnt. Village Day: Deathmatch"),
    ITALY_CITY_DM("MAP10", "185", "b9", DEATHMATCH.id, "Italy City: Deathmatch"),
    HOLLAND_CITY_DOM("MAP11", "211", "d3", DOMINATION.id, "Netherlands City: Domination"),
    HOLLAND_BRIDGE_NIGHT_DOM("MAP12", "216", "d8", DOMINATION.id, "Holland Bridge Dusk: Domination"),
    HOLLAND_STREET_INF("MAP13", "222", "de", INFILTRATION.id, "Netherlands Street: Infiltration"),
    HOLLAND_BRIDGE_HTL("MAP14", "245", "f5", HOLD_THE_LINE.id, "Holland Bridge Rain: Hold the Line"),
    HOLLAND_CITY_DM("MAP15", "281", "119", DEATHMATCH.id, "Netherlands City: Deathmatch"),
    HOLLAND_STREET_DM("MAP16", "282", "11a", DEATHMATCH.id, "Netherlands Street: Deathmatch"),
    HOLLAND_CHURCH_DM("MAP17", "283", "11b", DEATHMATCH.id, "Netherlands Church: Deathmatch"),
    HOLLAND_BRIDGE_DM("MAP18", "285", "11d", DEATHMATCH.id, "Holland Bridge Rain: Deathmatch"),
    HOLLAND_BRIDGE_NIGHT_DM("MAP19", "286", "11e", DEATHMATCH.id, "Holland Bridge Dusk: Deathmatch"),
    BELGIUM_FOREST_INF("MAP20", "324", "144", INFILTRATION.id, "Belgium Forest: Infiltration"),
    BELGIUM_CASTLE_DEM("MAP21", "336", "150", DEMOLITION.id, "Belgium Castle: Demolition"),
    BELGIUM_RIVER_NIGHT_HTL("MAP22", "341", "155", HOLD_THE_LINE.id, "Belgium River Night: Hold the Line"),
    BELGIUM_RANCH_BL("MAP23", "352", "160", BATTLELINES.id, "Belgium Ranch: Battlelines"),
    BELGIUM_RIVER_NIGHT_DM("MAP24", "381", "17d", DEATHMATCH.id, "Belgium River Night: Deathmatch"),
    BELGIUM_RANCH_DM("MAP25", "382", "17e", DEATHMATCH.id, "Belgium Ranch: Deathmatch"),
    BELGIUM_RIVER_DM("MAP26", "383", "17f", DEATHMATCH.id, "Belgium River Day: Deathmatch"),
    BELGIUM_FOREST_DM("MAP27", "384", "180", DEATHMATCH.id, "Belgium Forest: Deathmatch"),
    BELGIUM_CASTLE_DM("MAP28", "386", "182", DEATHMATCH.id, "Belgium Castle: Deathmatch");

    public final String code;
    public final String decimalId;
    public final String hexId;
    public final String modeId;
    public final String name;

    MohhMap(String code, String decimalId, String hexId, String modeId, String name) {
        this.code = code;
        this.decimalId = decimalId;
        this.hexId = hexId;
        this.modeId = modeId;
        this.name = name;
    }

    public static String getMapNameByCode(int code) {
        for (MohhMap map : MohhMap.values()) {
            int mapCode = Integer.parseInt(map.code.replaceAll("MAP", ""));
            if (mapCode == code) {
                return map.name;
            }
        }
        return "";
    }

    public static String getMapNameByHexId(String hexId) {
        for (MohhMap map : MohhMap.values()) {
            if (map.hexId.equals(hexId)) {
                return map.name;
            }
        }
        return null;
    }

    private static final Map<String, List<String>> mapStatId = new HashMap<>();

    static {
        mapStatId.put("1", List.of("153", "183")); // IT Mnt. Village Night (Village B)
        mapStatId.put("2", List.of("131", "181")); // IT Beach
        mapStatId.put("3", List.of("132", "182")); // IT Airfield
        mapStatId.put("4", List.of("124", "184")); // IT Mnt. Village Day (Village A)
        mapStatId.put("9", List.of("115", "185")); // IT City
        mapStatId.put("10", List.of("211", "281")); // NL City
        mapStatId.put("11", List.of("216", "286")); // NL Holland Bridge Dusk (Bridge B)
        mapStatId.put("12", List.of("222", "282")); // NL Street
        mapStatId.put("13", List.of("245", "285")); // NL Bridge (Bridge A)
        mapStatId.put("16", List.of("283")); // NL Church
        mapStatId.put("19", List.of("324", "384")); // BE Forest
        mapStatId.put("20", List.of("336", "386")); // BE Castle
        mapStatId.put("21", List.of("383")); // BE River (River A)
        mapStatId.put("22", List.of("352", "382")); // BE Ranch
        mapStatId.put("25", List.of("341", "381")); // BE Belgium River Night (River B)
    }

    public static String getMapStatId(String key) {
        for (Map.Entry<String, List<String>> entry : mapStatId.entrySet()) {
            if (entry.getValue().contains(key)) {
                return entry.getKey();
            }
        }
        return "1";
    }

}
