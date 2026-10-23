package eu.eahub.services.stats;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DataJpaTest
@Import(TwStatsService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TwStatsRebuildTest {

    // A Tuesday, so yesterday is a one-day event that is settled today
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
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
    private int matches;

    @BeforeEach
    void setup() {
        TwTestConfig.reset(jdbc, "Alice", "Bob", "Charlie");
    }

    private TwTournamentService tournament(Clock clock) {
        return new TwTournamentService(twEventRepository, twEntryRepository, twPersonaStatsRepository, twStatsService, twGolferRepository,
                mock(SocketWriter.class), config(clock));
    }

    private void play(TwTournamentService service, long persona, int strokes) {
        Map<String, String> entry = service.start(PS2_TW04, personaRepository.findById(persona).orElseThrow());
        String result = service.receive(PS2_TW04, personaRepository.findById(persona).orElseThrow(), card(entry.get("TKEY"), service.today(), strokes));
        assertTrue(result.contains("recorded"), result);
    }

    /**
     * A head-to-head match in this room between host and guest, reported with these fields
     */
    private void match(String room, long host, long guest, Map<String, String> fields) {
        GameEntity game = new GameEntity();
        game.setVers(PS2_TW04);
        game.setName(room);
        game.setParams("2,1");
        game.setSysflags("0");
        game.setStarted(true);
        game.setStartTime(LocalDateTime.ofInstant(CLOCK.instant(), ZoneId.systemDefault()).minusHours(5).plusMinutes(++matches));
        gameRepository.save(game);
        for (long personaId : new long[]{host, guest}) {
            PersonaConnectionEntity personaConnection = new PersonaConnectionEntity();
            personaConnection.setPersona(personaRepository.findById(personaId).orElseThrow());
            personaConnection.setVers(PS2_TW04);
            personaConnection.setAddress("/192.0.2." + personaId + ":1000");
            personaConnection.setStartTime(game.getStartTime());
            personaConnectionRepository.save(personaConnection);
            GameConnectionEntity gameConnection = new GameConnectionEntity();
            gameConnection.setGame(game);
            gameConnection.setPersonaConnection(personaConnection);
            gameConnection.setHost(personaId == host);
            gameConnection.setStartTime(game.getStartTime());
            gameConnectionRepository.save(gameConnection);
        }
        Map<String, String> report = new HashMap<>();
        for (int side = 0; side < 2; side++) {
            report.put("DONE" + side, "1");
            report.put("QUIT" + side, "0");
            report.put("HOLES" + side, "18");
            report.put("STROKES" + side, "72");
            report.put("SCORE" + side, "0");
            report.put("BIRD" + side, "3");
            report.put("PUTTS" + side, "1200");
            report.put("GIR" + side, "10");
        }
        report.putAll(fields);
        report.put("WHEN", game.getStartTime().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy.M.d H:mm:ss")));
        SocketWrapper reporter = new SocketWrapper();
        reporter.setPersonaEntity(personaRepository.findById(guest).orElseThrow());
        assertEquals(TwStatsService.Receipt.SAVED, twStatsService.rank(reporter, report, null).receipt());
    }

    private List<Map<String, Object>> statistics() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM stats.TW_PERSONA_STATS ORDER BY PERSONA_ID");
        rows.forEach(row -> row.remove("ID"));
        return rows;
    }

    @Test
    void rebuildingReplaysMatchesRoundsAndClosedDaysToTheSameStatistics() {
        match("Match.T.East", 1, 2, Map.of("SCORE0", "3", "SCORE1", "-3"));
        match("Stroke.T.East", 2, 3, Map.of("STROKES0", "70", "STROKES1", "70"));
        match("Stroke.T.East", 1, 3, Map.of("QUIT1", "1", "DONE1", "0", "HOLES1", "9"));
        match("Match.T.East", 3, 1, Map.of("DONE0", "0", "DONE1", "0", "HOLES0", "0", "HOLES1", "0"));
        TwTournamentService yesterday = tournament(Clock.offset(CLOCK, Duration.ofDays(-1)));
        yesterday.ensureCalendar(PS2_TW04);
        play(yesterday, 3, 71);
        play(yesterday, 1, 73);
        play(yesterday, 1, 69);
        TwTournamentService today = tournament(CLOCK);
        play(today, 2, 74);
        // Yesterday is settled once today's calendar is used
        today.ensureCalendar(PS2_TW04);
        List<Map<String, Object>> live = statistics();
        assertEquals(3, live.size());
        assertEquals(1, ((Number) live.getFirst().get("EVENT_WINS")).intValue());
        assertTrue(((Number) live.getFirst().get("EARNINGS")).longValue() > 0);
        assertEquals(1, ((Number) live.getFirst().get("MATCH_WINS")).intValue());

        TwStatsRebuild rebuild = new TwStatsRebuild(twGameReportRepository, twPersonaStatsRepository, twEntryRepository, twEventRepository, today);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> rebuild.rebuild(PS2_TW04));
        assertEquals(live, statistics());
        // Rebuilding again changes nothing
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> rebuild.rebuild(PS2_TW04));
        assertEquals(live, statistics());
    }
}
