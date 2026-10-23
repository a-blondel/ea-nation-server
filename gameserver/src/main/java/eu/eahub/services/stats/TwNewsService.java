package eu.eahub.services.stats;

import eu.eahub.config.TwConfig;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.entities.stats.TwEntryEntity;
import eu.eahub.entities.stats.TwGameReportEntity;
import eu.eahub.repositories.stats.TwEntryRepository;
import eu.eahub.repositories.stats.TwGameReportRepository;
import eu.eahub.utils.TwCardUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The generated half of the in-game news screen: today's event and leader, yesterday's winner, records set this week
 * and the week's busiest player. Sections with nothing to say are left out
 * Adapted from TW04OnlineServer twrecords.news; see THIRD-PARTY-TW04.txt
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TwNewsService {

    // The news code wraps at 64, but its box clips past ~45 proportional characters
    static final int WIDTH = 40;
    private static final long WEEK = 7 * 86400L;
    private static final int MAX_RECORDS = 6;
    private static final String[][] CONDITIONS = {
            {"tees", "White", "Blue"}, {"rough", "Short", "Long"}, {"fairways", "Medium", "Fast"}, {"greens", "Slow", "Fast"}
    };
    private static final int[][] CONDITION_BITS = {{1 << 3, 1 << 4}, {1 << 13, 1 << 15}, {1 << 17, 1 << 18}, {1 << 10, 1 << 12}};

    private static final long CACHE_SECONDS = 60;
    private static final int MAX_NEWS_LENGTH = 4998;

    private final TwGameReportRepository twGameReportRepository;
    private final TwEntryRepository twEntryRepository;
    private final TwTournamentService twTournamentService;
    private final TwConfig twConfig;
    private final Map<String, Cached> cache = new HashMap<>();

    private record Cached(String text, long at) {
    }

    /**
     * One finished round by one player; to-par is set for 18-hole rounds with a stroke total
     */
    record Played(long id, String persona, boolean tourney, boolean counted, long when, Integer day, Integer course,
                  Map<String, Long> fields, Integer toPar) {

        Long get(String key) {
            return fields.get(key);
        }

        boolean full() {
            return fields.getOrDefault("HOLES", 0L) == 18 && fields.containsKey("STROKES");
        }

        int strokes() {
            return fields.get("STROKES").intValue();
        }

        Played with(Map<String, Long> fields, boolean counted, Integer toPar) {
            return new Played(id, persona, tourney, counted, when, day, course, fields, toPar);
        }
    }

    /**
     * Get the news text: the operator's text (tw04.news, or the tw04.news-file contents), then the digest when tw04.auto-news is
     * on. The operator's text comes first, as maintenance notices matter more than who leads today
     *
     * @param vers the game version
     * @return the news, at most 4998 characters
     */
    public String news(String vers) {
        String text = twConfig.getNews();
        if (!twConfig.getNewsFile().isBlank()) {
            try {
                String file = Files.readString(Path.of(twConfig.getNewsFile()), StandardCharsets.UTF_8);
                text = file.substring(0, Math.min(MAX_NEWS_LENGTH, file.length()));
            } catch (IOException e) {
                log.warn("TW04 news file unavailable; using the configured news text");
            }
        }
        StringBuilder wrapped = new StringBuilder();
        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            wrap(line.replaceAll("[^ -~]", "?"), "", wrapped);
        }
        if (twConfig.isAutoNews()) {
            try {
                String generated = digest(vers);
                if (!generated.isEmpty()) {
                    wrapped = new StringBuilder(wrapped.toString().stripTrailing()).append("\n\n").append(generated.replaceAll("[^ -~\n]", "?"));
                }
            } catch (RuntimeException e) {
                log.warn("TW04 news digest unavailable", e);
            }
        }
        return wrapped.substring(0, Math.min(MAX_NEWS_LENGTH, wrapped.length())).stripTrailing() + "\n";
    }

    /**
     * Build the news digest
     *
     * @return the digest, already wrapped
     */
    public synchronized String digest(String vers) {
        Cached cached = cache.get(vers);
        if (cached != null && System.nanoTime() - cached.at() < CACHE_SECONDS * 1_000_000_000L) {
            return cached.text();
        }
        String text = build(vers);
        cache.put(vers, new Cached(text, System.nanoTime()));
        return text;
    }

    private String build(String vers) {
        Clock clock = twConfig.clock();
        long now = clock.instant().getEpochSecond();
        int today = twTournamentService.today();
        twTournamentService.ensureCalendar(vers);
        List<Played> rs = rounds(vers);
        Comparator<Played> lowest = Comparator.comparingInt(Played::strokes).thenComparingLong(Played::when);
        List<List<String>> sections = new ArrayList<>();

        Optional<TwTournamentService.Event> current = twTournamentService.event(vers, today);
        if (current.isPresent()) {
            TwTournamentService.Event event = current.get();
            List<String> lines = new ArrayList<>();
            lines.add("TODAY: " + event.name());
            lines.add(TwCardUtils.courseName(event.course()) + (event.purse() > 0 ? ", purse " + money(event.purse()) : ""));
            lines.add(conditions(event.conditions()));
            List<Played> board = board(rs, today);
            if (event.series() != null) {
                lines.add("One round a day: your first start counts.");
                lines.add(standing(vers, today, "Leader:"));
            } else if (board.isEmpty()) {
                lines.add("Nobody has posted a score yet.");
            } else {
                Played lead = board.stream().min(lowest).orElseThrow();
                lines.add("Leader: %s %d (%s), %d in the field".formatted(lead.persona(), lead.strokes(), toPar(lead), board.size()));
            }
            sections.add(lines);
        }

        List<Played> yesterday = board(rs, today - 1);
        Optional<TwTournamentService.Event> previous = twTournamentService.event(vers, today - 1);
        if (previous.isPresent() && previous.get().series() != null) {
            // A multi-day event reports its running total, and its winner on the last day
            if (!twTournamentService.day(vers, today - 1).isEmpty()) {
                String label = previous.get().last() == today - 1 ? "Won by" : "Led by";
                sections.add(List.of("YESTERDAY: " + previous.get().name(), standing(vers, today - 1, label)));
            }
        } else if (!yesterday.isEmpty()) {
            Played win = yesterday.stream().min(lowest).orElseThrow();
            String event = previous.map(TwTournamentService.Event::name).orElse("the daily event");
            sections.add(List.of("YESTERDAY: " + event, "Won by %s with %d (%s)".formatted(win.persona(), win.strokes(), toPar(win))));
        }

        // Only the headline records: on a young server every record is new this week
        List<String> fresh = new ArrayList<>();
        List<Played> full = rs.stream().filter(Played::full).toList();
        best(full, "STROKES", true).filter(r -> now - r.when() < WEEK)
                .ifPresent(r -> fresh.add("Lowest round: %s, %d (%s)".formatted(r.persona(), r.strokes(), toPar(r))));
        best(rs, "LDRV", false).filter(r -> now - r.when() < WEEK)
                .ifPresent(r -> fresh.add("Longest drive: %s, %d yd".formatted(r.persona(), r.get("LDRV"))));
        best(rs, "LPUT", false).filter(r -> now - r.when() < WEEK)
                .ifPresent(r -> fresh.add("Longest putt holed: %s, %d ft".formatted(r.persona(), r.get("LPUT"))));
        for (Map.Entry<Integer, Played> course : courses(rs)) {
            Played record = course.getValue();
            if (now - record.when() < WEEK) {
                fresh.add("Course record, %s: %s %d".formatted(TwCardUtils.courseName(course.getKey()), record.persona(), record.strokes()));
            }
        }
        for (Played r : rs) {
            if (r.get("ACES") != null && r.get("ACES") > 0 && now - r.when() < WEEK) {
                fresh.add("Hole in one: %s at %s".formatted(r.persona(), TwCardUtils.courseName(r.course())));
            }
        }
        if (!fresh.isEmpty()) {
            List<String> lines = new ArrayList<>(List.of("NEW RECORDS THIS WEEK"));
            lines.addAll(fresh.subList(0, Math.min(MAX_RECORDS, fresh.size())));
            sections.add(lines);
        }

        List<Played> week = rs.stream().filter(r -> now - r.when() < WEEK).toList();
        if (!week.isEmpty()) {
            Map<Long, Integer> counts = new LinkedHashMap<>();
            Map<Long, String> names = new HashMap<>();
            for (Played r : week) {
                counts.merge(r.id(), 1, Integer::sum);
                names.putIfAbsent(r.id(), r.persona());
            }
            long busiest = counts.keySet().iterator().next();
            for (Map.Entry<Long, Integer> count : counts.entrySet()) {
                if (count.getValue() > counts.get(busiest)) {
                    busiest = count.getKey();
                }
            }
            List<String> lines = new ArrayList<>();
            lines.add("THIS WEEK: %d round%s played".formatted(week.size(), week.size() == 1 ? "" : "s"));
            lines.add("Most active: %s (%d)".formatted(names.get(busiest), counts.get(busiest)));
            week.stream()
                    .filter(r -> r.toPar() != null)
                    .min(Comparator.comparingInt(Played::toPar).thenComparingLong(Played::when))
                    .ifPresent(b -> lines.add("Best round: %s %d (%s) at %s".formatted(b.persona(), b.strokes(), toPar(b),
                            TwCardUtils.courseName(b.course()))));
            sections.add(lines);
        }

        StringBuilder out = new StringBuilder();
        for (List<String> lines : sections) {
            for (String line : lines) {
                wrap(line, "  ", out);
            }
            out.append('\n');
        }
        return out.toString().stripTrailing();
    }

    /**
     * Every finished round, oldest first, cleaned, with course pars learnt from all cards
     */
    List<Played> rounds(String vers) {
        Clock clock = twConfig.clock();
        List<Played> raw = new ArrayList<>();
        Map<Long, List<TwGameReportEntity>> games = new LinkedHashMap<>();
        for (TwGameReportEntity report : twGameReportRepository.findByVersSince(vers, LocalDateTime.of(1970, 1, 1, 0, 0))) {
            games.computeIfAbsent(report.getGameConnection().getGame().getId(), id -> new ArrayList<>()).add(report);
        }
        for (List<TwGameReportEntity> reports : games.values()) {
            for (TwGameReportEntity report : reports) {
                PersonaEntity persona = report.getGameConnection().getPersonaConnection().getPersona();
                LocalDateTime when = Optional.ofNullable(report.getGameConnection().getGame().getEndTime())
                        .orElse(report.getGameConnection().getGame().getStartTime());
                raw.add(new Played(persona.getId(), persona.getPers(), false, false, epochSecond(when), null,
                        course(report.getGameConnection().getGame().getParams()), TwStatsService.card(report), null));
            }
        }
        // Tournament rounds: every attempt is golf; only each player's best completed round of the day stands on the
        // board, as in TwTournamentService.day. A multi-day event counts only the day's first round
        List<Played> attempts = new ArrayList<>();
        for (TwEntryEntity entry : twEntryRepository.findReported(vers)) {
            attempts.add(new Played(entry.getPersona().getId(), entry.getPersona().getPers(), true, entry.isCounted(), epochSecond(entry.getEndTime()),
                    entry.getEvent().getEventDay(), entry.getEvent().getCourse(), TwTournamentService.card(entry), null));
        }
        Map<String, Played> kept = new HashMap<>();
        for (Played r : attempts) {
            if (r.fields().getOrDefault("DONE", 0L) == 1 && r.fields().getOrDefault("QUIT", 0L) == 0
                    && r.fields().getOrDefault("HOLES", 0L) == 18) {
                kept.merge(r.id() + "/" + r.day(), r, (a, b) -> a.get("STROKES") <= b.get("STROKES") ? a : b);
            }
        }
        for (Played r : attempts) {
            raw.add(r.with(r.fields(), r.counted() && kept.get(r.id() + "/" + r.day()) == r, null));
        }

        List<Played> done = new ArrayList<>();
        for (Played r : raw) {
            Map<String, Long> f = r.fields();
            if (f.getOrDefault("DONE", 0L) != 1 || f.getOrDefault("QUIT", 0L) != 0 || f.getOrDefault("HOLES", 0L) <= 0) {
                continue;
            }
            done.add(r.with(TwCardUtils.clean(f), r.counted(), null));
        }
        // Every bound errs high, so the smallest one a course has produced is its par
        Map<Integer, Integer> pars = new HashMap<>();
        for (Played r : done) {
            Integer bound = r.course() == null || !r.full() ? null : TwCardUtils.par(r.fields());
            if (bound != null) {
                pars.merge(r.course(), bound, Math::min);
            }
        }
        List<Played> out = new ArrayList<>();
        for (Played r : done) {
            Integer toPar = r.full() ? r.strokes() - pars.getOrDefault(r.course(), TwCardUtils.DEFAULT_PAR) : null;
            out.add(r.with(r.fields(), r.counted(), toPar));
        }
        out.sort(Comparator.comparingLong(Played::when));
        return out;
    }

    private static long epochSecond(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    /**
     * The course of a head-to-head match, stored first in the game's PARAMS ("course,seed")
     */
    private static Integer course(String params) {
        try {
            return params == null || params.startsWith(",") ? null : Integer.valueOf(params.split(",")[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * "Leader: Alice 205 (-11) after 3 rounds, 12 in the field" from a multi-day board
     */
    private String standing(String vers, int day, String label) {
        List<TwTournamentService.Round> board = twTournamentService.day(vers, day);
        if (board.isEmpty()) {
            return "Nobody has posted every round yet.";
        }
        TwTournamentService.Round lead = board.getFirst();
        return "%s %s %d (%s) after %d round%s, %d in the field".formatted(label, lead.name(), lead.total(),
                twTournamentService.toPar(vers, lead), lead.rounds(), lead.rounds() == 1 ? "" : "s", board.size());
    }

    private static List<Played> board(List<Played> rs, int day) {
        return rs.stream().filter(r -> r.counted() && r.day() == day && r.get("STROKES") != null).toList();
    }

    /**
     * The holder of a record: lowest or highest positive value, earliest on a tie
     */
    private static Optional<Played> best(List<Played> pool, String key, boolean low) {
        Comparator<Played> order = Comparator.comparingLong((Played r) -> low ? r.get(key) : -r.get(key)).thenComparingLong(Played::when);
        return pool.stream().filter(r -> r.get(key) != null && (low || r.get(key) > 0)).min(order);
    }

    /**
     * Course records (best 18-hole round), hardest course first by average to par
     */
    private static List<Map.Entry<Integer, Played>> courses(List<Played> rs) {
        Map<Integer, List<Played>> byCourse = new HashMap<>();
        for (Played r : rs) {
            if (r.course() != null && r.full()) {
                byCourse.computeIfAbsent(r.course(), c -> new ArrayList<>()).add(r);
            }
        }
        Comparator<Map.Entry<Integer, List<Played>>> hardest = Comparator.comparingDouble(
                e -> -e.getValue().stream().mapToInt(Played::toPar).average().orElse(0));
        return byCourse.entrySet().stream()
                .sorted(hardest.thenComparing(e -> TwCardUtils.courseName(e.getKey())))
                .map(e -> Map.entry(e.getKey(), e.getValue().stream()
                        .min(Comparator.comparingInt(Played::strokes).thenComparingLong(Played::when))
                        .orElseThrow()))
                .toList();
    }

    static String conditions(int word) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < CONDITIONS.length; i++) {
            for (int option = 0; option < 2; option++) {
                if ((word & CONDITION_BITS[i][option]) != 0) {
                    parts.add(CONDITIONS[i][option + 1].toLowerCase() + " " + CONDITIONS[i][0]);
                }
            }
        }
        if (parts.isEmpty()) {
            return "Standard conditions";
        }
        String text = String.join(", ", parts);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String toPar(Played r) {
        return r.toPar() == null ? "" : TwCardUtils.toPar(r.toPar());
    }

    private static String money(long amount) {
        return String.format(Locale.US, "$%,d", amount);
    }

    /**
     * Greedy word wrap at WIDTH, like Python's textwrap.wrap; a blank line stays blank
     *
     * @param line   the line to wrap
     * @param indent the indent of continuation lines
     * @param out    where the wrapped lines are appended
     */
    public static void wrap(String line, String indent, StringBuilder out) {
        String prefix = "";
        StringBuilder current = new StringBuilder();
        for (String word : line.strip().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            while (word.length() > WIDTH - prefix.length()) {
                if (!current.isEmpty()) {
                    out.append(prefix).append(current).append('\n');
                    current.setLength(0);
                    prefix = indent;
                }
                int room = WIDTH - prefix.length();
                out.append(prefix).append(word, 0, room).append('\n');
                word = word.substring(room);
                prefix = indent;
            }
            if (!current.isEmpty() && prefix.length() + current.length() + 1 + word.length() > WIDTH) {
                out.append(prefix).append(current).append('\n');
                current.setLength(0);
                prefix = indent;
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty() || prefix.isEmpty()) {
            out.append(prefix).append(current).append('\n');
        }
    }
}
