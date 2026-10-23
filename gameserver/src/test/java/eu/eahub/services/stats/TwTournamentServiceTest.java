package eu.eahub.services.stats;

import eu.eahub.config.TwConfig;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.entities.stats.TwEntryEntity;
import eu.eahub.entities.stats.TwPersonaStatsEntity;
import eu.eahub.repositories.core.PersonaRepository;
import eu.eahub.repositories.stats.TwEntryRepository;
import eu.eahub.repositories.stats.TwEventRepository;
import eu.eahub.repositories.stats.TwGolferRepository;
import eu.eahub.repositories.stats.TwPersonaStatsRepository;
import eu.eahub.services.TwTestConfig;
import eu.eahub.steps.SocketWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static eu.eahub.services.server.GameServerService.PS2_TW04;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(TwStatsService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TwTournamentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TwEventRepository twEventRepository;
    @Autowired
    private TwEntryRepository twEntryRepository;
    @Autowired
    private TwPersonaStatsRepository twPersonaStatsRepository;
    @Autowired
    private TwGolferRepository twGolferRepository;
    @Autowired
    private PersonaRepository personaRepository;
    @Autowired
    private TwStatsService twStatsService;
    private TwTournamentService tour;

    @BeforeEach
    void setup() {
        TwTestConfig.reset(jdbc, "Alice", "Bob", "Charlie");
        tour = tournament(CLOCK);
    }

    static TwConfig config(Clock clock) {
        TwConfig config = mock(TwConfig.class);
        when(config.clock()).thenReturn(clock);
        return config;
    }

    private TwTournamentService tournament(Clock clock) {
        return new TwTournamentService(twEventRepository, twEntryRepository, twPersonaStatsRepository, twStatsService,
                twGolferRepository, mock(SocketWriter.class), config(clock));
    }

    private TwTournamentService on(String date) {
        return tournament(Clock.fixed(Instant.parse(date + "T12:00:00Z"), ZoneOffset.UTC));
    }

    private PersonaEntity p(long id) {
        return personaRepository.findById(id).orElseThrow();
    }

    private TwPersonaStatsEntity career(TwTournamentService t, long id) {
        t.ensureCalendar(PS2_TW04);
        return twStatsService.getOrCreateStats(p(id), PS2_TW04);
    }

    static int day(String date) {
        return (int) ChronoUnit.DAYS.between(TwTournamentService.EPOCH, LocalDate.parse(date));
    }

    static String card(String key, int day, int strokes) {
        int[] f = {0, 1, 0, 1, 0, 5, strokes, 30, 18, 0, 2, 0, 9, 20, 14, 7, 321, 0, 16, 0, 0, 0, 1};
        ByteBuffer b = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
        for (int v : f) {
            b.putInt(v);
        }
        b.put(HexFormat.of().parseHex(key.replace("$", "")));
        b.putShort((short) day);
        b.put((byte) 0);
        return HexFormat.of().formatHex(b.array());
    }

    private String play(TwTournamentService t, long persona, int strokes) {
        Map<String, String> entry = t.start(PS2_TW04, p(persona));
        return t.receive(PS2_TW04, p(persona), card(entry.get("TKEY"), t.today(), strokes));
    }

    @Test
    void calendarAndResultWidthsMatchIndependentReferenceFixtures() throws Exception {
        TwTournamentService.Event event = new TwTournamentService.Event(46314, "Test Open", 5, 2500000, 0x42008);
        String encoded = TwTournamentService.list(List.of(TwTournamentService.entry(event)));
        try (InputStream input = getClass().getResourceAsStream("/tw04-tournament-reference.txt")) {
            String[] lines = new String(input.readAllBytes(), StandardCharsets.US_ASCII).strip().split("\\R");
            assertEquals(lines[0], encoded);
            byte[] b = new byte[12];
            ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putShort(4, (short) 46314).putInt(8, 1);
            b[6] = 70;
            b[7] = 72;
            assertEquals(lines[1], TwTournamentService.list(List.of(new TwTournamentService.Entry("Alice", b))));
        }
    }

    @Test
    void serverOwnsCalendarEntryIdentityAndDateAndResultsSurviveRestart() {
        tour.ensureCalendar(PS2_TW04);
        int day = tour.today();
        TwTournamentService.Event event = tour.event(PS2_TW04, day).orElseThrow();
        long count = twEventRepository.count();
        assertEquals("00", tour.calendar(PS2_TW04, 65535, 100000));
        assertEquals(count, twEventRepository.count());
        assertTrue(tour.calendar(PS2_TW04, -1, 1).contains(event.name()));
        assertTrue(twEventRepository.findAll().stream().noneMatch(e -> e.getCourse() == 22)); // never Tiger's Dream
        Map<String, String> entry = tour.start(PS2_TW04, p(1));
        String data = card(entry.get("TKEY"), day + 100, 70);
        assertTrue(tour.receive(PS2_TW04, p(2), data).contains("not started"));
        tour = tournament(CLOCK);
        assertTrue(tour.receive(PS2_TW04, p(1), data).contains("recorded"));
        assertEquals(day, tour.day(PS2_TW04, day).getFirst().day());
        assertTrue(tour.day(PS2_TW04, day + 100).isEmpty());
        assertEquals(event.course(), tour.day(PS2_TW04, day).getFirst().course());
        assertTrue(tour.receive(PS2_TW04, p(1), card(entry.get("TKEY"), day, 50)).contains("already"));
        assertEquals(70, tour.day(PS2_TW04, day).getFirst().strokes());
        assertEquals("00", tour.results(PS2_TW04, p(2), day, 1));
        assertTrue(tour.results(PS2_TW04, p(1), day, 1).contains("Alice"));
    }

    @Test
    void majorsRunThursdayToTheirSundayWithPoolsPursesAndStarIcons() {
        // 2026: THE PLAYERS Mar 12-15, Southern Apr 9-12, PGA May 14-17, US Jun 18-21, UK Jul 16-19
        String[][] expected = {
                {"2026-03-12", "2026-03-15", "THE PLAYERS Championship", "2", "12000000"},
                {"2026-04-09", "2026-04-12", "Southern Major Championship", "8", "15000000"},
                {"2026-05-14", "2026-05-17", "PGA TOUR Major Championship", "16", "15000000"},
                {"2026-06-18", "2026-06-21", "US Major Championship", "20", "15000000"},
                {"2026-07-16", "2026-07-19", "UK Major Championship", "15", "15000000"}
        };
        for (String[] m : expected) {
            int first = day(m[0]);
            assertEquals(first + 3, day(m[1]));
            assertNull(TwTournamentService.plan(first - 1).series());
            assertNull(TwTournamentService.plan(first + 4).series());
            for (int r = 0; r < 4; r++) {
                TwTournamentService.Event e = TwTournamentService.plan(first + r);
                assertEquals(m[2] + " R" + (r + 1), e.name());
                assertEquals(first, e.series());
                assertEquals(Integer.parseInt(m[3]), e.course());
                assertEquals(Long.parseLong(m[4]), e.purse());
                assertEquals(4, TwTournamentService.entry(e).data()[15]);
            }
        }
        assertEquals(6, TwTournamentService.plan(day("2027-07-15")).course());
    }

    @Test
    void majorIsAFourRoundTotalPaidOnceOnItsFinalStandings() {
        String[] days = {"2026-06-18", "2026-06-19", "2026-06-20", "2026-06-21"};
        int[][] strokes = {{70, 70, 70, 70}, {72, 72, 72, 72}, {66, 0, 66, 66}};
        String last = null;
        for (int d = 0; d < 4; d++) {
            TwTournamentService t = on(days[d]);
            t.ensureCalendar(PS2_TW04);
            for (int player = 0; player < 3; player++) {
                if (strokes[player][d] != 0) {
                    last = play(t, player + 1, strokes[player][d]);
                }
            }
            if (d == 1) {
                List<TwTournamentService.Round> board = t.day(PS2_TW04, t.today());
                assertEquals(List.of("Alice", "Bob"), board.stream().map(TwTournamentService.Round::name).toList());
                assertEquals(140, board.getFirst().total());
                assertEquals(2, board.getFirst().rounds());
                assertEquals(0, career(t, 1).getEarnings()); // nothing is paid mid-event
            }
        }
        assertTrue(last.startsWith("Round 4 of 4: 66 is recorded."), last);
        assertTrue(last.contains("missed round"), last);
        TwTournamentService monday = on("2026-06-22");
        List<TwTournamentService.Round> finalStandings = monday.day(PS2_TW04, day("2026-06-21"));
        assertEquals(List.of(280, 288), finalStandings.stream().map(TwTournamentService.Round::total).toList());
        TwPersonaStatsEntity alice = career(monday, 1);
        assertEquals(1, alice.getEvents());
        assertEquals(1, alice.getEventWins());
        assertEquals(TwTournamentService.payout(15_000_000, 1), alice.getEarnings());
        assertEquals(TwTournamentService.payout(15_000_000, 2), career(monday, 2).getEarnings());
        TwPersonaStatsEntity charlie = career(monday, 3);
        assertEquals(1, charlie.getEvents());
        assertEquals(0, charlie.getEventWins());
        assertEquals(0, charlie.getEarnings());
        List<TwTournamentService.Row> board = monday.board(PS2_TW04, 42, day("2026-06-21") - 6);
        assertEquals("Alice", board.getFirst().name());
        assertEquals(800 - 280, board.getFirst().sort());
        List<TwTournamentService.Row> winners = monday.board(PS2_TW04, 33, 0);
        assertEquals(1, winners.size()); // only the final round names a winner
        assertEquals("Alice", winners.getFirst().name());
        // Settling again pays nothing twice
        tournament(Clock.offset(Clock.fixed(Instant.parse("2026-06-22T12:00:00Z"), ZoneOffset.UTC), Duration.ofDays(1))).ensureCalendar(PS2_TW04);
        assertEquals(TwTournamentService.payout(15_000_000, 1), career(monday, 1).getEarnings());
    }

    @Test
    void majorAllowsOneStartADayAndCountsOnlyThatRound() {
        TwTournamentService t = on("2026-06-18");
        t.ensureCalendar(PS2_TW04);
        int day = t.today();
        Map<String, String> first = t.start(PS2_TW04, p(1));
        assertFalse(first.get("DATA").isEmpty());
        Map<String, String> again = t.start(PS2_TW04, p(1));
        assertEquals("$" + "00".repeat(16), again.get("TKEY"));
        assertEquals("", again.get("DATA"));
        String receipt = t.receive(PS2_TW04, p(1), card(first.get("TKEY"), day, 70));
        assertTrue(receipt.contains("That is your round for today"), receipt);
        assertEquals("", t.start(PS2_TW04, p(1)).get("DATA")); // still refused once played

        // Quitting the day's round is final; a later round (from before this rule) is ignored
        Map<String, String> bob = t.start(PS2_TW04, p(2));
        String quit = card(bob.get("TKEY"), day, 40);
        quit = quit.substring(0, 16) + "01000000" + quit.substring(24);
        assertTrue(t.receive(PS2_TW04, p(2), quit).contains("out of this event"));
        TwEntryEntity later = new TwEntryEntity();
        later.setToken("ff".repeat(16));
        later.setPersona(p(2));
        later.setEvent(twEventRepository.findByVersAndEventDay(PS2_TW04, day).orElseThrow());
        later.setStartTime(LocalDateTime.of(2026, 6, 18, 12, 1)); // a minute after the first start
        twEntryRepository.save(later);
        t.receive(PS2_TW04, p(2), card("ff".repeat(16), day, 60));
        assertEquals(List.of("Alice"), t.day(PS2_TW04, day).stream().map(TwTournamentService.Round::name).toList());

        // Single-day events still take every attempt
        TwTournamentService daily = on("2026-06-22");
        daily.ensureCalendar(PS2_TW04);
        daily.start(PS2_TW04, p(1));
        assertFalse(daily.start(PS2_TW04, p(1)).get("DATA").isEmpty());
    }

    @Test
    void weekendsOutsideMajorsAreThreeRoundEventsWithOneStartADay() {
        // 2026-10-23..25 is an ordinary weekend; the PLAYERS/major weekends keep four rounds
        int friday = day("2026-10-23");
        TwTournamentService.Event fri = TwTournamentService.plan(friday);
        assertEquals(friday, fri.series());
        assertEquals(friday + 2, fri.last());
        assertTrue(fri.name().endsWith(" R1"), fri.name());
        for (int d = 1; d < 3; d++) {
            TwTournamentService.Event e = TwTournamentService.plan(friday + d);
            assertEquals(fri.series(), e.series());
            assertEquals(fri.course(), e.course());
            assertEquals(fri.conditions(), e.conditions());
            assertEquals(fri.purse(), e.purse());
            assertTrue(e.name().endsWith(" R" + (d + 1)), e.name());
        }
        assertNull(TwTournamentService.plan(friday - 1).series()); // Thursday
        assertNull(TwTournamentService.plan(friday + 3).series()); // Monday
        assertEquals(day("2026-06-18"), TwTournamentService.plan(day("2026-06-20")).series());
        assertNotEquals(4, TwTournamentService.entry(fri).data()[15]); // no major star

        String[] days = {"2026-10-23", "2026-10-24", "2026-10-25"};
        int[][] strokes = {{70, 70, 70}, {68, 0, 68}};
        String receipt = null;
        for (int d = 0; d < 3; d++) {
            TwTournamentService t = on(days[d]);
            t.ensureCalendar(PS2_TW04);
            for (int player = 0; player < 2; player++) {
                if (strokes[player][d] == 0) {
                    continue;
                }
                receipt = play(t, player + 1, strokes[player][d]);
                if (d == 0 && player == 0) {
                    assertTrue(receipt.startsWith("Round 1 of 3: 70 is recorded."), receipt);
                    assertEquals("", t.start(PS2_TW04, p(1)).get("DATA"));
                }
            }
        }
        assertTrue(receipt.startsWith("Round 3 of 3: 68 is recorded."), receipt);
        TwTournamentService monday = on("2026-10-26");
        List<TwTournamentService.Round> standings = monday.day(PS2_TW04, day("2026-10-25"));
        assertEquals(List.of("Alice"), standings.stream().map(TwTournamentService.Round::name).toList());
        assertEquals(210, standings.getFirst().total());
        assertEquals(TwTournamentService.payout(fri.purse(), 1), career(monday, 1).getEarnings());
        assertEquals(1, career(monday, 1).getEvents());
    }

    @Test
    void bestReplayTiesAndClosedDayEarningsFeedResumeAndBoards() {
        int day = tour.today();
        Map<String, String> a = tour.start(PS2_TW04, p(1));
        Map<String, String> b = tour.start(PS2_TW04, p(2));
        tour.receive(PS2_TW04, p(1), card(a.get("TKEY"), day, 72));
        tour.receive(PS2_TW04, p(2), card(b.get("TKEY"), day, 70));
        Map<String, String> replay = tour.start(PS2_TW04, p(1));
        tour.receive(PS2_TW04, p(1), card(replay.get("TKEY"), day, 70));
        assertEquals(2, tour.day(PS2_TW04, day).size());
        assertEquals(1, tour.day(PS2_TW04, day).getLast().rank());
        assertEquals(0, career(tour, 1).getEarnings());
        tour = tournament(Clock.offset(CLOCK, Duration.ofDays(1)));
        TwPersonaStatsEntity career = career(tour, 1);
        assertEquals(1, career.getEvents());
        assertEquals(1, career.getEventWins());
        assertTrue(career.getEarnings() > 0);
        assertEquals(1, tour.earningsRank(PS2_TW04, p(1)));
        assertEquals(career(tour, 1).getEarnings(), career(tour, 2).getEarnings());
        assertEquals(2, tour.board(PS2_TW04, 33, tour.today() - 7).size());
        assertFalse(tour.board(PS2_TW04, 35, tour.today() - 7).isEmpty());
        List<TwTournamentService.Row> daily = tour.board(PS2_TW04, 42, tour.today() - 7);
        assertEquals(2, daily.size());
        assertEquals(130, daily.getFirst().sort());
        long[] v = twStatsService.standing(p(1), PS2_TW04).values();
        assertEquals(1, v[26]);
        assertEquals(1, v[27]);
        assertEquals(career.getEarnings() / 100, v[50]);
        assertEquals(70, v[39]);
        assertEquals(71, v[41]);
        assertEquals(4, v[31]); // both completed attempts contribute golf statistics
    }

    @Test
    void lateScorecardForASettledDayIsStillPaid() {
        int day = tour.today();
        Map<String, String> late = tour.start(PS2_TW04, p(2));
        play(tour, 1, 72);
        TwTournamentService tomorrow = tournament(Clock.offset(CLOCK, Duration.ofDays(1)));
        assertEquals(TwTournamentService.payout(tour.event(PS2_TW04, day).orElseThrow().purse(), 1), career(tomorrow, 1).getEarnings());
        // Bob's round from yesterday arrives after the day was settled, within the entry's 48 hours, and wins
        assertTrue(tomorrow.receive(PS2_TW04, p(2), card(late.get("TKEY"), day, 68)).contains("recorded"));
        long purse = tour.event(PS2_TW04, day).orElseThrow().purse();
        assertEquals(TwTournamentService.payout(purse, 1), career(tomorrow, 2).getEarnings());
        assertEquals(TwTournamentService.payout(purse, 2), career(tomorrow, 1).getEarnings());
        assertEquals(0, career(tomorrow, 1).getEventWins());
        assertEquals(1, career(tomorrow, 2).getEventWins());
    }

    @Test
    void incompleteMalformedAndExpiredTournamentReportsCannotRank() {
        int day = tour.today();
        Map<String, String> start = tour.start(PS2_TW04, p(1));
        String good = card(start.get("TKEY"), day, 70);
        assertTrue(tour.receive(PS2_TW04, p(1), "g".repeat(256)).contains("could not"));
        byte[] partial = HexFormat.of().parseHex(good);
        ByteBuffer.wrap(partial).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 0);
        assertTrue(tour.receive(PS2_TW04, p(1), HexFormat.of().formatHex(partial)).contains("incomplete"));
        assertTrue(tour.day(PS2_TW04, day).isEmpty());
        start = tour.start(PS2_TW04, p(1));
        good = card(start.get("TKEY"), day, 70);
        tour = tournament(Clock.offset(CLOCK, Duration.ofDays(3)));
        assertTrue(tour.receive(PS2_TW04, p(1), good).contains("expired"));
        byte[] ace = HexFormat.of().parseHex(good);
        ByteBuffer.wrap(ace).order(ByteOrder.LITTLE_ENDIAN).putInt(44, 1).putInt(72, 15);
        assertEquals(1, TwTournamentService.parse(HexFormat.of().formatHex(ace)).fields().get("ACES"));
    }
}
