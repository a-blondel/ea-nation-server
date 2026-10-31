package eu.eahub.utils;

import eu.eahub.entities.stats.TwScorecard;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scorecard fields, plausibility limits and par estimates, adapted from TW04OnlineServer twrecords.clean
 * and twtourney.card_par. See THIRD-PARTY-TW04.txt.
 */
public final class TwCardUtils {

    // Scorecard fields in the game's order: the match report suffixes them with the player's side, the tournament
    // scorecard holds them as words. RANK is stored in the RNK column
    public static final List<String> CARD_FIELDS = List.of("RANK", "DONE", "QUIT", "TYPE", "SCORE", "CLUB", "STROKES", "PUTTS", "HOLES",
            "EAGS", "BIRD", "ACES", "GIR", "LPUT", "DRVS", "FRWY", "LDRV", "SHTC", "PARS", "SBOG", "DBOG", "TBOG", "COMP");
    // Course names in the game's order, from the string block at 0x00308550
    public static final String[] COURSES = {
            "Pebble Beach", "Princeville Resort", "TPC at Sawgrass", "Black Rock Cove", "Penguin Falls", "Bethpage Black",
            "Royal Birkdale", "Skillz", "Bay Hill Club", "The Predator", "Spyglass Hill", "Poppy Hills", "The Highlands",
            "TPC of Scottsdale", "Torrey Pines", "St Andrews", "Sahalee CC", "Emerald Dragon", "Wallaby Creek",
            "Kapalua Plantation", "Pinehurst No. 2", "NA", "Tiger's Dream 18"
    };
    public static final int DEFAULT_PAR = 72;
    private static final int PAR_LOW = 67;
    private static final int PAR_HIGH = 76;
    private static final int LONGEST_DRIVE_YARDS = 450;
    private static final int LONGEST_PUTT_FEET = 150;
    // The hole results, which add up to the holes played
    private static final List<String> CLASSES = List.of("ACES", "EAGS", "BIRD", "PARS", "SBOG", "DBOG", "TBOG");

    private TwCardUtils() {
    }

    /**
     * Copy a scorecard into an entity's columns
     *
     * @param scorecard The entity to fill
     * @param card      The scorecard fields
     */
    public static void setCard(TwScorecard scorecard, Map<String, Long> card) {
        scorecard.setRnk(card.getOrDefault("RANK", 0L).intValue());
        scorecard.setDone(card.getOrDefault("DONE", 0L).intValue());
        scorecard.setQuit(card.getOrDefault("QUIT", 0L).intValue());
        scorecard.setType(card.getOrDefault("TYPE", 0L).intValue());
        scorecard.setScore(card.getOrDefault("SCORE", 0L).intValue());
        scorecard.setClub(card.getOrDefault("CLUB", 0L).intValue());
        scorecard.setStrokes(card.getOrDefault("STROKES", 0L).intValue());
        scorecard.setPutts(card.getOrDefault("PUTTS", 0L).intValue());
        scorecard.setHoles(card.getOrDefault("HOLES", 0L).intValue());
        scorecard.setEags(card.getOrDefault("EAGS", 0L).intValue());
        scorecard.setBird(card.getOrDefault("BIRD", 0L).intValue());
        scorecard.setAces(card.getOrDefault("ACES", 0L).intValue());
        scorecard.setGir(card.getOrDefault("GIR", 0L).intValue());
        scorecard.setLput(card.getOrDefault("LPUT", 0L).intValue());
        scorecard.setDrvs(card.getOrDefault("DRVS", 0L).intValue());
        scorecard.setFrwy(card.getOrDefault("FRWY", 0L).intValue());
        scorecard.setLdrv(card.getOrDefault("LDRV", 0L).intValue());
        scorecard.setShtc(card.getOrDefault("SHTC", 0L).intValue());
        scorecard.setPars(card.getOrDefault("PARS", 0L).intValue());
        scorecard.setSbog(card.getOrDefault("SBOG", 0L).intValue());
        scorecard.setDbog(card.getOrDefault("DBOG", 0L).intValue());
        scorecard.setTbog(card.getOrDefault("TBOG", 0L).intValue());
        scorecard.setComp(card.getOrDefault("COMP", 0L).intValue());
    }

    /**
     * Get an entity's scorecard columns as card fields (STROKES, HOLES, ...)
     *
     * @param scorecard The entity
     * @return The scorecard fields
     */
    public static Map<String, Long> card(TwScorecard scorecard) {
        int[] values = {scorecard.getRnk(), scorecard.getDone(), scorecard.getQuit(), scorecard.getType(), scorecard.getScore(),
                scorecard.getClub(), scorecard.getStrokes(), scorecard.getPutts(), scorecard.getHoles(), scorecard.getEags(),
                scorecard.getBird(), scorecard.getAces(), scorecard.getGir(), scorecard.getLput(), scorecard.getDrvs(),
                scorecard.getFrwy(), scorecard.getLdrv(), scorecard.getShtc(), scorecard.getPars(), scorecard.getSbog(),
                scorecard.getDbog(), scorecard.getTbog(), scorecard.getComp()};
        Map<String, Long> card = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            card.put(CARD_FIELDS.get(i), (long) values[i]);
        }
        return card;
    }

    /**
     * Copy a scorecard with every impossible value removed
     * Consoles have reported e.g. PUTTS=1025 for 18 holes; an absent field is left out of totals,
     * averages and records instead of rejecting the whole round.
     *
     * @param raw The scorecard fields as reported by the console
     * @return A cleaned copy of the scorecard
     */
    public static Map<String, Long> clean(Map<String, Long> raw) {
        Map<String, Long> card = new HashMap<>(raw);
        long holes = card.getOrDefault("HOLES", 0L);
        within(card, "STROKES", holes, holes * 12);
        within(card, "PUTTS", 1, holes * 6); // 0 is "not reported", not a record
        within(card, "GIR", 0, holes);
        within(card, "DRVS", 0, holes);
        Long drives = card.get("DRVS");
        Long fairways = card.get("FRWY");
        if (drives == null || fairways != null && fairways > drives) {
            card.remove("FRWY");
        }
        within(card, "LDRV", 1, LONGEST_DRIVE_YARDS);
        within(card, "LPUT", 1, LONGEST_PUTT_FEET);
        for (String key : CLASSES) {
            within(card, key, 0, holes);
        }
        return card;
    }

    /**
     * Keep a field when its value is within bounds, remove it otherwise
     *
     * @param card The scorecard fields
     * @param key  The field
     * @param low  The lowest plausible value
     * @param high The highest plausible value
     */
    private static void within(Map<String, Long> card, String key, long low, long high) {
        long value = card.getOrDefault(key, 0L);
        if (value < low || value > high) {
            card.remove(key);
        } else {
            card.put(key, value);
        }
    }

    /**
     * Whether a scorecard's hole results add up to its holes
     *
     * @param card The scorecard fields
     * @return True when the aces, eagles, birdies, pars and bogeys count every hole played
     */
    public static boolean addsUp(Map<String, Long> card) {
        long holes = 0;
        for (String key : CLASSES) {
            holes += card.getOrDefault(key, 0L);
        }
        return holes == card.getOrDefault("HOLES", 0L);
    }

    /**
     * Estimate the par of a course from an ace-free, complete 18-hole card
     * TBOG means "triple or worse", so this errs high; the smallest bound a course has produced
     * is the best estimate.
     *
     * @param card The cleaned scorecard fields
     * @return The implied par, or null if the card cannot tell
     */
    public static Integer par(Map<String, Long> card) {
        if (card.getOrDefault("ACES", 0L) != 0 || card.getOrDefault("HOLES", 0L) != 18 || !addsUp(card)) {
            return null;
        }
        long par = card.getOrDefault("STROKES", 0L) + card.getOrDefault("BIRD", 0L) + 2 * card.getOrDefault("EAGS", 0L)
                - card.getOrDefault("SBOG", 0L) - 2 * card.getOrDefault("DBOG", 0L) - 3 * card.getOrDefault("TBOG", 0L);
        return par >= PAR_LOW && par <= PAR_HIGH ? (int) par : null;
    }

    /**
     * Format a score relative to par
     *
     * @param difference Strokes over (positive) or under (negative) par
     * @return "E" for even, otherwise a signed number such as "+3" or "-2"
     */
    public static String toPar(int difference) {
        return difference == 0 ? "E" : String.format("%+d", difference);
    }
}
