package eu.eahub.enums;

public enum LeaderboardLabel {
    // Categories
    MY_LEADERBOARD("MY LEADERBOARD"),
    MY_COMMUNITY_LEADERBOARD("MY COMMUNITY LEADERBOARD"),
    TOP_100("TOP 100"),
    COMMUNITY_TOP_100("COMMUNITY TOP 100"),
    WEAPON_LEADERS("WEAPON LEADERS"),
    COMMUNITY_WEAPON_LEADERS("COMMUNITY WEAPON LEADERS"),
    LAP_RECORDS("LAP RECORDS"),

    // Variations
    FORWARD("FORWARD"),
    REVERSE("REVERSE");

    public final String name;

    LeaderboardLabel(String name) {
        this.name = name;
    }

}
