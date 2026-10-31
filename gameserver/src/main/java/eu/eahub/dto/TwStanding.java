package eu.eahub.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A Tiger Woods player's place on a leaderboard, as sent in +snp, +usr and onln
 * packed is the S field: the packed statistics, or the text shown on a tournament board
 */
@Getter
@AllArgsConstructor
public class TwStanding {
    private String name;
    private int rank;
    private long points;
    private long[] values;
    private String packed;
}
