package eu.eahub.services.stats;

import eu.eahub.config.TwConfig;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.GameConnectionEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaConnectionEntity;
import eu.eahub.repositories.core.GameConnectionRepository;
import eu.eahub.repositories.core.GameRepository;
import eu.eahub.repositories.core.PersonaConnectionRepository;
import eu.eahub.repositories.core.PersonaRepository;
import eu.eahub.repositories.stats.TwEntryRepository;
import eu.eahub.repositories.stats.TwEventRepository;
import eu.eahub.repositories.stats.TwGameReportRepository;
import eu.eahub.repositories.stats.TwGolferRepository;
import eu.eahub.repositories.stats.TwPersonaStatsRepository;
import eu.eahub.services.TwTestConfig;
import eu.eahub.steps.SocketWriter;
import eu.eahub.utils.TwCardUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static eu.eahub.services.server.GameServerService.PS2_TW04;
import static eu.eahub.services.stats.TwTournamentServiceTest.card;
import static eu.eahub.services.stats.TwTournamentServiceTest.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(TwStatsService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TwNewsServiceTest {

    // A Tuesday, so yesterday is a single-day event rather than a weekend round
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TwEventRepository twEventRepository;
    @Autowired
    private TwEntryRepository twEntryRepository;
    @Autowired
    private TwPersonaStatsRepository twPersonaStatsRepository;
    @Autowired
    private TwGameReportRepository twGameReportRepository;
    @Autowired
    private TwGolferRepository twGolferRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private GameConnectionRepository gameConnectionRepository;
    @Autowired
    private PersonaConnectionRepository personaConnectionRepository;
    @Autowired
    private PersonaRepository personaRepository;
    @Autowired
    private TwStatsService twStatsService;
    private TwTournamentService tour;
    private TwNewsService news;

    @BeforeEach
    void setup() {
        TwTestConfig.reset(jdbc, "Alice", "Bob", "Charlie");
        tour = tournament(CLOCK);
        news = news(tour, CLOCK);
    }

    private TwTournamentService tournament(Clock clock) {
        return new TwTournamentService(twEventRepository, twEntryRepository, twPersonaStatsRepository, twStatsService,
                twGolferRepository, mock(SocketWriter.class), config(clock));
    }

    private TwNewsService news(TwTournamentService tournament, Clock clock) {
        return new TwNewsService(twGameReportRepository, twEntryRepository, tournament, config(clock));
    }

    private void play(TwTournamentService service, long persona, int strokes) {
        Map<String, String> entry = service.start(PS2_TW04, personaRepository.findById(persona).orElseThrow());
        String result = service.receive(PS2_TW04, personaRepository.findById(persona).orElseThrow(), card(entry.get("TKEY"), service.today(), strokes));
        assertTrue(result.contains("recorded"), result);
    }

    /**
     * A head-to-head Stroke Play match between Alice (host) and Bob two hours ago, reported with these fields
     */
    private void match(Integer course, Map<String, String> fields) {
        GameEntity game = new GameEntity();
        game.setVers(PS2_TW04);
        game.setName("Stroke.T.East");
        game.setParams((course == null ? "" : course) + ",1");
        game.setSysflags("0");
        game.setStarted(true);
        game.setStartTime(LocalDateTime.ofInstant(CLOCK.instant(), ZoneId.systemDefault()).minusHours(2));
        gameRepository.save(game);
        for (long personaId : new long[]{1, 2}) {
            PersonaConnectionEntity personaConnection = new PersonaConnectionEntity();
            personaConnection.setPersona(personaRepository.findById(personaId).orElseThrow());
            personaConnection.setVers(PS2_TW04);
            personaConnection.setAddress("/192.0.2." + personaId + ":1000");
            personaConnection.setStartTime(game.getStartTime());
            personaConnectionRepository.save(personaConnection);
            GameConnectionEntity gameConnection = new GameConnectionEntity();
            gameConnection.setGame(game);
            gameConnection.setPersonaConnection(personaConnection);
            gameConnection.setHost(personaId == 1);
            gameConnection.setStartTime(game.getStartTime());
            gameConnectionRepository.save(gameConnection);
        }
        Map<String, String> report = new HashMap<>(fields);
        report.put("WHEN", game.getStartTime().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy.M.d H:mm:ss")));
        SocketWrapper reporter = new SocketWrapper();
        reporter.setPersonaEntity(personaRepository.findById(1L).orElseThrow());
        assertEquals(TwStatsService.Receipt.SAVED, twStatsService.rank(reporter, report, null).receipt());
    }

    private static Map<String, String> sides(String... pairs) {
        Map<String, String> f = new HashMap<>();
        for (int side = 0; side < 2; side++) {
            f.put("DONE" + side, "1");
            f.put("QUIT" + side, "0");
            f.put("HOLES" + side, "18");
            f.put("STROKES" + side, "72");
            f.put("SCORE" + side, "0");
        }
        for (int i = 0; i < pairs.length; i += 2) {
            f.put(pairs[i], pairs[i + 1]);
        }
        return f;
    }

    @Test
    void quietServerProducesNoSectionsBeyondTodaysEvent() {
        String digest = news.digest(PS2_TW04);
        TwTournamentService.Event event = tour.event(PS2_TW04, tour.today()).orElseThrow();
        assertTrue(digest.startsWith("TODAY: " + event.name()), digest);
        assertTrue(digest.contains("Nobody has posted a score yet."), digest);
        assertFalse(digest.contains("YESTERDAY"));
        assertFalse(digest.contains("THIS WEEK"));
    }

    @Test
    void digestNamesLeaderYesterdaysWinnerRecordsAndBusiestPlayer() {
        TwTournamentService yesterday = tournament(Clock.offset(CLOCK, Duration.ofDays(-1)));
        play(yesterday, 3, 71);
        play(tour, 1, 70);
        play(tour, 2, 74);
        int course = tour.event(PS2_TW04, tour.today()).orElseThrow().course();
        // The head-to-head card names today's course: 68 + 4 birdies bounds par at 72
        match(course, sides("STROKES0", "68", "BIRD0", "4", "PARS0", "14", "LDRV0", "400",
                "PUTTS0", "1025", "STROKES1", "75", "ACES1", "1", "PARS1", "17"));
        String digest = news.digest(PS2_TW04);
        String text = digest.replaceAll("\\s+", " ");
        String name = TwCardUtils.courseName(course);
        String yesterdayEvent = yesterday.event(PS2_TW04, yesterday.today()).orElseThrow().name();
        assertTrue(text.contains("Leader: Alice 70 (-2), 2 in the field"), digest);
        assertTrue(text.contains("YESTERDAY: " + yesterdayEvent), digest);
        assertTrue(text.contains("Won by Charlie with 71 (-2)"), digest);
        assertTrue(text.contains("NEW RECORDS THIS WEEK"), digest);
        assertTrue(text.contains("Lowest round: Alice, 68 (-4)"), digest);
        assertTrue(text.contains("Longest drive: Alice, 400 yd"), digest);
        assertTrue(text.contains("Longest putt holed: Charlie, 20 ft"), digest);
        assertTrue(text.contains("Course record, " + name + ": Alice 68"), digest);
        assertTrue(text.contains("Hole in one: Bob at " + name), digest);
        assertTrue(text.contains("THIS WEEK: 5 rounds played"), digest);
        assertTrue(text.contains("Most active: Alice (2)"), digest);
        assertTrue(text.contains("Best round: Alice 68 (-4) at " + name), digest);
        for (String line : digest.split("\n")) {
            assertTrue(line.length() <= 40, line);
        }
    }

    @Test
    void majorDaysReportTheRunningTotalAndTheFinalDayItsWinner() {
        Clock thursday = Clock.fixed(Instant.parse("2026-06-18T12:00:00Z"), ZoneOffset.UTC);
        Clock friday = Clock.offset(thursday, Duration.ofDays(1));
        for (Clock c : List.of(thursday, friday)) {
            TwTournamentService t = tournament(c);
            t.ensureCalendar(PS2_TW04);
            play(t, 1, 70);
            play(t, 2, 72);
        }
        String text = news(tournament(friday), friday).digest(PS2_TW04).replaceAll("\\s+", " ");
        assertTrue(text.contains("TODAY: US Major Championship R2"), text);
        assertTrue(text.contains("Leader: Alice 140 (-4) after 2 rounds, 2 in the field"), text);
        assertTrue(text.contains("YESTERDAY: US Major Championship R1"), text);
        assertTrue(text.contains("Led by Alice 70 (-2) after 1 round, 2 in the field"), text);
    }

    @Test
    void newsPutsTheOperatorTextFirst() {
        TwConfig config = config(CLOCK);
        when(config.getNews()).thenReturn("Server maintenance on Sunday.");
        when(config.getNewsFile()).thenReturn("");
        when(config.isAutoNews()).thenReturn(true);
        String text = new TwNewsService(twGameReportRepository, twEntryRepository, tour, config).news(PS2_TW04);
        assertTrue(text.startsWith("Server maintenance on Sunday.\n\nTODAY: "), text);
        assertTrue(text.endsWith("\n"));
    }

    @Test
    void conditionsAndWrappingMatchTheReference() {
        assertEquals("Standard conditions", TwNewsService.conditions(0));
        assertEquals("White tees, long rough, fast greens", TwNewsService.conditions(1 << 3 | 1 << 15 | 1 << 12));
        StringBuilder out = new StringBuilder();
        TwNewsService.wrap("Course record, Tiger's Dream 18: SomeoneLongNamed 61", "  ", out);
        assertEquals("Course record, Tiger's Dream 18:\n  SomeoneLongNamed 61\n", out.toString());
        out.setLength(0);
        TwNewsService.wrap("", "  ", out);
        assertEquals("\n", out.toString());
    }
}
