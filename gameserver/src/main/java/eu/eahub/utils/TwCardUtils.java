package eu.eahub.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scorecard plausibility limits and par estimates, adapted from TW04OnlineServer twrecords.clean
 * and twtourney.card_par. See THIRD-PARTY-TW04.txt.
 */
public final class TwCardUtils {

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
    private static final List<String> CLASSES = List.of("ACES", "EAGS", "BIRD", "PARS", "SBOG", "DBOG", "TBOG");

    private TwCardUtils() {
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
        Map<String, Long> f = new HashMap<>(raw);
        long holes = f.getOrDefault("HOLES", 0L);
        within(f, "STROKES", holes, holes * 12);
        within(f, "PUTTS", 1, holes * 6); // 0 is "not reported", not a record
        within(f, "GIR", 0, holes);
        within(f, "DRVS", 0, holes);
        Long drives = f.get("DRVS");
        Long fairways = f.get("FRWY");
        if (drives == null || fairways != null && fairways > drives) {
            f.remove("FRWY");
        }
        within(f, "LDRV", 1, 450); // yards
        within(f, "LPUT", 1, 150); // feet
        for (String key : CLASSES) {
            within(f, key, 0, holes);
        }
        return f;
    }

    private static void within(Map<String, Long> f, String key, long low, long high) {
        long value = f.getOrDefault(key, 0L);
        if (value < low || value > high) {
            f.remove(key);
        } else {
            f.put(key, value);
        }
    }

    /**
     * Estimate the par of a course from an ace-free, complete 18-hole card
     * TBOG means "triple or worse", so this errs high; the smallest bound a course has produced
     * is the best estimate.
     *
     * @param f The cleaned scorecard fields
     * @return The implied par, or null if the card cannot tell
     */
    public static Integer par(Map<String, Long> f) {
        if (f.getOrDefault("ACES", 0L) != 0) {
            return null;
        }
        long holes = 0;
        for (String key : CLASSES) {
            holes += f.getOrDefault(key, 0L);
        }
        if (holes != 18 || f.getOrDefault("HOLES", 0L) != 18) {
            return null;
        }
        long par = f.getOrDefault("STROKES", 0L) + f.getOrDefault("BIRD", 0L) + 2 * f.getOrDefault("EAGS", 0L)
                - f.getOrDefault("SBOG", 0L) - 2 * f.getOrDefault("DBOG", 0L) - 3 * f.getOrDefault("TBOG", 0L);
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

    /**
     * Get the display name of a course
     *
     * @param course The course index, or null
     * @return The course name, or a placeholder for an index outside the course list
     */
    public static String courseName(Integer course) {
        if (course == null) {
            return "Unknown course";
        }
        return course >= 0 && course < COURSES.length ? COURSES[course] : "course " + course;
    }
}
