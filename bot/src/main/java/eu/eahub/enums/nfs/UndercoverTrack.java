package eu.eahub.enums.nfs;

import lombok.Getter;

/**
 * Tracks for Need For Speed: Undercover (PSP).
 */
@Getter
public enum UndercoverTrack {
    // CIRCUIT tracks (1-22)
    SWAMP(1, "CIRCUIT", "SWAMP"),
    EDGEWAY(2, "CIRCUIT", "EDGEWAY"),
    THROUGH_TOWN(3, "CIRCUIT", "THROUGH TOWN"),
    EASTSIDE(4, "CIRCUIT", "EASTSIDE"),
    ZIPPER(5, "CIRCUIT", "ZIPPER"),
    DOWNTOWN_LOOP(6, "CIRCUIT", "DOWNTOWN LOOP"),
    CLASSIC(7, "CIRCUIT", "CLASSIC"),
    WINDMILL(8, "CIRCUIT", "WINDMILL"),
    BOARDWALK(9, "CIRCUIT", "BOARDWALK"),
    MIDTOWN(10, "CIRCUIT", "MIDTOWN"),
    INDUSTRIAL(11, "CIRCUIT", "INDUSTRIAL"),
    GIANT_LOOP(12, "CIRCUIT", "GIANT LOOP"),
    NORTH_LOOP(13, "CIRCUIT", "NORTH LOOP"),
    SOUTH_LOOP(14, "CIRCUIT", "SOUTH LOOP"),
    COAST(15, "CIRCUIT", "COAST"),
    FIGURE_EIGHT(16, "CIRCUIT", "FIGURE EIGHT"),
    INTERIOR(17, "CIRCUIT", "INTERIOR"),
    HEAT(18, "CIRCUIT", "HEAT"),
    HIGHRISE(19, "CIRCUIT", "HIGHRISE"),
    HYBRID(20, "CIRCUIT", "HYBRID"),
    GEAR_HEAD(21, "CIRCUIT", "GEAR HEAD"),
    TUNNEL(22, "CIRCUIT", "TUNNEL"),

    // SPRINT tracks (23-36)
    PERIMETER(23, "SPRINT", "PERIMETER"),
    SWITCHBACK(24, "SPRINT", "SWITCHBACK"),
    HORSESHOE(25, "SPRINT", "HORSESHOE"),
    SNAKE(26, "SPRINT", "SNAKE"),
    CRESCENT(27, "SPRINT", "CRESCENT"),
    RESIDENTIAL(28, "SPRINT", "RESIDENTIAL"),
    MILITARY(29, "SPRINT", "MILITARY"),
    TWISTER(30, "SPRINT", "TWISTER"),
    FAST_TRACK(31, "SPRINT", "FAST TRACK"),
    SHIPYARD(32, "SPRINT", "SHIPYARD"),
    SPIRAL(33, "SPRINT", "SPIRAL"),
    NOOSE(34, "SPRINT", "NOOSE"),
    OUTSIDE(35, "SPRINT", "OUTSIDE"),
    DIPPER(36, "SPRINT", "DIPPER"),

    // GATEWAY tracks (37-39)
    SUNSET_KILLS(37, "GATEWAY", "SUNSET KILLS"),
    PORT_CRESCENT(38, "GATEWAY", "PORT CRESCENT"),
    PALM_HARBOR(39, "GATEWAY", "PALM HARBOR");

    private final int venueId;
    private final String trackType;
    private final String trackName;

    UndercoverTrack(int venueId, String trackType, String trackName) {
        this.venueId = venueId;
        this.trackType = trackType;
        this.trackName = trackName;
    }

    public static UndercoverTrack fromVenueId(int venueId) {
        for (UndercoverTrack track : values()) {
            if (track.venueId == venueId) {
                return track;
            }
        }
        return null;
    }

    public static String getTrackName(Integer venueId) {
        if (venueId == null) return "UNKNOWN";
        UndercoverTrack track = fromVenueId(venueId);
        return track != null ? track.getDisplayName() : "UNKNOWN";
    }

    public String getDisplayName() {
        return trackType + " - " + trackName;
    }
}
