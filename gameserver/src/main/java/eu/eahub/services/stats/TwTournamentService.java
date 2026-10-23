package eu.eahub.services.stats;

import eu.eahub.config.TwConfig;
import eu.eahub.dirtysdk.LobbyTagField;
import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.entities.stats.TwEntryEntity;
import eu.eahub.entities.stats.TwEventEntity;
import eu.eahub.entities.stats.TwGolferEntity;
import eu.eahub.entities.stats.TwPersonaStatsEntity;
import eu.eahub.repositories.stats.TwEntryRepository;
import eu.eahub.repositories.stats.TwEventRepository;
import eu.eahub.repositories.stats.TwGolferRepository;
import eu.eahub.repositories.stats.TwPersonaStatsRepository;
import eu.eahub.steps.SocketWriter;
import eu.eahub.utils.TwCardUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.net.Socket;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiger Woods tournaments: the server-owned calendar, start keys, scorecards and boards
 * Days count from 1899-12-30 like the game, and change at midnight in tw04.timezone
 * Wire formats are OnlineTourn.c's, see THIRD-PARTY-TW04.txt
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TwTournamentService {

    public static final LocalDate EPOCH = LocalDate.of(1899, 12, 30);
    // Hardest first. Excludes 7 Skillz, 21 NA and every composite (22..29): their hole lists live in each player's
    // profile (SLUS-20757 0x33FEE0, slot via 0x1BA2B0; built at 0x225830), so an unfilled slot plays Pebble Beach
    private static final int[] DIFFICULTY = {17, 9, 18, 12, 4, 3, 2, 5, 8, 1, 16, 14, 6, 15, 11, 10, 19, 0, 20, 13};
    static final int MAJOR_DAYS = 4;
    static final int WEEKEND_DAYS = 3;
    private static final int MAJOR_ICON = 4; // gold star
    private static final long ENTRY_LIFETIME_HOURS = 48;
    private static final long PAR_CACHE_SECONDS = 60;

    /**
     * A four-day event, Thursday to the given Sunday, its course rotating through a pool by year
     */
    private record Major(String name, int month, int sunday, long purse, int conditions, int[] pool) {
    }

    // TW04's own season names (LOADONCE.PS2 PGAn). THE PLAYERS is not a major but fits the pools
    private static final List<Major> MAJORS = List.of(
            new Major("THE PLAYERS Championship", 3, 3, 12_000_000, 1 << 12, new int[]{2}),
            new Major("Southern Major Championship", 4, 2, 15_000_000, 1 << 12, new int[]{20, 8, 16}),
            new Major("PGA TOUR Major Championship", 5, 3, 15_000_000, 1 << 15, new int[]{5, 16, 20}),
            new Major("US Major Championship", 6, 3, 15_000_000, 1 << 15 | 1 << 12, new int[]{0, 5, 20, 14}),
            new Major("UK Major Championship", 7, 3, 15_000_000, 1 << 18, new int[]{15, 6}));
    // esr2t without a key or an event: the client declines to start (0x002DE1FC)
    private static final Map<String, String> START_REFUSED = Map.of("CMD", "esr2t", "TKEY", "$" + "00".repeat(16), "DATA", "");
    // Scorecard words, in order; RANK is stored in the RNK column
    private static final String[] ROUND_FIELDS = ("RANK DONE QUIT TYPE SCORE CLUB STROKES PUTTS HOLES EAGS BIRD ACES GIR LPUT"
            + " DRVS FRWY LDRV SHTC PARS SBOG DBOG TBOG COMP").split(" ");

    private final TwEventRepository twEventRepository;
    private final TwEntryRepository twEntryRepository;
    private final TwPersonaStatsRepository twPersonaStatsRepository;
    private final TwStatsService twStatsService;
    private final TwGolferRepository twGolferRepository;
    private final SocketWriter socketWriter;
    private final TwConfig twConfig;
    private final Map<String, Integer> generatedDay = new ConcurrentHashMap<>();
    private final Map<String, Integer> settledDay = new ConcurrentHashMap<>();
    private final Map<String, Par> pars = new ConcurrentHashMap<>();

    private record Par(int value, long at) {
    }

    /**
     * Handle a tournament or profile command (cusr CMD=...)
     * Only lts5d (today's date) is answered before a persona is selected
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void cusr(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String cmd = fields.getOrDefault("CMD", "");
        PersonaEntity persona = socketWrapper.getPersonaEntity();
        if (persona == null && !cmd.equals("lts5d")) {
            socketWriter.writeText(socket, "cusrnusr", "");
            return;
        }
        String vers = persona == null ? null : socketWrapper.getPersonaConnectionEntity().getVers();
        switch (cmd) {
            case "lts5d" -> socketWriter.writeText(socket, "cusr", String.valueOf(today()));
            case "ufpvt" -> {
                ensureCalendar(vers);
                int seasonFirst = today() - 7;
                socketWrapper.setSeasonFirst(seasonFirst);
                socketWriter.writeText(socket, "cusr", seasonFirst + " " + (seasonFirst + 14) + " 36 52 64 0");
            }
            case "mg5ri" -> socketWriter.writeText(socket, "cusr", calendar(vers, integer(fields, "START"), integer(fields, "NUM")));
            case "qdb@w" -> socketWriter.writeText(socket, "cusr", results(vers, persona, integer(fields, "START"), integer(fields, "NUM")));
            case "esr2t" -> socketWriter.writeTagFields(socket, "cusr", start(vers, persona));
            case "5d0tr" -> socketWriter.writeText(socket, "cusr", receive(vers, persona, fields.getOrDefault("DATA", "")));
            case "myrnk" -> {
                // RNKRS is 36 little-endian words; MY RESUME's earnings rank is word 10 (0x002A0E08)
                byte[] record = new byte[144];
                ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN).putInt(40, earningsRank(vers, persona));
                socketWriter.writeTagFields(socket, "cusr", Map.of("CMD", cmd, "RNKRS", "$" + HexFormat.of().formatHex(record)));
            }
            case "whomi" -> {
                String golfer = fields.get("CRPIN");
                if (golfer != null && golfer.length() <= 8193) {
                    saveGolfer(vers, persona, golfer);
                }
                socketWriter.writeTagFields(socket, "cusr", Map.of("CMD", cmd));
            }
            default -> socketWriter.writeText(socket, "cusrunav", "");
        }
    }

    private static int integer(Map<String, String> fields, String key) {
        try {
            return Integer.parseInt(fields.getOrDefault(key, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Store a player's created golfer, uploaded with whomi
     * The upload is high-bit escaped text, while user replies expect $hex binary
     */
    private synchronized void saveGolfer(String vers, PersonaEntity persona, String golfer) {
        String data = golfer.startsWith("$") && golfer.substring(1).matches("(?:[0-9a-fA-F]{2})*")
                ? golfer
                : "$" + HexFormat.of().formatHex(golfer.getBytes(StandardCharsets.ISO_8859_1));
        TwGolferEntity entity = twGolferRepository.findByPersonaIdAndVers(persona.getId(), vers).orElseGet(() -> {
            TwGolferEntity created = new TwGolferEntity();
            created.setPersona(persona);
            created.setVers(vers);
            return created;
        });
        entity.setData(data);
        entity.setUpdatedOn(LocalDateTime.now());
        twGolferRepository.save(entity);
    }

    public int today() {
        return (int) ChronoUnit.DAYS.between(EPOCH, LocalDate.now(twConfig.clock()));
    }

    // Stored timestamps are server time, like the rest of the database; the clock only decides the tournament day
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(twConfig.clock().instant(), ZoneId.systemDefault());
    }

    /**
     * A calendar event. series is the first day of a multi-day event, or null for a single-day event
     */
    public record Event(int day, String name, int course, long purse, int conditions, Integer series) {

        public Event(int day, String name, int course, long purse, int conditions) {
            this(day, name, course, purse, conditions, null);
        }

        /**
         * The day whose standings are final for this event
         */
        public int last() {
            return series == null ? day : series + seriesDays(series) - 1;
        }
    }

    /**
     * Length of the multi-day event that starts on this day
     * Majors and THE PLAYERS start on a Thursday; the weekend events in every other week start on a Friday
     *
     * @param start the event's first day
     * @return the number of rounds
     */
    public static int seriesDays(int start) {
        return EPOCH.plusDays(start).getDayOfWeek() == DayOfWeek.THURSDAY ? MAJOR_DAYS : WEEKEND_DAYS;
    }

    public record Entry(String name, byte[] data) {
    }

    /**
     * One player's place on a day's board. For a multi-day event the board is the running total:
     * total covers rounds days, one round per day, and fields/strokes are that day's round
     */
    public record Round(long id, String name, int day, int course, Map<String, Long> fields, int rank, long purse,
                        Integer series, int total, int rounds) {

        public int strokes() {
            return fields.get("STROKES").intValue();
        }
    }

    public record Row(String name, int rank, long sort, String text, long id) {
    }


    /**
     * Store the calendar from last month to next month, then settle the days that have ended
     *
     * @param vers the game version
     */
    public synchronized void ensureCalendar(String vers) {
        int day = today();
        if (!Objects.equals(generatedDay.get(vers), day)) {
            LocalDate month = LocalDate.now(twConfig.clock()).withDayOfMonth(1);
            for (LocalDate d = month.minusMonths(1); d.isBefore(month.plusMonths(2)); d = d.plusDays(1)) {
                int n = (int) ChronoUnit.DAYS.between(EPOCH, d);
                if (n < 0 || n > 65535 || twEventRepository.findByVersAndEventDay(vers, n).isPresent()) {
                    continue;
                }
                Event planned = plan(n);
                TwEventEntity event = new TwEventEntity();
                event.setVers(vers);
                event.setEventDay(n);
                event.setName(planned.name());
                event.setCourse(planned.course());
                event.setPurse(planned.purse());
                event.setConditions(planned.conditions());
                event.setSeriesStart(planned.series());
                twEventRepository.save(event);
            }
            generatedDay.put(vers, day);
        }
        if (!Objects.equals(settledDay.get(vers), day)) {
            settle(vers, day);
            settledDay.put(vers, day);
        }
    }

    /**
     * Reproducible server-owned calendar. Client START never creates events
     *
     * @param n the day
     * @return the day's event
     */
    static Event plan(int n) {
        LocalDate date = EPOCH.plusDays(n);
        for (Major m : MAJORS) {
            LocalDate sunday = date.withMonth(m.month()).with(TemporalAdjusters.dayOfWeekInMonth(m.sunday(), DayOfWeek.SUNDAY));
            LocalDate thursday = sunday.minusDays(MAJOR_DAYS - 1);
            if (date.isBefore(thursday) || date.isAfter(sunday)) {
                continue;
            }
            int round = (int) ChronoUnit.DAYS.between(thursday, date) + 1;
            int course = m.pool()[Math.floorMod(date.getYear(), m.pool().length)];
            return new Event(n, m.name() + " R" + round, course, m.purse(), m.conditions(), (int) ChronoUnit.DAYS.between(EPOCH, thursday));
        }
        if (date.getDayOfWeek().compareTo(DayOfWeek.FRIDAY) >= 0) {
            // Any other weekend is a three-round event on Friday's course and conditions
            LocalDate friday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
            int first = (int) ChronoUnit.DAYS.between(EPOCH, friday);
            Event base = daily(first);
            String name = base.name().substring(0, Math.min(29, base.name().length())).strip();
            return new Event(n, name + " R" + (n - first + 1), base.course(), Math.round(base.purse() * 1.5 / 50000) * 50000,
                    base.conditions(), first);
        }
        return daily(n);
    }

    /**
     * A single-day event: course by rotation, reproducible random conditions and purse
     */
    private static Event daily(int n) {
        int course = DIFFICULTY[Math.floorMod(n, DIFFICULTY.length)];
        Random random = new Random(0x54573034L + n);
        int[][] bits = {{0, 1 << 4, 1 << 3}, {0, 1 << 13, 1 << 15}, {0, 1 << 17, 1 << 18}, {0, 1 << 10, 1 << 12}};
        double[][] factors = {{1.10, 1, .90}, {1, .95, 1.10}, {.97, 1, 1.05}, {1, .95, 1.10}};
        int conditions = 0;
        double multiplier = 1;
        for (int k = 0; k < bits.length; k++) {
            int choice = random.nextInt(4);
            choice = choice < 2 ? 0 : choice - 1;
            conditions |= bits[k][choice];
            multiplier *= factors[k][choice];
        }
        int difficulty = Math.floorMod(n, DIFFICULTY.length);
        long purse = Math.round((5000000 - difficulty * 150000) * multiplier / 50000) * 50000;
        String[] suffixes = {"Open", "Classic", "Invitational", "Championship", "Masters", "Challenge", "Cup"};
        String name = TwCardUtils.COURSES[course] + " " + suffixes[Math.floorMod(n, 7)];
        if (name.length() > 32) {
            name = name.substring(0, 32);
        }
        return new Event(n, name, course, purse, conditions);
    }

    public Optional<Event> event(String vers, int day) {
        return twEventRepository.findByVersAndEventDay(vers, day).map(TwTournamentService::event);
    }

    private static Event event(TwEventEntity e) {
        return new Event(e.getEventDay(), e.getName(), e.getCourse(), e.getPurse(), e.getConditions(), e.getSeriesStart());
    }

    /**
     * Encode an event as a calendar entry
     *
     * @param e the event
     * @return the entry
     */
    public static Entry entry(Event e) {
        byte[] b = new byte[16];
        ByteBuffer out = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0, (int) e.purse()).putInt(8, e.conditions()).putShort(12, (short) e.day());
        b[14] = (byte) e.course();
        LocalDate date = EPOCH.plusDays(e.day());
        int key = e.series() != null && seriesDays(e.series()) == MAJOR_DAYS ? -1 : date.getMonthValue() * 100 + date.getDayOfMonth();
        b[15] = (byte) switch (key) {
            case -1 -> MAJOR_ICON;
            case 101 -> 24;
            case 115 -> 8;
            case 214 -> 0;
            case 222 -> 16;
            case 301 -> 14;
            case 317 -> 19;
            case 326 -> 26;
            case 408 -> 12;
            case 422 -> 25;
            case 502 -> 6;
            case 505 -> 29;
            case 602 -> 21;
            case 620 -> 4;
            case 621 -> 9;
            case 704 -> 1;
            case 805 -> 17;
            case 815 -> 27;
            case 910 -> 13;
            case 1001 -> 7;
            case 1030 -> 23;
            case 1031 -> 22;
            case 1111 -> 18;
            case 1224 -> 28;
            case 1225 -> 20;
            default -> 2;
        };
        return new Entry(e.name(), b);
    }

    /**
     * Encode entries as the client's list format
     *
     * @param entries the entries, at most 105
     * @return the count, then each name and its data, in hex
     */
    public static String list(List<Entry> entries) {
        if (entries.size() > 105) {
            throw new IllegalArgumentException("Too many entries");
        }
        StringBuilder out = new StringBuilder(String.format("%02X", entries.size()));
        for (Entry e : entries) {
            String name = e.name().replaceAll("[^ -~]", "?");
            name = name.substring(0, Math.min(32, name.length()));
            out.append(String.format("%02X", name.length())).append(name).append(HexFormat.of().withUpperCase().formatHex(e.data()));
        }
        return out.toString();
    }

    /**
     * Get the calendar (mg5ri)
     *
     * @param vers  the game version
     * @param start the first day, or -1 for today
     * @param count the number of days, at most 32
     * @return the events, as a list
     */
    public String calendar(String vers, int start, int count) {
        ensureCalendar(vers);
        if (start < 0) {
            start = today();
        }
        count = Math.clamp(count, 0, 32);
        List<Entry> entries = new ArrayList<>();
        if (start > 65535) {
            return "00";
        }
        for (TwEventEntity event : twEventRepository.findByVersAndEventDayBetweenOrderByEventDay(vers, start, Math.min(65535, start + count - 1))) {
            entries.add(entry(event(event)));
        }
        return list(entries);
    }

    /**
     * Issue a start key for today's event (esr2t)
     * A multi-day event allows one round a day, the first one started
     *
     * @param vers    the game version
     * @param persona the player
     * @return the key and the event, or a refusal
     */
    public synchronized Map<String, String> start(String vers, PersonaEntity persona) {
        ensureCalendar(vers);
        Optional<TwEventEntity> found = twEventRepository.findByVersAndEventDay(vers, today());
        if (found.isEmpty()) {
            return START_REFUSED;
        }
        TwEventEntity event = found.get();
        if (event.getSeriesStart() != null && twEntryRepository.existsByPersonaIdAndEventId(persona.getId(), event.getId())) {
            return START_REFUSED;
        }
        String key = UUID.randomUUID().toString().replace("-", "");
        TwEntryEntity entry = new TwEntryEntity();
        entry.setToken(key);
        entry.setEvent(event);
        entry.setPersona(persona);
        entry.setStartTime(now());
        twEntryRepository.save(entry);
        return Map.of("CMD", "esr2t", "TKEY", "$" + key, "DATA", list(List.of(entry(event(event)))));
    }

    public record Card(Map<String, Long> fields, String key, int day, int course) {
    }

    /**
     * Decode a scorecard (5d0tr)
     *
     * @param hex the card, in hex
     * @return the card's fields, start key, day and course
     */
    public static Card parse(String hex) {
        if (hex == null || hex.length() < 224 || hex.length() > 1024 || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException("Invalid result length");
        }
        byte[] data = HexFormat.of().parseHex(hex);
        ByteBuffer in = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        Map<String, Long> fields = new LinkedHashMap<>();
        for (String key : ROUND_FIELDS) {
            fields.put(key, Integer.toUnsignedLong(in.getInt()));
        }
        String key = HexFormat.of().formatHex(Arrays.copyOfRange(data, 92, 108));
        long holes = fields.get("HOLES");
        long classes = 0;
        for (String name : List.of("ACES", "EAGS", "BIRD", "PARS", "SBOG", "DBOG", "TBOG")) {
            classes += fields.get(name);
        }
        if (holes < 1 || holes > 18 || classes != holes
                || fields.get("STROKES") < holes || fields.get("STROKES") > holes * 12
                || fields.get("DONE") > 1 || fields.get("QUIT") > 1) {
            throw new IllegalArgumentException("Invalid scorecard");
        }
        for (String metric : List.of("PUTTS", "GIR", "LPUT", "DRVS", "FRWY", "LDRV")) {
            if (fields.get(metric) > 100000) {
                throw new IllegalArgumentException("Invalid golf statistic");
            }
        }
        return new Card(fields, key, Short.toUnsignedInt(in.getShort(108)), Byte.toUnsignedInt(data[110]));
    }

    /**
     * Record a scorecard against the start key it carries (5d0tr)
     *
     * @param vers    the game version
     * @param persona the player
     * @param data    the card, in hex
     * @return the receipt shown to the player
     */
    public synchronized String receive(String vers, PersonaEntity persona, String data) {
        Card card;
        try {
            card = parse(data);
        } catch (IllegalArgumentException ex) {
            return "That scorecard could not be read or does not add up.";
        }
        Optional<TwEntryEntity> found = twEntryRepository.findByTokenAndPersonaId(card.key(), persona.getId());
        if (found.isEmpty() || !vers.equals(found.get().getEvent().getVers())) {
            return "This round was not started on this server.";
        }
        TwEntryEntity entry = found.get();
        TwEventEntity event = entry.getEvent();
        int day = event.getEventDay();
        if (entry.getEndTime() != null) {
            return "This round has already been recorded.";
        }
        LocalDateTime now = now();
        if (Duration.between(entry.getStartTime(), now).toHours() >= ENTRY_LIFETIME_HOURS) {
            return "This tournament entry has expired.";
        }
        Integer series = event.getSeriesStart();
        boolean counted = finished(card.fields()) && (series == null || firstStart(entry));
        int first = series == null ? day : series;
        int last = series == null ? day : series + seriesDays(series) - 1;
        if (counted) {
            // Golf counts in the player's statistics, and the event as entered once
            TwPersonaStatsEntity stats = twStatsService.getOrCreateStats(persona, vers);
            if (!twEntryRepository.existsByPersonaIdAndCountedTrueAndEventVersAndEventEventDayBetween(persona.getId(), vers, first, last)) {
                stats.setEvents(stats.getEvents() + 1);
            }
            TwStatsService.addGolf(stats, card.fields());
            twPersonaStatsRepository.save(stats);
        }
        setCard(entry, card.fields());
        entry.setEndTime(now);
        entry.setCounted(counted);
        twEntryRepository.save(entry);
        // A card for an event that has already been settled changes its final standings
        twEventRepository.findByVersAndEventDay(vers, last)
                .filter(lastDay -> lastDay.getSettledOn() != null && last < today())
                .ifPresent(lastDay -> settle(vers, lastDay));
        if (!finished(card.fields())) {
            return series != null
                    ? "Round " + (day - series + 1) + " was not completed, so you are out of this event. Each day's first round is the only one that counts."
                    : "Your incomplete round was saved. Only completed 18-hole rounds qualify.";
        }
        Optional<Round> standing = day(vers, day).stream().filter(r -> r.id() == persona.getId()).findFirst();
        if (series != null) {
            int round = day - series + 1;
            String recorded = "Round " + round + " of " + seriesDays(series) + ": " + card.fields().get("STROKES") + " is recorded."
                    + (round < seriesDays(series) ? " That is your round for today; the next opens tomorrow." : "");
            if (standing.isEmpty()) {
                return recorded + "\nA missed round keeps you off the leaderboard.";
            }
            Round best = standing.get();
            return recorded + "\nTotal " + best.total() + " (" + toPar(vers, best) + "). Place: " + best.rank() + ".";
        }
        Round best = standing.orElseThrow();
        return "Your round of " + card.fields().get("STROKES") + " is recorded.\nBest today: " + best.strokes() + ". Place: " + best.rank() + ".";
    }

    /**
     * Whether an entry is its player's first start of the day (the only round that counts in a multi-day event)
     */
    private boolean firstStart(TwEntryEntity entry) {
        return twEntryRepository.findByPersonaIdAndEventIdOrderByStartTimeAscIdAsc(entry.getPersona().getId(), entry.getEvent().getId())
                .getFirst().getId().equals(entry.getId());
    }

    private static boolean finished(Map<String, Long> f) {
        return f.get("DONE") == 1 && f.get("QUIT") == 0 && f.get("HOLES") == 18;
    }

    private static void setCard(TwEntryEntity entry, Map<String, Long> f) {
        entry.setRnk(f.get("RANK").intValue());
        entry.setDone(f.get("DONE").intValue());
        entry.setQuit(f.get("QUIT").intValue());
        entry.setType(f.get("TYPE").intValue());
        entry.setScore(f.get("SCORE").intValue());
        entry.setClub(f.get("CLUB").intValue());
        entry.setStrokes(f.get("STROKES").intValue());
        entry.setPutts(f.get("PUTTS").intValue());
        entry.setHoles(f.get("HOLES").intValue());
        entry.setEags(f.get("EAGS").intValue());
        entry.setBird(f.get("BIRD").intValue());
        entry.setAces(f.get("ACES").intValue());
        entry.setGir(f.get("GIR").intValue());
        entry.setLput(f.get("LPUT").intValue());
        entry.setDrvs(f.get("DRVS").intValue());
        entry.setFrwy(f.get("FRWY").intValue());
        entry.setLdrv(f.get("LDRV").intValue());
        entry.setShtc(f.get("SHTC").intValue());
        entry.setPars(f.get("PARS").intValue());
        entry.setSbog(f.get("SBOG").intValue());
        entry.setDbog(f.get("DBOG").intValue());
        entry.setTbog(f.get("TBOG").intValue());
        entry.setComp(f.get("COMP").intValue());
    }

    /**
     * Get a stored scorecard as card fields (STROKES, HOLES, ...)
     *
     * @param e the entry
     * @return the card fields, absent ones left out
     */
    public static Map<String, Long> card(TwEntryEntity e) {
        Integer[] values = {e.getRnk(), e.getDone(), e.getQuit(), e.getType(), e.getScore(), e.getClub(), e.getStrokes(), e.getPutts(),
                e.getHoles(), e.getEags(), e.getBird(), e.getAces(), e.getGir(), e.getLput(), e.getDrvs(), e.getFrwy(), e.getLdrv(),
                e.getShtc(), e.getPars(), e.getSbog(), e.getDbog(), e.getTbog(), e.getComp()};
        Map<String, Long> card = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                card.put(ROUND_FIELDS[i], values[i].longValue());
            }
        }
        return card;
    }

    private static Round round(TwEntryEntity e) {
        Map<String, Long> f = card(e);
        return new Round(e.getPersona().getId(), e.getPersona().getPers(), e.getEvent().getEventDay(), e.getEvent().getCourse(), f, 0,
                e.getEvent().getPurse(), e.getEvent().getSeriesStart(), f.getOrDefault("STROKES", 0L).intValue(), 1);
    }

    /**
     * A day's board: each player's best completed round that day. On a multi-day event it is the running total of
     * each day's one counted (first) round since the first day, and only players who have completed every day so far
     * are on it
     *
     * @param vers the game version
     * @param day  the day
     * @return the board, best first
     */
    public List<Round> day(String vers, int day) {
        Optional<TwEventEntity> event = twEventRepository.findByVersAndEventDay(vers, day);
        if (event.isEmpty()) {
            return List.of();
        }
        Integer series = event.get().getSeriesStart();
        int first = series == null ? day : series;
        Map<Long, Map<Integer, Round>> best = new HashMap<>();
        for (TwEntryEntity entry : twEntryRepository.findCounted(vers, first, day)) {
            Round r = round(entry);
            if (Objects.equals(r.series(), series)) {
                best.computeIfAbsent(r.id(), id -> new HashMap<>()).merge(r.day(), r, (a, b) -> a.strokes() <= b.strokes() ? a : b);
            }
        }
        int played = day - first + 1;
        List<Round> totals = new ArrayList<>();
        for (Map<Integer, Round> days : best.values()) {
            if (days.size() != played) {
                continue;
            }
            Round today = days.get(day);
            int total = days.values().stream().mapToInt(Round::strokes).sum();
            totals.add(new Round(today.id(), today.name(), day, today.course(), today.fields(), 0, today.purse(), series, total, played));
        }
        totals.sort(Comparator.comparingInt(Round::total).thenComparing(Round::name));
        List<Round> result = new ArrayList<>();
        int rank = 0;
        int last = -1;
        for (int i = 0; i < totals.size(); i++) {
            Round r = totals.get(i);
            if (r.total() != last) {
                rank = i + 1;
            }
            last = r.total();
            result.add(new Round(r.id(), r.name(), r.day(), r.course(), r.fields(), rank, r.purse(), r.series(), r.total(), r.rounds()));
        }
        return result;
    }

    /**
     * Whether a day's standings decide its event: every single-day event, a series' last day
     */
    private static boolean decides(int day, Integer series) {
        return series == null || day == series + seriesDays(series) - 1;
    }

    /**
     * Prize money for a place
     *
     * @param purse the event's purse
     * @param place the place, from 1
     * @return the amount
     */
    public static long payout(long purse, int place) {
        double[] rates = {.18, .109, .068, .048, .04, .036, .0335, .031, .029, .027};
        if (place < 1) {
            return 0;
        }
        return (long) (purse * (place <= 10 ? rates[place - 1] : .027 * Math.pow(.85, place - 10)));
    }

    /**
     * Rank and pay the days before today that are not settled yet
     */
    private void settle(String vers, int today) {
        for (TwEventEntity event : twEventRepository.findByVersAndEventDayLessThanAndSettledOnIsNullOrderByEventDay(vers, today)) {
            settle(vers, event);
        }
    }

    /**
     * Settle a day: if it decides its event, pay the purse on its final standings and count each player's event win
     * and top finishes. Settling again (a scorecard can arrive after the day ended) only applies the difference
     */
    private void settle(String vers, TwEventEntity event) {
        int day = event.getEventDay();
        if (decides(day, event.getSeriesStart())) {
            Map<Long, Round> standings = new HashMap<>();
            for (Round r : day(vers, day)) {
                standings.put(r.id(), r);
            }
            Map<Long, TwEntryEntity> best = new HashMap<>();
            List<TwEntryEntity> entries = twEntryRepository.findCounted(vers, day, day);
            for (TwEntryEntity entry : entries) {
                best.merge(entry.getPersona().getId(), entry, (a, b) -> a.getStrokes() <= b.getStrokes() ? a : b);
            }
            for (TwEntryEntity entry : entries) {
                Round r = best.get(entry.getPersona().getId()) == entry ? standings.get(entry.getPersona().getId()) : null;
                Integer rank = r == null ? null : r.rank();
                Long prize = r == null ? null : payout(r.purse(), r.rank());
                if (!Objects.equals(rank, entry.getFinalRank()) || !Objects.equals(prize, entry.getPrize())) {
                    TwPersonaStatsEntity stats = twStatsService.getOrCreateStats(entry.getPersona(), vers);
                    addFinish(stats, entry.getFinalRank(), entry.getPrize(), -1);
                    addFinish(stats, rank, prize, 1);
                    twPersonaStatsRepository.save(stats);
                    entry.setFinalRank(rank);
                    entry.setPrize(prize);
                    twEntryRepository.save(entry);
                }
            }
        }
        event.setSettledOn(now());
        twEventRepository.save(event);
    }

    private static void addFinish(TwPersonaStatsEntity stats, Integer rank, Long prize, int sign) {
        if (rank == null) {
            return;
        }
        if (rank == 1) {
            stats.setEventWins(stats.getEventWins() + sign);
        }
        if (rank <= 10) {
            stats.setTop10(stats.getTop10() + sign);
        }
        if (rank <= 25) {
            stats.setTop25(stats.getTop25() + sign);
        }
        stats.setEarnings(stats.getEarnings() + sign * prize);
    }

    /**
     * Get a player's earnings rank (MY RESUME), 0 when they have won nothing
     *
     * @param vers    the game version
     * @param persona the player
     * @return the rank
     */
    public int earningsRank(String vers, PersonaEntity persona) {
        ensureCalendar(vers);
        long earnings = twPersonaStatsRepository.findByPersonaIdAndVers(persona.getId(), vers).map(TwPersonaStatsEntity::getEarnings).orElse(0L);
        return earnings == 0 ? 0 : (int) twPersonaStatsRepository.getEarningsRank(vers, earnings);
    }

    /**
     * A player's results (qdb@w)
     *
     * @param vers    the game version
     * @param persona the player
     * @param start   the first day, or -1 for today
     * @param count   the number of days, at most 32
     * @return the player's place each day, as a list
     */
    public String results(String vers, PersonaEntity persona, int start, int count) {
        if (start < 0) {
            start = today();
        }
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < Math.clamp(count, 0, 32); i++) {
            List<Round> board = day(vers, start + i);
            Optional<Round> mine = board.stream().filter(r -> r.id() == persona.getId()).findFirst();
            if (mine.isEmpty()) {
                continue;
            }
            Round r = mine.get();
            byte[] data = new byte[12];
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putShort(4, (short) r.day()).putInt(8, r.rank() - 1);
            data[6] = (byte) Math.min(255, board.getFirst().strokes());
            data[7] = (byte) Math.min(255, r.strokes());
            entries.add(new Entry(board.getFirst().name(), data));
        }
        return list(entries);
    }

    /**
     * To par for a board row: the running total against par for each round played
     * One par is estimated per course from every counted card, never a different par for each row
     *
     * @param vers the game version
     * @param r    the board row
     * @return E, or the difference with its sign
     */
    public String toPar(String vers, Round r) {
        int diff = r.total() - par(vers, r.course()) * r.rounds();
        return diff == 0 ? "E" : String.format("%+d", diff);
    }

    /**
     * Estimate a course's par: every bound errs high, so the smallest one its cards produce is its par
     */
    public int par(String vers, int course) {
        String key = vers + "/" + course;
        Par cached = pars.get(key);
        if (cached != null && System.nanoTime() - cached.at() < PAR_CACHE_SECONDS * 1_000_000_000L) {
            return cached.value();
        }
        int par = twEntryRepository.findCountedByCourse(vers, course).stream()
                .map(e -> TwCardUtils.par(card(e)))
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .min()
                .orElse(TwCardUtils.DEFAULT_PAR);
        pars.put(key, new Par(par, System.nanoTime()));
        return par;
    }

    /**
     * A tournament leaderboard (snap)
     *
     * @param vers        the game version
     * @param index       36..50 = a day of the season, 52..54 = a week's money, 33 = last week's winners, 35 = weekly money leaders
     * @param seasonFirst the season's first day
     * @return the rows, best first
     */
    public List<Row> board(String vers, int index, int seasonFirst) {
        ensureCalendar(vers);
        List<Row> rows = new ArrayList<>();
        if (index >= 36 && index <= 50) {
            for (Round r : day(vers, seasonFirst + index - 36)) {
                rows.add(new Row(r.name(), r.rank(), Math.max(0, 200L * r.rounds() - r.total()), toPar(vers, r), r.id()));
            }
        } else if (index >= 52 && index <= 54) {
            int first = seasonFirst + (index - 52) * 7;
            rows.addAll(money(vers, first, first + 6));
        } else if (index == 33) {
            for (int d = today() - 1; d >= today() - 7; d--) {
                Optional<TwEventEntity> event = twEventRepository.findByVersAndEventDay(vers, d);
                if (event.isEmpty() || !decides(d, event.get().getSeriesStart())) {
                    continue;
                }
                for (Round r : day(vers, d)) {
                    if (r.rank() == 1) {
                        rows.add(new Row(r.name(), rows.size() + 1, listDay(d), toPar(vers, r), r.id()));
                    }
                }
            }
        } else if (index == 35) {
            int first = today() - 7;
            first -= first % 7;
            for (int d = first; d <= today(); d += 7) {
                List<Row> week = money(vers, d, d + 6);
                if (!week.isEmpty()) {
                    Row r = week.getFirst();
                    rows.addFirst(new Row(r.name(), 1, listDay(d), r.text(), r.id()));
                }
            }
        }
        return rows;
    }

    /**
     * Prize money won in events decided between two days
     */
    private List<Row> money(String vers, int first, int last) {
        Map<Long, Long> amounts = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (TwEntryEntity entry : twEntryRepository.findPrizes(vers, first, last)) {
            amounts.merge(entry.getPersona().getId(), entry.getPrize(), Long::sum);
            names.put(entry.getPersona().getId(), entry.getPersona().getPers());
        }
        List<Map.Entry<Long, Long>> sorted = amounts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed().thenComparingLong(Map.Entry::getKey))
                .toList();
        List<Row> rows = new ArrayList<>();
        long previous = -1;
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<Long, Long> r = sorted.get(i);
            if (r.getValue() != previous) {
                rank = i + 1;
            }
            previous = r.getValue();
            rows.add(new Row(names.get(r.getKey()), rank, r.getValue() / 10000, String.format(Locale.US, "$%,d", r.getValue()), r.getKey()));
        }
        return rows;
    }

    private int listDay(int day) {
        return Math.clamp(day - (int) ChronoUnit.DAYS.between(EPOCH, LocalDate.of(2003, 1, 1)), 0, 32766);
    }
}
