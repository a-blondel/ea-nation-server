package eu.eahub.enums.nfs;

import lombok.Getter;

/**
 * Tracks for Need For Speed: ProStreet (PSP).
 */
@Getter
public enum ProStreetTrack {
    INFINEON_INNER(1, "INFINEON RACEWAY", "INNER CIRCUIT"),
    INFINEON_OUTER(2, "INFINEON RACEWAY", "OUTER CIRCUIT"),
    INFINEON_SHORT(3, "INFINEON RACEWAY", "SHORT CIRCUIT"),
    AUTOBAHN_GRIP_LARGE(4, "AUTOBAHN GRIP", "GRIP LARGE"),
    AUTOBAHN_GRIP_MEDIUM(5, "AUTOBAHN GRIP", "GRIP MEDIUM"),
    AUTOBAHN_GRIP_SMALL(6, "AUTOBAHN GRIP", "GRIP SMALL"),
    AUTOBAHN_CIRCUIT(7, "AUTOBAHN", "CIRCUIT"),
    WILLOWSPRINGS_SHORT1(8, "WILLOWSPRINGS RACEWAY", "THE STREETS - SHORT 1"),
    WILLOWSPRINGS_SHORT2(9, "WILLOWSPRINGS RACEWAY", "THE STREETS - SHORT 2"),
    WILLOWSPRINGS_SHORT3(10, "WILLOWSPRINGS RACEWAY", "THE STREETS - SHORT 3"),
    WILLOWSPRINGS_HORSE_THIEF1(11, "WILLOWSPRINGS RACEWAY", "HORSE THIEF MILE 1"),
    WILLOWSPRINGS_HORSE_THIEF2(12, "WILLOWSPRINGS RACEWAY", "HORSE THIEF MILE 2"),
    WILLOWSPRINGS_GP(13, "WILLOWSPRINGS RACEWAY", "GRAND PRIX CIRCUIT"),
    AUTOPOLIS_SHORT(14, "AUTOPOLIS", "SHORT CIRCUIT"),
    AUTOPOLIS_GP(15, "AUTOPOLIS", "GRAND PRIX CIRCUIT"),
    AIRFIELD_1(16, "AIRFIELD", "RACE 1"),
    AIRFIELD_2(17, "AIRFIELD", "RACE 2"),
    AIRFIELD_3(18, "AIRFIELD", "RACE 3"),
    AIRFIELD_4(19, "AIRFIELD", "RACE 4"),
    AIRFIELD_5(20, "AIRFIELD", "RACE 5"),
    TOKYO_1(21, "TOKYO HIGHWAY", "RACE 1"),
    TOKYO_2(22, "TOKYO HIGHWAY", "RACE 2"),
    MONDELLO_GP(23, "MONDELLO PARK", "GP CIRCUIT"),
    MONDELLO_SHORT(24, "MONDELLO PARK", "SHORT CIRCUIT"),
    MONDELLO_CLUB(25, "MONDELLO PARK", "CLUB CIRCUIT"),
    PORSCHE_CLUB(26, "PORSCHE TEST TRACK", "CLUB CIRCUIT"),
    PORSCHE_HIGH_SPEED(27, "PORSCHE TEST TRACK", "HIGH SPEED TRACK"),
    PORSCHE_LONG(28, "PORSCHE TEST TRACK", "LONG CIRCUIT"),
    PORTLAND(29, "PORTLAND INT. RACEWAY", "PORTLAND CIRCUIT"),
    TEXAS_OVAL(30, "TEXAS WORLD SPEEDWAY", "OVAL"),
    TEXAS_CLUB(31, "TEXAS WORLD SPEEDWAY", "CLUB CIRCUIT"),
    TEXAS_SHORT1(32, "TEXAS WORLD SPEEDWAY", "SHORT CIRCUIT 1"),
    TEXAS_SHORT2(33, "TEXAS WORLD SPEEDWAY", "SHORT CIRCUIT 2"),
    TEXAS_GP1(34, "TEXAS WORLD SPEEDWAY", "GP CIRCUIT 1"),
    TEXAS_GP2(35, "TEXAS WORLD SPEEDWAY", "GP CIRCUIT 2"),
    TEXAS_GP3(36, "TEXAS WORLD SPEEDWAY", "GP CIRCUIT 3"),
    BEACH_1(37, "BEACH FRONT", "RACE 1"),
    BEACH_2(38, "BEACH FRONT", "RACE 2");

    private final int venueId;
    private final String trackType;
    private final String trackName;

    ProStreetTrack(int venueId, String trackType, String trackName) {
        this.venueId = venueId;
        this.trackType = trackType;
        this.trackName = trackName;
    }

    public static ProStreetTrack fromVenueId(int venueId) {
        for (ProStreetTrack track : values()) {
            if (track.venueId == venueId) {
                return track;
            }
        }
        return null;
    }

    public static String getTrackName(Integer venueId) {
        if (venueId == null) return "UNKNOWN";
        ProStreetTrack track = fromVenueId(venueId);
        return track != null ? track.getDisplayName() : "UNKNOWN";
    }

    public String getDisplayName() {
        return trackType + " - " + trackName;
    }
}
