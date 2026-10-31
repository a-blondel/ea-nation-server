package eu.eahub.services.stats;

import eu.eahub.dirtysdk.TwStatsCodec.Field;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.dto.TwStanding;
import eu.eahub.entities.core.AccountEntity;
import eu.eahub.entities.core.GameConnectionEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaConnectionEntity;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.entities.stats.TwPersonaStatsEntity;
import eu.eahub.enums.TwReceipt;
import eu.eahub.repositories.core.AccountRepository;
import eu.eahub.repositories.core.GameConnectionRepository;
import eu.eahub.repositories.core.GameRepository;
import eu.eahub.repositories.core.PersonaConnectionRepository;
import eu.eahub.repositories.core.PersonaRepository;
import eu.eahub.repositories.stats.TwGameReportRepository;
import eu.eahub.repositories.stats.TwPersonaStatsRepository;
import eu.eahub.services.TwTestConfig;
import eu.eahub.services.core.TwChallengeService;
import eu.eahub.steps.SocketWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

import static eu.eahub.enums.TwReceipt.DUPLICATE;
import static eu.eahub.enums.TwReceipt.INVALID;
import static eu.eahub.enums.TwReceipt.SAVED;
import static eu.eahub.enums.TwReceipt.UNAUTHORIZED;
import static eu.eahub.services.server.GameServerService.PS2_TW04;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ContextConfiguration(classes = TwTestConfig.class)
@Import(TwStatsService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TwStatsServiceTest {

    private static final DateTimeFormatter WHEN_FORMAT = DateTimeFormatter.ofPattern("yyyy.M.d H:mm:ss");
    private static final String HOST = "Host";
    private static final String GUEST = "Guest";
    private static final String OTHER = "Other";

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
    private AccountRepository accountRepository;
    @Autowired
    private TwStatsService stats;
    @MockitoBean
    private TwChallengeService twChallengeService;
    @MockitoBean
    private SocketWriter socketWriter;
    private GameEntity game;
    private int games;

    @BeforeEach
    void setup() {
        TwTestConfig.reset(jdbc, HOST, GUEST, OTHER);
        games = 0;
        game = game("Match.T.East");
    }

    private PersonaEntity persona(String name) {
        return personaRepository.findByPers(name).orElseThrow();
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
        for (String name : List.of(HOST, GUEST)) {
            PersonaConnectionEntity personaConnection = new PersonaConnectionEntity();
            personaConnection.setPersona(persona(name));
            personaConnection.setVers(PS2_TW04);
            personaConnection.setAddress("/192.0.2." + (name.equals(HOST) ? 1 : 2) + ":1000");
            personaConnection.setStartTime(created.getStartTime());
            personaConnectionRepository.save(personaConnection);
            GameConnectionEntity gameConnection = new GameConnectionEntity();
            gameConnection.setGame(created);
            gameConnection.setPersonaConnection(personaConnection);
            gameConnection.setHost(name.equals(HOST));
            gameConnection.setStartTime(created.getStartTime());
            gameConnectionRepository.save(gameConnection);
        }
        return created;
    }

    private SocketWrapper player(String name) {
        SocketWrapper wrapper = new SocketWrapper();
        wrapper.setPersonaEntity(persona(name));
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
        Map<String, String> report = new HashMap<>();
        report.put("AUTH", "a".repeat(32));
        report.put("WHEN", session.getStartTime().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).format(WHEN_FORMAT));
        report.put("NAME0", HOST);
        report.put("NAME1", HOST);
        report.put("REPT", HOST);
        for (int side = 0; side < 2; side++) {
            report.put("RANK" + side, "1");
            report.put("DONE" + side, "1");
            report.put("QUIT" + side, "0");
            report.put("HOLES" + side, "18");
            report.put("STROKES" + side, side == 0 ? "73" : "70");
            report.put("SCORE" + side, side == 0 ? "2" : "0");
            report.put("PUTTS" + side, "27");
            report.put("GIR" + side, "9");
            report.put("DRVS" + side, "14");
            report.put("FRWY" + side, "7");
            report.put("BIRD" + side, "2");
            report.put("EAGS" + side, "1");
            report.put("LDRV" + side, "321");
        }
        return report;
    }

    private TwReceipt receive(String name, Map<String, String> report) {
        return stats.report(player(name), report, null).getReceipt();
    }

    private TwStanding standing(String name) {
        return stats.standing(persona(name), PS2_TW04);
    }

    private static long value(TwStanding standing, Field field) {
        return standing.getValues()[field.index];
    }

    private List<String> names(int board) {
        return stats.board(PS2_TW04, board, 0, 25, "").stream().map(TwStanding::getName).toList();
    }

    @Test
    void firstReportCounts() {
        Map<String, String> report = report();
        assertEquals(UNAUTHORIZED, receive(OTHER, report));
        assertEquals(SAVED, receive(GUEST, report));
        report.put("SCORE0", "-5");
        assertEquals(DUPLICATE, receive(HOST, report));
        assertEquals(DUPLICATE, receive(HOST, report));
        TwStanding host = standing(HOST);
        TwStanding guest = standing(GUEST);
        assertEquals(1, value(host, Field.MATCH_WINS));
        assertEquals(1, value(guest, Field.MATCH_LOSSES));
        assertEquals(2, host.getPoints());
        assertEquals(GUEST, guest.getName());
        assertEquals(2, twGameReportRepository.count());
        assertTrue(twGameReportRepository.findAll().stream().allMatch(report0 -> report0.getRnk() == 1));
    }

    @Test
    void reportForAnotherGameIsRefused() {
        GameEntity other = game("Match.T.East");
        assertEquals(UNAUTHORIZED, stats.report(player(HOST), report(), other.getId()).getReceipt());
        Map<String, String> report = report();
        report.put("WHEN", "2001.1.1 0:00:00");
        assertEquals(UNAUTHORIZED, receive(HOST, report));
        report.put("WHEN", "not a date");
        assertEquals(UNAUTHORIZED, receive(HOST, report));
        assertEquals(SAVED, stats.report(player(HOST), report(), game.getId()).getReceipt());
    }

    @Test
    void quitForfeits() {
        Map<String, String> report = report();
        report.put("DONE0", "0");
        report.put("DONE1", "0");
        report.put("QUIT1", "1");
        report.put("HOLES0", "2");
        report.put("HOLES1", "2");
        report.put("STROKES0", "0");
        report.put("STROKES1", "0");
        report.put("COMP0", "1");
        report.put("COMP1", "0");
        assertEquals(SAVED, receive(GUEST, report));
        TwStanding host = standing(HOST);
        TwStanding guest = standing(GUEST);
        assertEquals(1, value(host, Field.MATCH_WINS));
        assertEquals(1, value(host, Field.MATCH_COMPLETE));
        assertEquals(0, value(host, Field.MATCH_INCOMPLETE));
        assertEquals(1, value(guest, Field.MATCH_LOSSES));
        assertEquals(1, value(guest, Field.MATCH_INCOMPLETE));
        assertEquals(0, value(guest, Field.MATCH_COMPLETE));
        assertEquals(-1, value(guest, Field.MATCH_STREAK));
        assertEquals(0, value(host, Field.BEST_ROUND));
        assertEquals(0, value(host, Field.LONGEST_DRIVE));
        assertEquals(2, names(TwStatsService.BOARD_MATCH_PLAY).size());
        assertTrue(names(TwStatsService.BOARD_STROKE_PLAY).isEmpty());
    }

    @Test
    void quitBeforeAnyHoleDoesNotCount() {
        Map<String, String> report = report();
        report.put("DONE0", "0");
        report.put("DONE1", "0");
        report.put("QUIT1", "1");
        report.put("HOLES0", "0");
        report.put("HOLES1", "0");
        report.put("STROKES0", "0");
        report.put("STROKES1", "0");
        assertEquals(SAVED, receive(HOST, report));
        TwStanding host = standing(HOST);
        TwStanding guest = standing(GUEST);
        assertEquals(0, value(host, Field.MATCH_WINS));
        assertEquals(0, host.getPoints());
        assertEquals(0, value(guest, Field.MATCH_LOSSES));
        assertEquals(0, value(guest, Field.MATCH_INCOMPLETE));
        assertTrue(names(TwStatsService.BOARD_MATCH_PLAY).isEmpty());
    }

    @Test
    void strokePlayRound() {
        GameEntity stroke = game("Stroke.T.East");
        assertEquals(SAVED, receive(HOST, report(stroke)));
        TwStanding guest = standing(GUEST);
        assertEquals(1, value(guest, Field.STROKE_WINS));
        assertEquals(50, value(guest, Field.GIR_PERCENT));
        assertEquals(150, value(guest, Field.PUTTS_PER_HOLE));
        assertEquals(50, value(guest, Field.DRIVING_ACCURACY));
        assertEquals(18, value(guest, Field.HOLES_PER_EAGLE));
        assertEquals(70, value(guest, Field.BEST_ROUND));
        assertEquals(70, value(guest, Field.SCORING_AVERAGE));
        assertEquals(321, value(guest, Field.LONGEST_DRIVE));
        assertEquals(2, names(TwStatsService.BOARD_STROKE_PLAY).size());
        assertTrue(names(TwStatsService.BOARD_MATCH_PLAY).isEmpty());
    }

    @Test
    void tiesAndWeeklyBoard() {
        Map<String, String> report = report();
        report.put("SCORE0", "0");
        assertEquals(SAVED, receive(HOST, report));
        assertEquals(1, value(standing(HOST), Field.MATCH_TIES));
        assertEquals(1, standing(GUEST).getRank());
        assertEquals(2, names(TwStatsService.BOARD_LAST_WEEK).size());
        startedLongAgo(game);
        assertTrue(names(TwStatsService.BOARD_LAST_WEEK).isEmpty());
        assertEquals(2, names(TwStatsService.BOARD_MATCH_PLAY).size());
        GameEntity abandoned = game("Match.T.East");
        report = report(abandoned);
        report.put("DONE0", "0");
        report.put("QUIT0", "1");
        report.put("QUIT1", "1");
        assertEquals(SAVED, receive(HOST, report));
        assertEquals(1, value(standing(HOST), Field.MATCH_INCOMPLETE));
        assertEquals(1, standing(HOST).getPoints());
    }

    @Test
    void invalidReportsAndStreaks() {
        Map<String, String> report = report();
        report.remove("DONE0");
        assertEquals(INVALID, receive(HOST, report));
        report = report();
        report.put("HOLES0", "900");
        assertEquals(INVALID, receive(HOST, report));
        assertEquals(SAVED, receive(HOST, report()));
        GameEntity second = game("Match.T.East");
        report = report(second);
        report.put("SCORE1", "3");
        assertEquals(SAVED, receive(GUEST, report));
        assertEquals(-1, value(standing(HOST), Field.MATCH_STREAK));
        assertEquals(1, value(standing(GUEST), Field.MATCH_STREAK));
    }

    @Test
    void impossibleFieldsAreDropped() {
        GameEntity first = game("Stroke.T.East");
        Map<String, String> report = report(first);
        report.put("PUTTS0", "1025"); // seen from a real console
        report.put("LDRV0", "9999");
        report.put("FRWY0", "15"); // more fairways than drives
        assertEquals(SAVED, receive(HOST, report));
        GameEntity second = game("Stroke.T.East");
        report = report(second);
        report.put("PUTTS0", "36");
        report.put("LDRV0", "280");
        assertEquals(SAVED, receive(HOST, report));
        TwStanding host = standing(HOST);
        assertEquals(280, value(host, Field.LONGEST_DRIVE));
        assertEquals(200, value(host, Field.PUTTS_PER_HOLE)); // 36 putts over the 18 holes that reported them
        assertEquals(7, value(host, Field.FAIRWAYS));
        assertEquals(50, value(host, Field.DRIVING_ACCURACY)); // 7 of 14, the bogus round's drives excluded
        assertEquals(18, value(host, Field.GIR));
        assertEquals(50, value(host, Field.GIR_PERCENT));
        assertEquals(73, value(host, Field.BEST_ROUND));
    }

    @Test
    void matchClosedOutEarlyIsNotAScoringRound() {
        Map<String, String> report = report();
        report.put("HOLES0", "14");
        report.put("HOLES1", "14");
        report.put("STROKES0", "0");
        report.put("STROKES1", "0");
        report.put("GIR0", "7");
        report.put("BIRD0", "3");
        assertEquals(SAVED, receive(HOST, report));
        TwStanding host = standing(HOST);
        assertEquals(1, value(host, Field.MATCH_WINS));
        assertEquals(0, value(host, Field.BEST_ROUND));
        assertEquals(0, value(host, Field.SCORING_AVERAGE));
        assertEquals(0, value(host, Field.BIRDIE_AVERAGE));
        assertEquals(50, value(host, Field.GIR_PERCENT)); // 7 greens in 14 holes still count
        assertEquals(3, value(host, Field.BIRDIES));
    }

    private void golfer(String name, int longestDrive, Integer bestRound, int matchWins) {
        TwPersonaStatsEntity playerStats = stats.getOrCreateStats(persona(name), PS2_TW04);
        playerStats.setLongestDrive(longestDrive);
        playerStats.setBestRound(bestRound);
        playerStats.setRounds(bestRound == null ? 0 : 1);
        playerStats.setRoundStrokes(bestRound == null ? 0 : bestRound);
        playerStats.setMatchWins(matchWins);
        twPersonaStatsRepository.save(playerStats);
    }

    private static List<String> rows(List<TwStanding> board) {
        return board.stream().map(row -> row.getRank() + " " + row.getName() + " " + row.getPoints()).toList();
    }

    @Test
    void statisticLists() {
        golfer(HOST, 310, 70, 2);
        golfer(GUEST, 345, 66, 0);
        golfer(OTHER, 310, null, 2);
        // List 4 is the longest drive (field 37); a tie shares the rank
        assertEquals(List.of("1 Guest 345", "2 Host 310", "2 Other 310"), rows(stats.board(PS2_TW04, 4, 0, 25, "")));
        // List 29 is the best round (field 39, 8 bits, lowest first): 256 - strokes, and a player without a round is left out
        assertEquals(List.of("1 Guest 190", "2 Host 186"), rows(stats.board(PS2_TW04, 29, 0, 25, "")));
        // List 18 is match wins (field 7); players without one are left out
        assertEquals(List.of("1 Host 2", "1 Other 2"), rows(stats.board(PS2_TW04, 18, 0, 25, "")));
        assertEquals(List.of("1 Other 2"), rows(stats.board(PS2_TW04, 18, 0, 25, "oTHER")));
        assertEquals(List.of("2 Other 310"), rows(stats.board(PS2_TW04, 4, 2, 25, "")));
        // A list of a statistic nobody has, and the lists that are not statistics
        assertEquals(List.of(), stats.board(PS2_TW04, 30, 0, 25, ""));
        assertTrue(TwStatsService.isStatList(1) && TwStatsService.isStatList(31));
        assertFalse(TwStatsService.isStatList(TwStatsService.BOARD_OVERALL) || TwStatsService.isStatList(TwStatsService.BOARD_MATCH_PLAY)
                || TwStatsService.isStatList(TwStatsService.BOARD_STROKE_PLAY) || TwStatsService.isStatList(TwStatsService.BOARD_LAST_WEEK));
    }

    @Test
    void leaderboards() {
        assertEquals(SAVED, receive(HOST, report()));
        List<TwStanding> board = stats.board(PS2_TW04, TwStatsService.BOARD_OVERALL, 0, 25, "");
        assertEquals(List.of(HOST, GUEST), board.stream().map(TwStanding::getName).toList());
        assertEquals(List.of(1, 2), board.stream().map(TwStanding::getRank).toList());
        assertEquals(List.of(GUEST), stats.board(PS2_TW04, TwStatsService.BOARD_MATCH_PLAY, 0, 25, "gUEST").stream().map(TwStanding::getName).toList());
        assertEquals(1, stats.board(PS2_TW04, TwStatsService.BOARD_MATCH_PLAY, 1, 25, "").size());
        assertEquals(0, standing(OTHER).getRank());
    }

    @Test
    void bannedPlayersAreLeftOut() {
        assertEquals(SAVED, receive(HOST, report()));
        golfer(HOST, 310, 70, 1);
        AccountEntity account = persona(HOST).getAccount();
        account.setBanned(true);
        accountRepository.save(account);
        assertEquals(List.of(GUEST), names(TwStatsService.BOARD_OVERALL));
        assertEquals(List.of(GUEST), names(TwStatsService.BOARD_MATCH_PLAY));
        assertTrue(names(TwStatsService.BOARD_LAST_WEEK).isEmpty());
        assertEquals(List.of(GUEST), names(4)); // the longest drive list keeps the guest's 321 yards
        assertTrue(stats.board(PS2_TW04, TwStatsService.BOARD_OVERALL, 0, 25, HOST).isEmpty());
        assertEquals(1, standing(GUEST).getRank());
    }

    @Test
    void simultaneousReportsCountOnce() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CyclicBarrier barrier = new CyclicBarrier(2);
            List<Callable<TwReceipt>> tasks = List.of(
                    () -> {
                        barrier.await();
                        return receive(HOST, report());
                    },
                    () -> {
                        barrier.await();
                        return receive(GUEST, report());
                    });
            Set<TwReceipt> results = new HashSet<>();
            for (Future<TwReceipt> future : executor.invokeAll(tasks)) {
                results.add(future.get());
            }
            assertEquals(Set.of(SAVED, DUPLICATE), results);
        }
        assertEquals(1, value(standing(HOST), Field.MATCH_WINS));
    }
}
