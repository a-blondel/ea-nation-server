package eu.eahub.enums.nfs;

import lombok.Getter;

/**
 * Tracks for Need For Speed: Most Wanted 5-1-0 (PSP).
 * Most Wanted uses separate venue IDs for forward and reverse directions.
 */
@Getter
public enum MostWantedTrack {
    CITY_POWER_FORWARD(0, "CITY POWER"),
    CITY_POWER_REVERSE(1, "CITY POWER"),
    GREAT_NORTHERN_WAY_REVERSE(2, "GREAT NORTHERN WAY"),
    GREAT_NORTHERN_WAY_FORWARD(3, "GREAT NORTHERN WAY"),
    DOWNTOWN_EXPRESSWAY_REVERSE(4, "DOWNTOWN EXPRESSWAY"),
    DOWNTOWN_EXPRESSWAY_FORWARD(5, "DOWNTOWN EXPRESSWAY"),
    EXCHANGE_DISTRICT_REVERSE(6, "EXCHANGE DISTRICT"),
    EXCHANGE_DISTRICT_FORWARD(7, "EXCHANGE DISTRICT"),
    SOUTH_CENTRAL_REVERSE(8, "SOUTH CENTRAL"),
    SOUTH_CENTRAL_FORWARD(9, "SOUTH CENTRAL"),
    MAIN_AND_TERMINAL_REVERSE(10, "MAIN & TERMINAL"),
    MAIN_AND_TERMINAL_FORWARD(11, "MAIN & TERMINAL"),
    WEST_VILLAGE_REVERSE(12, "WEST VILLAGE"),
    WEST_VILLAGE_FORWARD(13, "WEST VILLAGE"),
    RIVERVIEW_REVERSE(14, "RIVERVIEW"),
    RIVERVIEW_FORWARD(15, "RIVERVIEW"),
    PRECINCT_5TH_REVERSE(16, "5TH PRECINCT"),
    PRECINCT_5TH_FORWARD(17, "5TH PRECINCT"),
    HILLSIDE_REVERSE(18, "HILLSIDE"),
    HILLSIDE_FORWARD(19, "HILLSIDE");

    private final int venueId;
    private final String trackName;

    MostWantedTrack(int venueId, String trackName) {
        this.venueId = venueId;
        this.trackName = trackName;
    }

    public static MostWantedTrack fromVenueId(int venueId) {
        for (MostWantedTrack track : values()) {
            if (track.venueId == venueId) {
                return track;
            }
        }
        return null;
    }

    public static String getTrackName(Integer venueId) {
        if (venueId == null) return "UNKNOWN";
        MostWantedTrack track = fromVenueId(venueId);
        return track != null ? track.trackName : "UNKNOWN";
    }
}
