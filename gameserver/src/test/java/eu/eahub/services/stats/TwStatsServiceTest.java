package eu.eahub.services.stats;

import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.GameConnectionEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaConnectionEntity;
import eu.eahub.repositories.core.GameConnectionRepository;
import eu.eahub.repositories.core.GameRepository;
import eu.eahub.repositories.core.PersonaConnectionRepository;
import eu.eahub.repositories.core.PersonaRepository;
import eu.eahub.repositories.stats.TwGameReportRepository;
import eu.eahub.repositories.stats.TwPersonaStatsRepository;
import eu.eahub.services.TwTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static eu.eahub.services.server.GameServerService.PS2_TW04;
import static eu.eahub.services.stats.TwStatsService.Receipt.DUPLICATE;
import static eu.eahub.services.stats.TwStatsService.Receipt.INVALID;
import static eu.eahub.services.stats.TwStatsService.Receipt.SAVED;
import static eu.eahub.services.stats.TwStatsService.Receipt.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TwStatsService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TwStatsServiceTest {

    private static final DateTimeFormatter WHEN_FORMAT = DateTimeFormatter.ofPattern("yyyy.M.d H:mm:ss");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TwGameReportRepository twGameReportRepository;
    @Autowired
    private TwPersonaStatsRepository twPersonaStatsRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private GameConnectionRepository gameConnectionRepository;
    @Autowired
    private PersonaConnectionRepository personaConnectionRepository;
    @Autowired
    private PersonaRepository personaRepository;
    @Autowired
    private TwStatsService stats;
    private GameEntity game;
    private int games;

    @BeforeEach
    void setup() {
        TwTestConfig.reset(jdbc, "Host", "Guest", "Other");
        games = 0;
        game = game("Match.T.East");
    }

    /**
     * A started session between Host and Guest, as GameService.startSession records it; each one starts a minute earlier
     */
    private GameEntity game(String room) {
        GameEntity created = new GameEntity();
        created.setVers(PS2_TW04);
        created.setName(room);
        created.setParams(",1");
        created.setSysflags("0");
        created.setMinsize(2);
        created.setMaxsize(2);
        created.setStarted(true);
        created.setStartTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minusMinutes(games++));
        gameRepository.save(created);
        for (long personaId : new long[]{1, 2}) {
            PersonaConnectionEntity personaConnection = new PersonaConnectionEntity();
            personaConnection.setPersona(personaRepository.findById(personaId).orElseThrow());
            personaConnection.setVers(PS2_TW04);
            personaConnection.setAddress("/192.0.2." + personaId + ":1000");
            personaConnection.setStartTime(created.getStartTime());
            personaConnectionRepository.save(personaConnection);
            GameConnectionEntity gameConnection = new GameConnectionEntity();
            gameConnection.setGame(created);
            gameConnection.setPersonaConnection(personaConnection);
            gameConnection.setHost(personaId == 1);
            gameConnection.setStartTime(created.getStartTime());
            gameConnectionRepository.save(gameConnection);
        }
        return created;
    }

    private SocketWrapper player(long personaId) {
        SocketWrapper wrapper = new SocketWrapper();
        wrapper.setPersonaEntity(personaRepository.findById(personaId).orElseThrow());
        return wrapper;
    }

    private void startedLongAgo(GameEntity session) {
        session.setStartTime(LocalDateTime.of(2000, 1, 1, 0, 0));
        gameRepository.save(session);
    }

    private Map<String, String> report() {
        return report(game);
    }

    private static Map<String, String> report(GameEntity session) {
        Map<String, String> r = new HashMap<>();
        r.put("AUTH", "a".repeat(32));
        r.put("WHEN", session.getStartTime().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).format(WHEN_FORMAT));
        r.put("NAME0", "Host");
        r.put("NAME1", "Host");
        r.put("REPT", "Host");
        for (int s = 0; s < 2; s++) {
            r.put("RANK" + s, "1");
            r.put("DONE" + s, "1");
            r.put("QUIT" + s, "0");
            r.put("HOLES" + s, "18");
            r.put("STROKES" + s, s == 0 ? "73" : "70");
            r.put("SCORE" + s, s == 0 ? "2" : "0");
            r.put("PUTTS" + s, "27");
            r.put("GIR" + s, "9");
            r.put("DRVS" + s, "14");
            r.put("FRWY" + s, "7");
            r.put("BIRD" + s, "2");
            r.put("EAGS" + s, "1");
            r.put("LDRV" + s, "321");
        }
        return r;
    }

    private TwStatsService.Receipt receive(long personaId, Map<String, String> report) {
        return stats.rank(player(personaId), report, null).receipt();
    }

    private TwStatsService.Standing standing(long personaId) {
        return stats.standing(personaRepository.findById(personaId).orElseThrow(), PS2_TW04);
    }

    @Test
    void firstReportWinsAndOnlyPlayersOfTheMatchCanReport() {
        Map<String, String> r = report();
        assertEquals(UNAUTHORIZED, receive(3, r));
        assertEquals(SAVED, receive(2, r));
        r.put("SCORE0", "-5");
        assertEquals(DUPLICATE, receive(1, r));
        assertEquals(DUPLICATE, receive(1, r));
        TwStatsService.Standing host = standing(1);
        TwStatsService.Standing guest = standing(2);
        assertEquals(1, host.values()[7]);
        assertEquals(1, guest.values()[8]);
        assertEquals(2, host.points());
        assertEquals("Guest", guest.name());
        assertEquals(2, twGameReportRepository.count());
        assertTrue(twGameReportRepository.findAll().stream().allMatch(report -> report.getRnk() == 1));
    }

    @Test
    void reportForAnotherGameOrUnknownTimeIsRefused() {
        GameEntity other = game("Match.T.East");
        assertEquals(UNAUTHORIZED, stats.rank(player(1), report(), other.getId()).receipt());
        Map<String, String> r = report();
        r.put("WHEN", "2001.1.1 0:00:00");
        assertEquals(UNAUTHORIZED, receive(1, r));
        r.put("WHEN", "not a date");
        assertEquals(UNAUTHORIZED, receive(1, r));
        assertEquals(SAVED, stats.rank(player(1), report(), game.getId()).receipt());
    }

    @Test
    void capturedQuitShapeAwardsForfeitAndDropWithoutFabricatedGolfAverages() {
        Map<String, String> r = report();
        r.put("DONE0", "0");
        r.put("DONE1", "0");
        r.put("QUIT1", "1");
        r.put("HOLES0", "2");
        r.put("HOLES1", "2");
        r.put("STROKES0", "0");
        r.put("STROKES1", "0");
        r.put("COMP0", "1");
        r.put("COMP1", "0");
        assertEquals(SAVED, receive(2, r));
        TwStatsService.Standing host = standing(1);
        TwStatsService.Standing guest = standing(2);
        assertEquals(1, host.values()[7]);
        assertEquals(1, host.values()[20]);
        assertEquals(0, host.values()[16]);
        assertEquals(1, guest.values()[8]);
        assertEquals(1, guest.values()[16]);
        assertEquals(0, guest.values()[20]);
        assertEquals(-1, guest.values()[14]);
        assertEquals(0, host.values()[39]);
        assertEquals(0, host.values()[37]);
        assertEquals(2, stats.board(PS2_TW04, 13, 0, 25, "").size());
        assertTrue(stats.board(PS2_TW04, 14, 0, 25, "").isEmpty());
    }

    @Test
    void quitBeforeAnyHoleIsNotAForfeit() {
        Map<String, String> r = report();
        r.put("DONE0", "0");
        r.put("DONE1", "0");
        r.put("QUIT1", "1");
        r.put("HOLES0", "0");
        r.put("HOLES1", "0");
        r.put("STROKES0", "0");
        r.put("STROKES1", "0");
        assertEquals(SAVED, receive(1, r));
        TwStatsService.Standing host = standing(1);
        TwStatsService.Standing guest = standing(2);
        assertEquals(0, host.values()[7]);
        assertEquals(0, host.points());
        assertEquals(0, guest.values()[8]);
        assertEquals(0, guest.values()[16]);
        assertTrue(stats.board(PS2_TW04, 13, 0, 25, "").isEmpty());
    }

    @Test
    void completedStrokeRoundUsesStrokesAndComputesRatios() {
        GameEntity stroke = game("Stroke.T.East");
        assertEquals(SAVED, receive(1, report(stroke)));
        TwStatsService.Standing guest = standing(2);
        assertEquals(1, guest.values()[10]);
        assertEquals(50, guest.values()[42]);
        assertEquals(150, guest.values()[43]);
        assertEquals(50, guest.values()[46]);
        assertEquals(18, guest.values()[44]);
        assertEquals(70, guest.values()[39]);
        assertEquals(70, guest.values()[41]);
        assertEquals(321, guest.values()[37]);
        assertEquals(2, stats.board(PS2_TW04, 14, 0, 25, "").size());
        assertTrue(stats.board(PS2_TW04, 13, 0, 25, "").isEmpty());
    }

    @Test
    void tiesAndAbandonedMatchesDoNotBecomeWinsAndWeeklyWindowUsesStartTime() {
        Map<String, String> r = report();
        r.put("SCORE0", "0");
        assertEquals(SAVED, receive(1, r));
        assertEquals(1, standing(1).values()[9]);
        assertEquals(1, standing(2).rank());
        assertEquals(2, stats.board(PS2_TW04, 32, 0, 25, "").size());
        startedLongAgo(game);
        assertTrue(stats.board(PS2_TW04, 32, 0, 25, "").isEmpty());
        assertEquals(2, stats.board(PS2_TW04, 13, 0, 25, "").size());
        GameEntity abandoned = game("Match.T.East");
        r = report(abandoned);
        r.put("DONE0", "0");
        r.put("QUIT0", "1");
        r.put("QUIT1", "1");
        assertEquals(SAVED, receive(1, r));
        assertEquals(1, standing(1).values()[16]);
        assertEquals(1, standing(1).points());
    }

    @Test
    void invalidReportsDoNotConsumeTheMatchAndStreakFollowsResults() {
        Map<String, String> r = report();
        r.remove("DONE0");
        assertEquals(INVALID, receive(1, r));
        r = report();
        r.put("HOLES0", "900");
        assertEquals(INVALID, receive(1, r));
        assertEquals(SAVED, receive(1, report()));
        GameEntity second = game("Match.T.East");
        r = report(second);
        r.put("SCORE1", "3");
        assertEquals(SAVED, receive(2, r));
        assertEquals(-1, standing(1).values()[14]);
        assertEquals(1, standing(2).values()[14]);
    }

    @Test
    void impossibleFieldsAreDroppedIndividuallyNotTrustedOrFatal() {
        GameEntity first = game("Stroke.T.East");
        Map<String, String> r = report(first);
        r.put("PUTTS0", "1025"); // seen from a real console
        r.put("LDRV0", "9999");
        r.put("FRWY0", "15"); // more fairways than drives
        assertEquals(SAVED, receive(1, r));
        GameEntity second = game("Stroke.T.East");
        r = report(second);
        r.put("PUTTS0", "36");
        r.put("LDRV0", "280");
        assertEquals(SAVED, receive(1, r));
        TwStatsService.Standing host = standing(1);
        assertEquals(280, host.values()[37]);
        assertEquals(200, host.values()[43]); // 36 putts over the 18 holes that reported them
        assertEquals(7, host.values()[36]);
        assertEquals(50, host.values()[46]); // 7 of 14, the bogus round's drives excluded
        assertEquals(18, host.values()[33]);
        assertEquals(50, host.values()[42]);
        assertEquals(73, host.values()[39]);
    }

    @Test
    void matchClosedOutEarlyOrWithoutStrokesIsGolfButNotAScoringRound() {
        Map<String, String> r = report();
        r.put("HOLES0", "14");
        r.put("HOLES1", "14");
        r.put("STROKES0", "0");
        r.put("STROKES1", "0");
        r.put("GIR0", "7");
        r.put("BIRD0", "3");
        assertEquals(SAVED, receive(1, r));
        TwStatsService.Standing host = standing(1);
        assertEquals(1, host.values()[7]);
        assertEquals(0, host.values()[39]);
        assertEquals(0, host.values()[41]);
        assertEquals(0, host.values()[45]);
        assertEquals(50, host.values()[42]); // 7 greens in 14 holes still count
        assertEquals(3, host.values()[31]);
    }

    @Test
    void leaderboardsShareTiedRanksAndFindOnePlayer() {
        assertEquals(SAVED, receive(1, report()));
        List<TwStatsService.Standing> board = stats.board(PS2_TW04, 0, 0, 25, "");
        assertEquals(List.of("Host", "Guest"), board.stream().map(TwStatsService.Standing::name).toList());
        assertEquals(List.of(1, 2), board.stream().map(TwStatsService.Standing::rank).toList());
        assertEquals(List.of("Guest"), stats.board(PS2_TW04, 13, 0, 25, "gUEST").stream().map(TwStatsService.Standing::name).toList());
        assertEquals(1, stats.board(PS2_TW04, 13, 1, 25, "").size());
        assertEquals(0, standing(3).rank());
    }

    @Test
    void simultaneousReportsAreCountedOnce() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CyclicBarrier barrier = new CyclicBarrier(2);
            List<Callable<TwStatsService.Receipt>> tasks = List.of(
                    () -> {
                        barrier.await();
                        return receive(1, report());
                    },
                    () -> {
                        barrier.await();
                        return receive(2, report());
                    });
            Set<TwStatsService.Receipt> results = new HashSet<>();
            for (Future<TwStatsService.Receipt> future : executor.invokeAll(tasks)) {
                results.add(future.get());
            }
            assertEquals(Set.of(SAVED, DUPLICATE), results);
        }
        assertEquals(1, standing(1).values()[7]);
    }
}
