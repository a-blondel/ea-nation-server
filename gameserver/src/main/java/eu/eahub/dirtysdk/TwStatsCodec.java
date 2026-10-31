package eu.eahub.dirtysdk;

import java.nio.charset.StandardCharsets;

/**
 * TW04's 56 LSB-first fields in fifteen high-bit escaped groups plus '!'. Layout from
 * TW04OnlineServer/twstats.py; see THIRD-PARTY-TW04.txt.
 */
public final class TwStatsCodec {

    private static final int[] WIDTHS = {
            32, 17, 15, 10, 17, 17, 3, 13, 13, 13, 13, 13, 13, 8, 8, 11, 11, 3, 3, 13, 13, 20, 20, 18,
            17, 17, 10, 10, 10, 10, 17, 17, 16, 17, 8, 17, 17, 9, 9, 8, 13, 8, 7, 10, 7, 7, 7, 16, 1, 1,
            25, 18, 8, 16, 20, 17
    };

    /**
     * The fields shown by the game, as read on the profile and leaderboard screens (TW04OnlineServer twstats.py)
     */
    public enum Field {
        POINTS(1), MATCH_POINTS(4), STROKE_POINTS(5), TIGER_STATUS(6),
        MATCH_WINS(7), MATCH_LOSSES(8), MATCH_TIES(9), STROKE_WINS(10), STROKE_LOSSES(11), STROKE_TIES(12),
        STROKE_STREAK(13), MATCH_STREAK(14), STROKE_INCOMPLETE(15), MATCH_INCOMPLETE(16), STROKE_COMPLETE(19), MATCH_COMPLETE(20),
        EVENTS(26), EVENT_WINS(27), TOP10(28), TOP25(29),
        EAGLES(30), BIRDIES(31), ACES(32), GIR(33), LONGEST_PUTT(34), FAIRWAYS(36), LONGEST_DRIVE(37), BEST_ROUND(39),
        SCORING_AVERAGE(41), GIR_PERCENT(42), PUTTS_PER_HOLE(43), HOLES_PER_EAGLE(44), BIRDIE_AVERAGE(45), DRIVING_ACCURACY(46),
        EARNINGS(50);

        public final int index;

        Field(int index) {
            this.index = index;
        }
    }

    private TwStatsCodec() {
    }

    /**
     * Get the width of a field
     *
     * @param field The field index
     * @return The width in bits
     */
    public static int width(int field) {
        return WIDTHS[field];
    }

    /**
     * Get the statistics of a persona that has not played yet
     *
     * @return The 56 default values
     */
    public static long[] defaults() {
        long[] values = new long[56];
        values[52] = 255;
        return values;
    }

    /**
     * Pack the 56 statistics into the escaped wire format, clamping each to its field width
     *
     * @param values The 56 statistics
     * @return The packed statistics, 121 ISO-8859-1 characters
     */
    public static String pack(long[] values) {
        if (values.length != 56) {
            throw new IllegalArgumentException("Expected 56 statistics");
        }
        byte[] raw = new byte[105];
        int offset = 0;
        for (int i = 0; i < WIDTHS.length; i++) {
            int width = WIDTHS[i];
            boolean signed = i == 13 || i == 14;
            long min = signed ? -(1L << (width - 1)) : 0;
            long max = (1L << (signed ? width - 1 : width)) - 1;
            long value = Math.clamp(values[i], min, max);
            for (int bit = 0; bit < width; bit++, offset++) {
                if ((value & (1L << bit)) != 0) {
                    raw[offset / 8] |= (byte) (1 << (offset % 8));
                }
            }
        }
        byte[] escaped = new byte[121];
        for (int group = 0; group < 15; group++) {
            int mask = 0x80;
            for (int n = 0; n < 7; n++) {
                int value = raw[group * 7 + n] & 255;
                if ((value & 128) != 0) {
                    mask |= 1 << n;
                }
                escaped[group * 8 + n + 1] = (byte) (value | 128);
            }
            escaped[group * 8] = (byte) mask;
        }
        escaped[120] = '!';
        return new String(escaped, StandardCharsets.ISO_8859_1);
    }
}
