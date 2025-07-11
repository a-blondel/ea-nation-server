package com.ea.enums;

public enum MohhRankCategory {
    MY_LEADERBOARD("0", "0"), // MY EA LEADERBOARD
    MY_COMMUNITY_LEADERBOARD("1", "0"), // MY COMMUNITY LEADERBOARD
    TOP_100("2", "1"), // EA TOP 100
    COMMUNITY_TOP_100("3", "1"), // COMMUNITY TOP 100
    WEAPON_LEADERS("4", "2"), // EA WEAPON LEADERS
    COMMUNITY_WEAPON_LEADERS("5", "2"); // COMMUNITY WEAPON LEADERS

    public final String mohhId;
    public final String mohh2Id;

    MohhRankCategory(String mohhId, String mohh2Id) {
        this.mohhId = mohhId;
        this.mohh2Id = mohh2Id;
    }

    public static MohhRankCategory getRankingCategory(boolean isMohh, String rankingCategory) {
        for (MohhRankCategory category : MohhRankCategory.values()) {
            if (isMohh && category.mohhId.equals(rankingCategory)) {
                return category;
            } else if (!isMohh && category.mohh2Id.equals(rankingCategory)) {
                return category;
            }
        }
        return null;
    }
}
