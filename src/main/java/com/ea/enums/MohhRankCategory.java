package com.ea.enums;

public enum MohhRankCategory {
    MOHH_MY_LEADERBOARD_ID("0", "0"), // MY EA LEADERBOARD
    MOHH_MY_COMMUNITY_LEADERBOARD_ID("1", "0"), // MY COMMUNITY LEADERBOARD
    MOHH_TOP_100_ID("2", "1"), // EA TOP 100
    MOHH_COMMUNITY_TOP_100_ID("3", "1"), // COMMUNITY TOP 100
    MOHH_WEAPON_LEADERS_ID("4", "2"), // EA WEAPON LEADERS
    MOHH_COMMUNITY_WEAPON_LEADERS_ID("5", "2"); // COMMUNITY WEAPON LEADERS

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
