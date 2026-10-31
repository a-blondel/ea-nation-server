package eu.eahub.services.stats;

import eu.eahub.dirtysdk.LobbyTagField;
import eu.eahub.dirtysdk.TwStatsCodec;
import eu.eahub.dirtysdk.TwStatsCodec.Field;
import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.dto.TwRankResult;
import eu.eahub.dto.TwStanding;
import eu.eahub.entities.core.GameConnectionEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.entities.stats.TwGameReportEntity;
import eu.eahub.entities.stats.TwPersonaStatsEntity;
import eu.eahub.enums.TwReceipt;
import eu.eahub.repositories.core.GameConnectionRepository;
import eu.eahub.repositories.core.GameRepository;
import eu.eahub.repositories.core.PersonaRepository;
import eu.eahub.repositories.stats.TwGameReportRepository;
import eu.eahub.repositories.stats.TwPersonaStatsRepository;
import eu.eahub.services.core.TwChallengeService;
import eu.eahub.steps.SocketWriter;
import eu.eahub.utils.TwCardUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToLongFunction;

import static eu.eahub.services.server.GameServerService.ALL_TW;

/**
 * Tiger Woods head-to-head results and leaderboards
 * A match counts once, whoever reports it first, and only its two players can report it
 * Ranking: two points per win, one per tie; a player who quits alone forfeits
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TwStatsService {

    // The leaderboards the STATISTICS screen asks for (snap INDEX), besides one list per statistic and the tournament boards
    public static final int BOARD_OVERALL = 0;
    public static final int BOARD_MATCH_PLAY = 13;
    public static final int BOARD_STROKE_PLAY = 14;
    public static final int BOARD_LAST_WEEK = 32;
    private static final int DEFAULT_CHANNEL = 4;
    private static final int DEFAULT_RANGE = 25;
    private static final int MAX_RANGE = 50;
    private static final int MAX_REPORT_FIELDS = 128;
    private static final int MAX_FIELD_LENGTH = 1024;
    private static final int MAX_HOLES = 18;
    private static final int MAX_STROKES = 10000;
    private static final int MIN_SCORE = -1000;
    private static final long MAX_STATISTIC = 100000;
    private static final long WEEK_DAYS = 7;
    // WHEN as reported back by the game: the session start time it was sent, in UTC
    private static final DateTimeFormatter WHEN_FORMAT = DateTimeFormatter.ofPattern("yyyy.M.d H:mm:ss");
    // Rank points at which the game shows the next Tiger status
    private static final int[] TIGER_STATUS_POINTS = {50, 100, 200, 500};
    // The statistic each of the game's 32 built-in ranked lists sorts by (its list table at 0x003027C0). 0 is the overall
    // board, which the STATISTICS screen opens for rank points; 13 and 14 are the Match and Stroke Play boards
    private static final int[] LIST_FIELDS = {0, 41, 43, 44, 37, 34, 33, 42, 46, 30, 31, 45, 32, 4, 5, 10, 11, 12, 7, 8, 9, 13, 14, 26,
            27, 15, 16, 19, 20, 39, 40, 50};
    // Statistics the game ranks lowest first: it draws 2^width - P for them
    private static final Set<Integer> LOWEST_FIRST = Set.of(Field.BEST_ROUND.index, Field.SCORING_AVERAGE.index, Field.PUTTS_PER_HOLE.index,
            Field.HOLES_PER_EAGLE.index);

    private final TwGameReportRepository twGameReportRepository;
    private final TwPersonaStatsRepository twPersonaStatsRepository;
    private final GameConnectionRepository gameConnectionRepository;
    private final GameRepository gameRepository;
    private final PersonaRepository personaRepository;
    private final TwChallengeService twChallengeService;
    private final SocketWriter socketWriter;

    /**
     * Request ranking snapshot (snap)
     * INDEX 0 is the overall board, 13 Match Play, 14 Stroke Play, 32 the last seven days, 1 to 31 one statistic each,
     * and the others are tournament boards, answered empty. The snap acknowledgement arms the channel (4 or 5) before its
     * +snp rows arrive
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void snap(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (socketWrapper.getPersonaEntity() == null) {
            socketWriter.writeText(socket, "snapnusr", "");
            return;
        }
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        int index = LobbyTagField.getNumber(fields, "INDEX", -1);
        int channel = LobbyTagField.getNumber(fields, "CHAN", DEFAULT_CHANNEL);
        if (channel != 4 && channel != 5) {
            socketWriter.writeText(socket, "snapdata", "");
            return;
        }
        int start = Math.max(0, LobbyTagField.getNumber(fields, "START", 0));
        int count = Math.clamp(LobbyTagField.getNumber(fields, "RANGE", DEFAULT_RANGE), 0, MAX_RANGE);
        String find = fields.getOrDefault("FIND", "");
        boolean headToHead = index == BOARD_OVERALL || index == BOARD_MATCH_PLAY || index == BOARD_STROKE_PLAY
                || index == BOARD_LAST_WEEK || isStatList(index);
        List<TwStanding> page = headToHead ? board(vers, index, start, count, find) : List.of();
        String chan = String.valueOf(channel);
        String size = String.valueOf(page.size());
        socketWriter.writeTagFields(socket, "snap", Map.of("CHAN", chan, "COUNT", size, "RANGE", size, "MORE", "0"));
        for (TwStanding row : page) {
            socketWriter.writeTagFields(socket, "+snp", Map.of("CHAN", chan, "R", String.valueOf(row.getRank()), "P", String.valueOf(row.getPoints()),
                    "N", row.getName(), "S", row.getPacked()));
        }
        log.info("TW04 leaderboard index {} channel {}: {} rows", index, channel, page.size());
    }

    /**
     * Send ranking results (rank), sent by both players once they are back in the lobby
     * The match counts once; the caller ends its session
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     * @return The match the report was accepted for, or null if it was refused
     */
    @Transactional
    public GameEntity rank(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (socketWrapper.getPersonaEntity() == null) {
            socketWriter.writeText(socket, "ranknusr", "");
            return null;
        }
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        TwRankResult result = report(socketWrapper, fields, twChallengeService.getGameIdBySessionToken(fields.get("AUTH")));
        switch (result.getReceipt()) {
            case UNAUTHORIZED -> socketWriter.writeText(socket, "rankmaut", "");
            case INVALID -> socketWriter.writeText(socket, "rankdata", "");
            default -> {
                socketWriter.writeTagFields(socket, "rank", Map.of("TITLE", "SUCCESS", "MESG", "Game results received. Statistics saved."));
                log.info("TW04 result {} from {}", result.getReceipt(), socketWrapper.getPersonaEntity().getPers());
            }
        }
        return result.getGame();
    }

    /**
     * Record a player's report of a head-to-head match
     * The match is found from the reporter and WHEN, the start time sent in +ses. The game row is locked, so that both
     * players reporting at the same time still count the match once
     *
     * @param reporter       The reporting player's socket wrapper
     * @param fields         The report fields
     * @param expectedGameId The game the report's AUTH token was issued for, or null if unknown (e.g. after a restart)
     * @return The receipt and the match
     */
    @Transactional
    public TwRankResult report(SocketWrapper reporter, Map<String, String> fields, Long expectedGameId) {
        if (!valid(fields)) {
            return new TwRankResult(TwReceipt.INVALID, null);
        }
        Optional<GameEntity> found = findGame(reporter.getPersonaEntity(), fields.get("WHEN"));
        if (found.isEmpty() || (expectedGameId != null && !expectedGameId.equals(found.get().getId()))) {
            log.warn("TW04 report from {} matches no session started at {}", reporter.getPersonaEntity().getPers(), fields.get("WHEN"));
            return new TwRankResult(TwReceipt.UNAUTHORIZED, null);
        }
        // Within a transaction, the lock makes a second report wait for the first one to be committed
        GameEntity game = gameRepository.findForUpdateById(found.get().getId()).orElseThrow();
        GameConnectionEntity[] sides = sides(game);
        if (sides == null) {
            return new TwRankResult(TwReceipt.UNAUTHORIZED, null);
        }
        if (twGameReportRepository.existsByGameConnectionGameId(game.getId())) {
            return new TwRankResult(TwReceipt.DUPLICATE, game);
        }
        List<Map<String, Long>> cards = new ArrayList<>();
        for (int side = 0; side < 2; side++) {
            Map<String, Long> card = card(fields, side);
            cards.add(card);
            TwGameReportEntity report = new TwGameReportEntity();
            report.setGameConnection(sides[side]);
            report.setSide(side);
            TwCardUtils.setCard(report, card);
            twGameReportRepository.save(report);
        }
        if (!isRanked(game)) {
            return new TwRankResult(TwReceipt.SAVED, game);
        }
        TwPersonaStatsEntity[] stats = new TwPersonaStatsEntity[2];
        for (int side = 0; side < 2; side++) {
            stats[side] = getOrCreateStats(sides[side].getPersonaConnection().getPersona(), game.getVers());
        }
        applyMatch(isMatchPlay(game), cards, stats);
        twPersonaStatsRepository.saveAll(List.of(stats));
        return new TwRankResult(TwReceipt.SAVED, game);
    }

    /**
     * Find the match a report is for
     *
     * @param reporter The reporting player
     * @param when     The match's start time as the game reports it, in UTC
     * @return The match, if the player has one that started then
     */
    private Optional<GameEntity> findGame(PersonaEntity reporter, String when) {
        LocalDateTime startTime;
        try {
            startTime = LocalDateTime.parse(when, WHEN_FORMAT).atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException | NullPointerException e) {
            return Optional.empty();
        }
        return gameConnectionRepository.findByPlayerNameAndGameStartTimeAndGameVersIn(reporter.getPers(), startTime, ALL_TW).stream()
                .map(GameConnectionEntity::getGame)
                .findFirst();
    }

    /**
     * Get the host (side 0) and guest (side 1) connections of a match
     *
     * @param game The match
     * @return Both connections, or null if the match does not have a host and a guest
     */
    private static GameConnectionEntity[] sides(GameEntity game) {
        GameConnectionEntity[] sides = new GameConnectionEntity[2];
        for (GameConnectionEntity connection : game.getGameConnections()) {
            sides[connection.isHost() ? 0 : 1] = connection;
        }
        return sides[0] == null || sides[1] == null ? null : sides;
    }

    /**
     * Whether a match is Match Play rather than Stroke Play, from its name (the room's, or the mode from Messenger)
     *
     * @param game The match
     * @return True for Match Play
     */
    public static boolean isMatchPlay(GameEntity game) {
        return game.getName() != null && game.getName().startsWith("Match.");
    }

    /**
     * Whether a match counts on the statistics: RANKED GAME was on in its setup (MFLG, the third of its PARAMS)
     * Matches started before MFLG was kept are ranked
     *
     * @param game The match
     * @return True if the match is ranked
     */
    public static boolean isRanked(GameEntity game) {
        String[] params = game.getParams() == null ? new String[0] : game.getParams().split(",");
        try {
            return params.length < 3 || (Integer.parseInt(params[2]) & TwChallengeService.MODE_RANKED) != 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * Check a report's fields before anything is read from it
     *
     * @param fields The report fields
     * @return True if both sides' results are present and plausible
     */
    private static boolean valid(Map<String, String> fields) {
        if (fields.size() > MAX_REPORT_FIELDS || fields.values().stream().anyMatch(value -> value == null || value.length() > MAX_FIELD_LENGTH)) {
            return false;
        }
        try {
            for (int side = 0; side < 2; side++) {
                for (String key : List.of("DONE", "QUIT")) {
                    if (!Set.of("0", "1").contains(fields.getOrDefault(key + side, ""))) {
                        return false;
                    }
                }
                for (String key : List.of("STROKES", "HOLES", "SCORE")) {
                    int value = Integer.parseInt(fields.get(key + side));
                    if (value < (key.equals("SCORE") ? MIN_SCORE : 0) || value > MAX_STROKES) {
                        return false;
                    }
                }
                for (String key : List.of("PUTTS", "GIR", "FRWY", "DRVS", "LDRV", "LPUT", "EAGS", "BIRD", "ACES")) {
                    long value = Long.parseLong(fields.getOrDefault(key + side, "0"));
                    if (value < 0 || value > MAX_STATISTIC) {
                        return false;
                    }
                }
                if (Integer.parseInt(fields.get("HOLES" + side)) > MAX_HOLES) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Get one side's scorecard from a report (fields suffixed 0 for the host, 1 for the guest)
     *
     * @param fields The report fields
     * @param side   The side
     * @return The scorecard fields, 0 where absent
     */
    private static Map<String, Long> card(Map<String, String> fields, int side) {
        Map<String, Long> card = new LinkedHashMap<>();
        for (String key : TwCardUtils.CARD_FIELDS) {
            try {
                card.put(key, Long.parseLong(fields.getOrDefault(key + side, "0").strip()));
            } catch (NumberFormatException e) {
                card.put(key, 0L);
            }
        }
        return card;
    }

    /**
     * Get a player's statistics, created if they have none yet
     *
     * @param persona The player
     * @param vers    The game version
     * @return The statistics
     */
    public TwPersonaStatsEntity getOrCreateStats(PersonaEntity persona, String vers) {
        return twPersonaStatsRepository.findByPersonaIdAndVers(persona.getId(), vers).orElseGet(() -> newStats(persona, vers));
    }

    /**
     * Create empty statistics for a player
     *
     * @param persona The player
     * @param vers    The game version
     * @return The statistics, not stored yet
     */
    static TwPersonaStatsEntity newStats(PersonaEntity persona, String vers) {
        TwPersonaStatsEntity stats = new TwPersonaStatsEntity();
        stats.setPersona(persona);
        stats.setVers(vers);
        return stats;
    }

    /**
     * Apply a match result to both players' statistics (side 0 is the host)
     * A match abandoned before a hole was played does not count. Match Play is won on holes up (SCORE), Stroke Play
     * on fewer strokes. A player who quits alone forfeits; if both quit, or the round is otherwise unfinished, it is
     * an incomplete for both. Golf statistics only come from finished matches
     *
     * @param matchPlay True for Match Play, false for Stroke Play
     * @param cards     Both players' card fields
     * @param stats     Both players' statistics, updated in place
     */
    static void applyMatch(boolean matchPlay, List<Map<String, Long>> cards, TwPersonaStatsEntity[] stats) {
        if (cards.get(0).get("HOLES") == 0 && cards.get(1).get("HOLES") == 0) {
            return;
        }
        boolean hostQuit = cards.get(0).get("QUIT") != 0;
        boolean guestQuit = cards.get(1).get("QUIT") != 0;
        boolean finished = cards.get(0).get("DONE") != 0 && cards.get(1).get("DONE") != 0 && !hostQuit && !guestQuit;
        boolean forfeit = hostQuit != guestQuit;
        int winner = -1;
        if (forfeit) {
            winner = hostQuit ? 1 : 0;
        } else if (finished) {
            int comparison = matchPlay
                    ? Long.compare(cards.get(0).get("SCORE"), cards.get(1).get("SCORE"))
                    : Long.compare(cards.get(1).get("STROKES"), cards.get(0).get("STROKES"));
            if (comparison > 0) {
                winner = 0;
            } else if (comparison < 0) {
                winner = 1;
            }
        }
        for (int side = 0; side < 2; side++) {
            TwPersonaStatsEntity playerStats = stats[side];
            boolean quit = side == 0 ? hostQuit : guestQuit;
            if (finished || forfeit) {
                if (winner == -1) {
                    addTie(playerStats, matchPlay);
                } else if (winner == side) {
                    addWin(playerStats, matchPlay);
                } else {
                    addLoss(playerStats, matchPlay);
                }
                addCompletion(playerStats, matchPlay, !quit);
            } else {
                addCompletion(playerStats, matchPlay, false);
            }
            if (finished) {
                addGolf(playerStats, cards.get(side));
            }
        }
    }

    /**
     * Count a tie: one point, and the streak ends
     *
     * @param stats     The player's statistics
     * @param matchPlay True for Match Play, false for Stroke Play
     */
    private static void addTie(TwPersonaStatsEntity stats, boolean matchPlay) {
        if (matchPlay) {
            stats.setMatchTies(stats.getMatchTies() + 1);
            stats.setMatchPoints(stats.getMatchPoints() + 1);
            stats.setMatchStreak(0);
        } else {
            stats.setStrokeTies(stats.getStrokeTies() + 1);
            stats.setStrokePoints(stats.getStrokePoints() + 1);
            stats.setStrokeStreak(0);
        }
    }

    /**
     * Count a win: two points, and the winning streak grows
     *
     * @param stats     The player's statistics
     * @param matchPlay True for Match Play, false for Stroke Play
     */
    private static void addWin(TwPersonaStatsEntity stats, boolean matchPlay) {
        if (matchPlay) {
            stats.setMatchWins(stats.getMatchWins() + 1);
            stats.setMatchPoints(stats.getMatchPoints() + 2);
            stats.setMatchStreak(Math.max(0, stats.getMatchStreak()) + 1);
        } else {
            stats.setStrokeWins(stats.getStrokeWins() + 1);
            stats.setStrokePoints(stats.getStrokePoints() + 2);
            stats.setStrokeStreak(Math.max(0, stats.getStrokeStreak()) + 1);
        }
    }

    /**
     * Count a loss: the losing streak grows (negative)
     *
     * @param stats     The player's statistics
     * @param matchPlay True for Match Play, false for Stroke Play
     */
    private static void addLoss(TwPersonaStatsEntity stats, boolean matchPlay) {
        if (matchPlay) {
            stats.setMatchLosses(stats.getMatchLosses() + 1);
            stats.setMatchStreak(Math.min(0, stats.getMatchStreak()) - 1);
        } else {
            stats.setStrokeLosses(stats.getStrokeLosses() + 1);
            stats.setStrokeStreak(Math.min(0, stats.getStrokeStreak()) - 1);
        }
    }

    /**
     * Count a match as completed or not
     *
     * @param stats     The player's statistics
     * @param matchPlay True for Match Play, false for Stroke Play
     * @param complete  True if the player finished the match
     */
    private static void addCompletion(TwPersonaStatsEntity stats, boolean matchPlay, boolean complete) {
        if (matchPlay && complete) {
            stats.setMatchComplete(stats.getMatchComplete() + 1);
        } else if (matchPlay) {
            stats.setMatchIncomplete(stats.getMatchIncomplete() + 1);
        } else if (complete) {
            stats.setStrokeComplete(stats.getStrokeComplete() + 1);
        } else {
            stats.setStrokeIncomplete(stats.getStrokeIncomplete() + 1);
        }
    }

    /**
     * Add a round's golf to a player's statistics
     * Impossible values are dropped one field at a time; each rate only divides by the holes or drives whose value
     * survived. Scoring figures only use full rounds: a Match Play card closed out early, or one with no stroke total,
     * is golf but not a "round"
     *
     * @param stats The player's statistics, updated in place
     * @param raw   The card fields as reported
     */
    public static void addGolf(TwPersonaStatsEntity stats, Map<String, Long> raw) {
        Map<String, Long> card = TwCardUtils.clean(raw);
        long played = card.getOrDefault("HOLES", 0L);
        stats.setHoles(stats.getHoles() + (int) played);
        if (card.containsKey("PUTTS")) {
            stats.setPutts(stats.getPutts() + card.get("PUTTS").intValue());
            stats.setPuttHoles(stats.getPuttHoles() + (int) played);
        }
        if (card.containsKey("GIR")) {
            stats.setGir(stats.getGir() + card.get("GIR").intValue());
            stats.setGirHoles(stats.getGirHoles() + (int) played);
        }
        if (card.containsKey("FRWY")) {
            stats.setFairways(stats.getFairways() + card.get("FRWY").intValue());
            stats.setDrives(stats.getDrives() + card.get("DRVS").intValue());
        }
        stats.setEagles(stats.getEagles() + card.getOrDefault("EAGS", 0L).intValue());
        stats.setBirdies(stats.getBirdies() + card.getOrDefault("BIRD", 0L).intValue());
        stats.setAces(stats.getAces() + card.getOrDefault("ACES", 0L).intValue());
        stats.setLongestPutt(Math.max(stats.getLongestPutt(), card.getOrDefault("LPUT", 0L).intValue()));
        stats.setLongestDrive(Math.max(stats.getLongestDrive(), card.getOrDefault("LDRV", 0L).intValue()));
        Long strokes = card.get("STROKES");
        if (strokes != null && played == MAX_HOLES) {
            stats.setRounds(stats.getRounds() + 1);
            stats.setRoundStrokes(stats.getRoundStrokes() + strokes.intValue());
            stats.setRoundBirdies(stats.getRoundBirdies() + card.getOrDefault("BIRD", 0L).intValue());
            stats.setBestRound(stats.getBestRound() == null ? strokes.intValue() : Math.min(stats.getBestRound(), strokes.intValue()));
        }
    }

    /**
     * Convert statistics to the game's 56 values
     *
     * @param stats The player's statistics, or null if the player has none
     * @return The values to pack
     */
    public static long[] values(TwPersonaStatsEntity stats) {
        long[] values = TwStatsCodec.defaults();
        if (stats == null) {
            return values;
        }
        long points = stats.getMatchPoints() + stats.getStrokePoints();
        values[Field.POINTS.index] = points;
        values[Field.MATCH_POINTS.index] = stats.getMatchPoints();
        values[Field.STROKE_POINTS.index] = stats.getStrokePoints();
        long status = 0;
        for (int threshold : TIGER_STATUS_POINTS) {
            if (points >= threshold) {
                status++;
            }
        }
        values[Field.TIGER_STATUS.index] = status;
        values[Field.MATCH_WINS.index] = stats.getMatchWins();
        values[Field.MATCH_LOSSES.index] = stats.getMatchLosses();
        values[Field.MATCH_TIES.index] = stats.getMatchTies();
        values[Field.STROKE_WINS.index] = stats.getStrokeWins();
        values[Field.STROKE_LOSSES.index] = stats.getStrokeLosses();
        values[Field.STROKE_TIES.index] = stats.getStrokeTies();
        values[Field.STROKE_STREAK.index] = stats.getStrokeStreak();
        values[Field.MATCH_STREAK.index] = stats.getMatchStreak();
        values[Field.STROKE_INCOMPLETE.index] = stats.getStrokeIncomplete();
        values[Field.MATCH_INCOMPLETE.index] = stats.getMatchIncomplete();
        values[Field.STROKE_COMPLETE.index] = stats.getStrokeComplete();
        values[Field.MATCH_COMPLETE.index] = stats.getMatchComplete();
        values[Field.EVENTS.index] = stats.getEvents();
        values[Field.EVENT_WINS.index] = stats.getEventWins();
        values[Field.TOP10.index] = stats.getTop10();
        values[Field.TOP25.index] = stats.getTop25();
        values[Field.EARNINGS.index] = stats.getEarnings() / 100;
        values[Field.EAGLES.index] = stats.getEagles();
        values[Field.BIRDIES.index] = stats.getBirdies();
        values[Field.ACES.index] = stats.getAces();
        values[Field.GIR.index] = stats.getGir();
        values[Field.LONGEST_PUTT.index] = stats.getLongestPutt();
        values[Field.FAIRWAYS.index] = stats.getFairways();
        values[Field.LONGEST_DRIVE.index] = stats.getLongestDrive();
        if (stats.getGirHoles() > 0) {
            values[Field.GIR_PERCENT.index] = Math.min(100, Math.round(100.0 * stats.getGir() / stats.getGirHoles()));
        }
        if (stats.getPuttHoles() > 0) {
            values[Field.PUTTS_PER_HOLE.index] = Math.round(100.0 * stats.getPutts() / stats.getPuttHoles());
        }
        if (stats.getDrives() > 0) {
            values[Field.DRIVING_ACCURACY.index] = Math.min(100, Math.round(100.0 * stats.getFairways() / stats.getDrives()));
        }
        if (stats.getEagles() > 0) {
            values[Field.HOLES_PER_EAGLE.index] = Math.round((double) stats.getHoles() / stats.getEagles());
        }
        if (stats.getRounds() > 0) {
            values[Field.BEST_ROUND.index] = stats.getBestRound();
            values[Field.SCORING_AVERAGE.index] = Math.round((double) stats.getRoundStrokes() / stats.getRounds());
            values[Field.BIRDIE_AVERAGE.index] = Math.round((double) stats.getRoundBirdies() / stats.getRounds());
        }
        return values;
    }

    /**
     * Get a player's overall standing
     *
     * @param persona The player
     * @param vers    The game version
     * @return The standing, unranked if the player has no results
     */
    public TwStanding standing(PersonaEntity persona, String vers) {
        return twPersonaStatsRepository.findByPersonaIdAndVers(persona.getId(), vers)
                .map(stats -> standing(stats, BOARD_OVERALL))
                .orElseGet(() -> unranked(persona));
    }

    /**
     * Get the overall standings of several players, e.g. everyone in a room
     *
     * @param personas The players
     * @param vers     The game version
     * @return The standings by persona id
     */
    public Map<Long, TwStanding> standings(Collection<PersonaEntity> personas, String vers) {
        Map<Long, TwStanding> result = new HashMap<>();
        for (PersonaEntity persona : personas) {
            result.put(persona.getId(), unranked(persona));
        }
        for (TwPersonaStatsEntity stats : twPersonaStatsRepository.findByPersonaIdInAndVers(result.keySet(), vers)) {
            result.put(stats.getPersona().getId(), standing(stats, BOARD_OVERALL));
        }
        return result;
    }

    /**
     * Get the standing of a player without results
     *
     * @param persona The player
     * @return An unranked standing with the default statistics
     */
    private static TwStanding unranked(PersonaEntity persona) {
        long[] values = values(null);
        return new TwStanding(persona.getPers(), 0, 0, values, TwStatsCodec.pack(values));
    }

    /**
     * Get a player's standing on a head-to-head board
     *
     * @param stats The player's statistics
     * @param board The overall, Match Play or Stroke Play board
     * @return The standing
     */
    private TwStanding standing(TwPersonaStatsEntity stats, int board) {
        long points = switch (board) {
            case BOARD_MATCH_PLAY -> stats.getMatchPoints();
            case BOARD_STROKE_PLAY -> stats.getStrokePoints();
            default -> stats.getMatchPoints() + stats.getStrokePoints();
        };
        long rank = 0;
        if (inBoard(stats, board)) {
            rank = switch (board) {
                case BOARD_MATCH_PLAY -> twPersonaStatsRepository.getMatchRankByPoints(stats.getVers(), stats.getMatchPoints());
                case BOARD_STROKE_PLAY -> twPersonaStatsRepository.getStrokeRankByPoints(stats.getVers(), stats.getStrokePoints());
                default -> twPersonaStatsRepository.getRankByPoints(stats.getVers(), (int) points);
            };
        }
        return standing(stats, (int) rank, points);
    }

    /**
     * Get a player's standing with a known rank and points
     *
     * @param stats  The player's statistics
     * @param rank   The rank
     * @param points The points the board sorts by
     * @return The standing
     */
    private static TwStanding standing(TwPersonaStatsEntity stats, int rank, long points) {
        long[] values = values(stats);
        return new TwStanding(stats.getPersona().getPers(), rank, points, values, TwStatsCodec.pack(values));
    }

    /**
     * Whether a list is one of the game's leaderboards of a single statistic (e.g. 4 = longest drive, 18 = match wins)
     *
     * @param board The list index
     * @return True for 1 to 31, except the Match and Stroke Play boards
     */
    public static boolean isStatList(int board) {
        return board >= 1 && board < LIST_FIELDS.length && board != BOARD_MATCH_PLAY && board != BOARD_STROKE_PLAY;
    }

    /**
     * Get a page of a head-to-head leaderboard
     *
     * @param vers  The game version
     * @param board The overall, Match Play, Stroke Play or last seven days board, or a statistic's list (isStatList)
     * @param start The first row (from 0)
     * @param count The number of rows
     * @param find  A player name to show alone, or empty
     * @return The rows
     */
    public List<TwStanding> board(String vers, int board, int start, int count, String find) {
        if (board == BOARD_LAST_WEEK || isStatList(board)) {
            List<TwStanding> rows = board == BOARD_LAST_WEEK ? week(vers) : statBoard(vers, board);
            if (!find.isEmpty()) {
                return rows.stream().filter(row -> row.getName().equalsIgnoreCase(find)).limit(count).toList();
            }
            return rows.stream().skip(start).limit(count).toList();
        }
        if (!find.isEmpty()) {
            Optional<TwPersonaStatsEntity> found = personaRepository.findByPers(find)
                    .filter(persona -> persona.getDeletedOn() == null && !persona.getAccount().isBanned())
                    .flatMap(persona -> twPersonaStatsRepository.findByPersonaIdAndVers(persona.getId(), vers));
            return found.filter(stats -> inBoard(stats, board)).map(stats -> List.of(standing(stats, board))).orElse(List.of());
        }
        List<TwPersonaStatsEntity> page = switch (board) {
            case BOARD_MATCH_PLAY -> twPersonaStatsRepository.getMatchLeaderboardByVers(vers, count, start);
            case BOARD_STROKE_PLAY -> twPersonaStatsRepository.getStrokeLeaderboardByVers(vers, count, start);
            default -> twPersonaStatsRepository.getLeaderboardByVers(vers, count, start);
        };
        return page.stream().map(stats -> standing(stats, board)).toList();
    }

    /**
     * Whether a player appears on a head-to-head board
     *
     * @param stats The player's statistics
     * @param board The overall, Match Play or Stroke Play board
     * @return True if the player has played a match of that kind (or any match or event, for the overall board)
     */
    private static boolean inBoard(TwPersonaStatsEntity stats, int board) {
        int match = stats.getMatchComplete() + stats.getMatchIncomplete();
        int stroke = stats.getStrokeComplete() + stats.getStrokeIncomplete();
        return switch (board) {
            case BOARD_MATCH_PLAY -> match > 0;
            case BOARD_STROKE_PLAY -> stroke > 0;
            default -> match + stroke + stats.getEvents() > 0;
        };
    }

    /**
     * Get the last seven days' head-to-head results, totalled from the reports of that week
     *
     * @param vers The game version
     * @return The rows, most points first
     */
    private List<TwStanding> week(String vers) {
        Map<Long, TwPersonaStatsEntity> totals = new LinkedHashMap<>();
        Map<Long, List<TwGameReportEntity>> games = new LinkedHashMap<>();
        for (TwGameReportEntity report : twGameReportRepository.findByVersSince(vers, LocalDateTime.now().minusDays(WEEK_DAYS))) {
            games.computeIfAbsent(report.getGameConnection().getGame().getId(), gameId -> new ArrayList<>()).add(report);
        }
        for (List<TwGameReportEntity> reports : games.values()) {
            if (reports.size() != 2 || !isRanked(reports.getFirst().getGameConnection().getGame())) {
                continue;
            }
            TwPersonaStatsEntity[] stats = new TwPersonaStatsEntity[2];
            List<Map<String, Long>> cards = new ArrayList<>();
            for (TwGameReportEntity report : reports) {
                PersonaEntity persona = report.getGameConnection().getPersonaConnection().getPersona();
                if (persona.getDeletedOn() != null || persona.getAccount().isBanned()) {
                    stats = null;
                    break;
                }
                stats[report.getSide()] = totals.computeIfAbsent(persona.getId(), personaId -> newStats(persona, vers));
            }
            if (stats == null) {
                continue;
            }
            reports.sort(Comparator.comparingInt(TwGameReportEntity::getSide));
            reports.forEach(report -> cards.add(TwCardUtils.card(report)));
            applyMatch(isMatchPlay(reports.getFirst().getGameConnection().getGame()), cards, stats);
        }
        return ranked(totals.values(), stats -> stats.getMatchPoints() + stats.getStrokePoints());
    }

    /**
     * Get a statistic's list: the players who have a value for it, best first
     * The points are the value, or 2^width - value for a statistic ranked lowest first, which the game draws as the value
     *
     * @param vers  The game version
     * @param board The list index
     * @return The rows
     */
    private List<TwStanding> statBoard(String vers, int board) {
        int field = LIST_FIELDS[board];
        long top = 1L << TwStatsCodec.width(field);
        Map<TwPersonaStatsEntity, Long> points = new LinkedHashMap<>();
        for (TwPersonaStatsEntity stats : twPersonaStatsRepository.findRankedByVers(vers)) {
            long value = Math.min(values(stats)[field], top - 1);
            if (value > 0) {
                points.put(stats, LOWEST_FIRST.contains(field) ? top - value : value);
            }
        }
        return ranked(points.keySet(), points::get);
    }

    /**
     * Sort players by points, highest first, then by name; tied players share a rank
     *
     * @param stats  The players' statistics
     * @param points How many points each player has
     * @return The rows
     */
    private static List<TwStanding> ranked(Collection<TwPersonaStatsEntity> stats, ToLongFunction<TwPersonaStatsEntity> points) {
        List<TwPersonaStatsEntity> sorted = new ArrayList<>(stats);
        sorted.sort(Comparator.comparingLong(points).reversed()
                .thenComparing(playerStats -> playerStats.getPersona().getPers(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(playerStats -> playerStats.getPersona().getId()));
        List<TwStanding> result = new ArrayList<>();
        long previous = Long.MIN_VALUE;
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            TwPersonaStatsEntity playerStats = sorted.get(i);
            long value = points.applyAsLong(playerStats);
            if (value != previous) {
                rank = i + 1;
            }
            previous = value;
            result.add(standing(playerStats, rank, value));
        }
        return result;
    }
}
