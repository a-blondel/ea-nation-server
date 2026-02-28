package eu.eahub.enums.nfs;

import lombok.Getter;

/**
 * Tracks for Need For Speed: Carbon - Own the City (PSP).
 */
@Getter
public enum CarbonTrack {
    // CIRCUIT tracks (1-20)
    PERIMETER(1, "CIRCUIT", "PERIMETER"),
    REACTOR_LOOP(2, "CIRCUIT", "REACTOR LOOP"),
    JUNKTOWN_SCRAMBLE(3, "CIRCUIT", "JUNKTOWN SCRAMBLE"),
    STORAGE_RUN(4, "CIRCUIT", "STORAGE RUN"),
    EAST_TUNNEL(5, "CIRCUIT", "EAST TUNNEL"),
    INNER_CITY_RUN(6, "CIRCUIT", "INNER CITY RUN"),
    URBAN_TECHNICAL(7, "CIRCUIT", "URBAN TECHNICAL"),
    CROSSOVER(8, "CIRCUIT", "CROSSOVER"),
    FACTORY_CIRCUIT(9, "CIRCUIT", "FACTORY CIRCUIT"),
    JUNKYARD_BLITZ(10, "CIRCUIT", "JUNKYARD BLITZ"),
    CENTRIFUGAL(11, "CIRCUIT", "CENTRIFUGAL"),
    BRIDGE_CITY(12, "CIRCUIT", "BRIDGE CITY"),
    SCRYSCRAPER_CIRCUIT(13, "CIRCUIT", "SCRYSCRAPER CIRCUIT"),
    TWO_BRIDGE_CIRCUIT(14, "CIRCUIT", "TWO BRIDGE CIRCUIT"),
    HOMES_AND_TOWERS(15, "CIRCUIT", "HOMES AND TOWERS"),
    SOUTH_SIDE(16, "CIRCUIT", "SOUTH SIDE"),
    FIGURE_EIGHT(17, "CIRCUIT", "FIGURE EIGHT"),
    WESTSIDE_LOOP(18, "CIRCUIT", "WESTSIDE LOOP"),
    UNIVERSITY_DRIVE(19, "CIRCUIT", "UNIVERSITY DRIVE"),
    GIANT_LOOP(20, "CIRCUIT", "GIANT LOOP"),

    // SPRINT tracks (21-30)
    LONG_POINT(21, "SPRINT", "LONG POINT"),
    MOUNTAIN_SPEEDZONE(22, "SPRINT", "MOUNTAIN SPEEDZONE"),
    WESTSIDE_SPRINT(23, "SPRINT", "WESTSIDE SPRINT"),
    DOUBLE_SWITCH(24, "SPRINT", "DOUBLE SWITCH"),
    CROSS_TOWN_SPRINT(25, "SPRINT", "CROSS TOWN SPRINT"),
    SHIPYARD_SPRINT(26, "SPRINT", "SHIPYARD SPRINT"),
    NORTH_BRIDGE_SPRINT(27, "SPRINT", "NORTH BRIDGE SPRINT"),
    DOWNTOWN_SPRINT(28, "SPRINT", "DOWNTOWN SPRINT"),
    MOUNTAINS_TO_SHIPYARD(29, "SPRINT", "MOUNTAINS TO SHIPYARD"),
    HIGH_TO_LOW(30, "SPRINT", "HIGH TO LOW");

    private final int venueId;
    private final String trackType;
    private final String trackName;

    CarbonTrack(int venueId, String trackType, String trackName) {
        this.venueId = venueId;
        this.trackType = trackType;
        this.trackName = trackName;
    }

    public static CarbonTrack fromVenueId(int venueId) {
        for (CarbonTrack track : values()) {
            if (track.venueId == venueId) {
                return track;
            }
        }
        return null;
    }

    public static String getTrackName(Integer venueId) {
        if (venueId == null) return "UNKNOWN";
        CarbonTrack track = fromVenueId(venueId);
        return track != null ? track.getDisplayName() : "UNKNOWN";
    }

    public String getDisplayName() {
        return trackType + " - " + trackName;
    }
}
