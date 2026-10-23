package eu.eahub.services.stats;

import eu.eahub.dirtysdk.TwStatsCodec;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.GameConnectionEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.entities.stats.TwGameReportEntity;
import eu.eahub.entities.stats.TwPersonaStatsEntity;
import eu.eahub.repositories.core.GameConnectionRepository;
import eu.eahub.repositories.core.GameRepository;
import eu.eahub.repositories.core.PersonaRepository;
import eu.eahub.repositories.stats.TwGameReportRepository;
import eu.eahub.repositories.stats.TwPersonaStatsRepository;
import eu.eahub.utils.TwCardUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import static eu.eahub.dirtysdk.TwStatsCodec.Field;
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

    // WHEN as reported back by the game: the session start time it was sent, in UTC
    private static final DateTimeFormatter WHEN_FORMAT = DateTimeFormatter.ofPattern("yyyy.M.d H:mm:ss");
    public static final List<String> CARD_FIELDS = List.of("RANK", "DONE", "QUIT", "TYPE", "SCORE", "CLUB", "STROKES", "PUTTS", "HOLES",
            "EAGS", "BIRD", "ACES", "GIR", "LPUT", "DRVS", "FRWY", "LDRV", "SHTC", "PARS", "SBOG", "DBOG", "TBOG", "COMP");
    private static final int[] TIGER_STATUS_POINTS = {50, 100, 200, 500};

    private final TwGameReportRepository twGameReportRepository;
    private final TwPersonaStatsRepository twPersonaStatsRepository;
    private final GameConnectionRepository gameConnectionRepository;
    private final GameRepository gameRepository;
    private final PersonaRepository personaRepository;

    public enum Receipt {
        SAVED,
        DUPLICATE,
        UNAUTHORIZED,
        INVALID
    }

    public record Result(Receipt receipt, GameEntity game) {
    }

    public record Standing(long id, String name, int rank, long points, long[] values) {

        public String packed() {
            return TwStatsCodec.pack(values);
        }
    }

    /**
     * Record a player's report of a head-to-head match (rank)
     * The match is found from the reporter and WHEN, the start time sent in +ses. Synchronized, and the game row is
     * locked, so that both players reporting at the same time still count the match once
     *
     * @param reporter       the reporting player's socket wrapper
     * @param fields         the report fields
     * @param expectedGameId the game the report's AUTH token was issued for, or null if unknown (e.g. after a restart)
     * @return the receipt and the match
     */
    @Transactional
    public synchronized Result rank(SocketWrapper reporter, Map<String, String> fields, Long expectedGameId) {
        if (!valid(fields)) {
            return new Result(Receipt.INVALID, null);
        }
        Optional<GameEntity> found = findGame(reporter.getPersonaEntity(), fields.get("WHEN"));
        if (found.isEmpty() || (expectedGameId != null && !expectedGameId.equals(found.get().getId()))) {
            return new Result(Receipt.UNAUTHORIZED, null);
        }
        // Within a transaction, the lock makes a second report wait for the first one to be committed
        GameEntity game = gameRepository.findForUpdateById(found.get().getId()).orElse(found.get());
        GameConnectionEntity[] sides = sides(game);
        if (sides == null) {
            return new Result(Receipt.UNAUTHORIZED, null);
        }
        if (twGameReportRepository.existsByGameConnectionGameId(game.getId())) {
            return new Result(Receipt.DUPLICATE, game);
        }
        List<Map<String, Long>> cards = new ArrayList<>();
        for (int side = 0; side < 2; side++) {
            Map<String, Long> card = card(fields, side);
            cards.add(card);
            TwGameReportEntity report = new TwGameReportEntity();
            report.setGameConnection(sides[side]);
            report.setSide(side);
            setCard(report, card);
            twGameReportRepository.save(report);
        }
        TwPersonaStatsEntity[] stats = new TwPersonaStatsEntity[2];
        for (int side = 0; side < 2; side++) {
            stats[side] = getOrCreateStats(sides[side].getPersonaConnection().getPersona(), game.getVers());
        }
        applyMatch(isMatchPlay(game), cards, stats);
        twPersonaStatsRepository.saveAll(List.of(stats));
        return new Result(Receipt.SAVED, game);
    }

    private Optional<GameEntity> findGame(PersonaEntity reporter, String when) {
        LocalDateTime startTime;
        try {
            startTime = LocalDateTime.parse(when, WHEN_FORMAT).atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException | NullPointerException e) {
            return Optional.empty();
        }
        return gameConnectionRepository.findMatchingGameConnections(reporter.getPers(), startTime, true).stream()
                .map(GameConnectionEntity::getGame)
                .filter(game -> ALL_TW.contains(game.getVers()))
                .findFirst();
    }

    /**
     * Get the host (side 0) and guest (side 1) connections of a match
     */
    private static GameConnectionEntity[] sides(GameEntity game) {
        GameConnectionEntity[] sides = new GameConnectionEntity[2];
        for (GameConnectionEntity connection : game.getGameConnections()) {
            sides[connection.isHost() ? 0 : 1] = connection;
        }
        return sides[0] == null || sides[1] == null ? null : sides;
    }

    public static boolean isMatchPlay(GameEntity game) {
        return game.getName() != null && game.getName().startsWith("Match.");
    }

    private static boolean valid(Map<String, String> fields) {
        if (fields.size() > 128 || fields.values().stream().anyMatch(v -> v == null || v.length() > 1024)) {
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
                    if (value < (key.equals("SCORE") ? -1000 : 0) || value > 10000) {
                        return false;
                    }
                }
                for (String key : List.of("PUTTS", "GIR", "FRWY", "DRVS", "LDRV", "LPUT", "EAGS", "BIRD", "ACES")) {
                    int value = Integer.parseInt(fields.getOrDefault(key + side, "0"));
                    if (value < 0 || value > 100000) {
                        return false;
                    }
                }
                if (Integer.parseInt(fields.get("HOLES" + side)) > 18) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Map<String, Long> card(Map<String, String> fields, int side) {
        Map<String, Long> card = new LinkedHashMap<>();
        for (String key : CARD_FIELDS) {
            try {
                card.put(key, Long.parseLong(fields.getOrDefault(key + side, "0").strip()));
            } catch (NumberFormatException e) {
                card.put(key, 0L);
            }
        }
        return card;
    }

    private static void setCard(TwGameReportEntity report, Map<String, Long> card) {
        report.setRnk(card.get("RANK").intValue());
        report.setDone(card.get("DONE").intValue());
        report.setQuit(card.get("QUIT").intValue());
        report.setType(card.get("TYPE").intValue());
        report.setScore(card.get("SCORE").intValue());
        report.setClub(card.get("CLUB").intValue());
        report.setStrokes(card.get("STROKES").intValue());
        report.setPutts(card.get("PUTTS").intValue());
        report.setHoles(card.get("HOLES").intValue());
        report.setEags(card.get("EAGS").intValue());
        report.setBird(card.get("BIRD").intValue());
        report.setAces(card.get("ACES").intValue());
        report.setGir(card.get("GIR").intValue());
        report.setLput(card.get("LPUT").intValue());
        report.setDrvs(card.get("DRVS").intValue());
        report.setFrwy(card.get("FRWY").intValue());
        report.setLdrv(card.get("LDRV").intValue());
        report.setShtc(card.get("SHTC").intValue());
        report.setPars(card.get("PARS").intValue());
        report.setSbog(card.get("SBOG").intValue());
        report.setDbog(card.get("DBOG").intValue());
        report.setTbog(card.get("TBOG").intValue());
        report.setComp(card.get("COMP").intValue());
    }

    /**
     * Get a stored report as card fields (STROKES, HOLES, ...)
     *
     * @param report the report
     * @return the card fields
     */
    public static Map<String, Long> card(TwGameReportEntity report) {
        Map<String, Long> card = new LinkedHashMap<>();
        int[] values = {report.getRnk(), report.getDone(), report.getQuit(), report.getType(), report.getScore(), report.getClub(),
                report.getStrokes(), report.getPutts(), report.getHoles(), report.getEags(), report.getBird(), report.getAces(),
                report.getGir(), report.getLput(), report.getDrvs(), report.getFrwy(), report.getLdrv(), report.getShtc(),
                report.getPars(), report.getSbog(), report.getDbog(), report.getTbog(), report.getComp()};
        for (int i = 0; i < values.length; i++) {
            card.put(CARD_FIELDS.get(i), (long) values[i]);
        }
        return card;
    }

    public TwPersonaStatsEntity getOrCreateStats(PersonaEntity persona, String vers) {
        return twPersonaStatsRepository.findByPersonaIdAndVers(persona.getId(), vers).orElseGet(() -> newStats(persona, vers));
    }

    private static TwPersonaStatsEntity newStats(PersonaEntity persona, String vers) {
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
     * @param matchPlay true for Match Play, false for Stroke Play
     * @param cards     both players' card fields
     * @param stats     both players' statistics, updated in place
     */
    static void applyMatch(boolean matchPlay, List<Map<String, Long>> cards, TwPersonaStatsEntity[] stats) {
        if (cards.get(0).get("HOLES") == 0 && cards.get(1).get("HOLES") == 0) {
            return;
        }
        boolean quit0 = cards.get(0).get("QUIT") != 0;
        boolean quit1 = cards.get(1).get("QUIT") != 0;
        boolean finished = cards.get(0).get("DONE") != 0 && cards.get(1).get("DONE") != 0 && !quit0 && !quit1;
        boolean forfeit = quit0 != quit1;
        int winner = -1;
        if (forfeit) {
            winner = quit0 ? 1 : 0;
        } else if (finished) {
            int comparison = matchPlay
                    ? Long.compare(cards.get(0).get("SCORE"), cards.get(1).get("SCORE"))
                    : Long.compare(cards.get(1).get("STROKES"), cards.get(0).get("STROKES"));
            winner = comparison > 0 ? 0 : comparison < 0 ? 1 : -1;
        }
        for (int side = 0; side < 2; side++) {
            TwPersonaStatsEntity s = stats[side];
            boolean quit = side == 0 ? quit0 : quit1;
            if (finished || forfeit) {
                if (winner == -1) {
                    if (matchPlay) {
                        s.setMatchTies(s.getMatchTies() + 1);
                        s.setMatchPoints(s.getMatchPoints() + 1);
                        s.setMatchStreak(0);
                    } else {
                        s.setStrokeTies(s.getStrokeTies() + 1);
                        s.setStrokePoints(s.getStrokePoints() + 1);
                        s.setStrokeStreak(0);
                    }
                } else if (winner == side) {
                    if (matchPlay) {
                        s.setMatchWins(s.getMatchWins() + 1);
                        s.setMatchPoints(s.getMatchPoints() + 2);
                        s.setMatchStreak(Math.max(0, s.getMatchStreak()) + 1);
                    } else {
                        s.setStrokeWins(s.getStrokeWins() + 1);
                        s.setStrokePoints(s.getStrokePoints() + 2);
                        s.setStrokeStreak(Math.max(0, s.getStrokeStreak()) + 1);
                    }
                } else if (matchPlay) {
                    s.setMatchLosses(s.getMatchLosses() + 1);
                    s.setMatchStreak(Math.min(0, s.getMatchStreak()) - 1);
                } else {
                    s.setStrokeLosses(s.getStrokeLosses() + 1);
                    s.setStrokeStreak(Math.min(0, s.getStrokeStreak()) - 1);
                }
                addCompletion(s, matchPlay, !quit);
            } else {
                addCompletion(s, matchPlay, false);
            }
            if (finished) {
                addGolf(s, cards.get(side));
            }
        }
    }

    private static void addCompletion(TwPersonaStatsEntity s, boolean matchPlay, boolean complete) {
        if (matchPlay && complete) {
            s.setMatchComplete(s.getMatchComplete() + 1);
        } else if (matchPlay) {
            s.setMatchIncomplete(s.getMatchIncomplete() + 1);
        } else if (complete) {
            s.setStrokeComplete(s.getStrokeComplete() + 1);
        } else {
            s.setStrokeIncomplete(s.getStrokeIncomplete() + 1);
        }
    }

    /**
     * Add a round's golf to a player's statistics
     * Impossible values are dropped one field at a time; each rate only divides by the holes or drives whose value
     * survived. Scoring figures only use full rounds: a Match Play card closed out early, or one with no stroke total,
     * is golf but not a "round"
     *
     * @param s   the player's statistics, updated in place
     * @param raw the card fields as reported
     */
    public static void addGolf(TwPersonaStatsEntity s, Map<String, Long> raw) {
        Map<String, Long> f = TwCardUtils.clean(raw);
        long played = f.getOrDefault("HOLES", 0L);
        s.setHoles(s.getHoles() + (int) played);
        if (f.containsKey("PUTTS")) {
            s.setPutts(s.getPutts() + f.get("PUTTS").intValue());
            s.setPuttHoles(s.getPuttHoles() + (int) played);
        }
        if (f.containsKey("GIR")) {
            s.setGir(s.getGir() + f.get("GIR").intValue());
            s.setGirHoles(s.getGirHoles() + (int) played);
        }
        if (f.containsKey("FRWY")) {
            s.setFairways(s.getFairways() + f.get("FRWY").intValue());
            s.setDrives(s.getDrives() + f.get("DRVS").intValue());
        }
        s.setEagles(s.getEagles() + f.getOrDefault("EAGS", 0L).intValue());
        s.setBirdies(s.getBirdies() + f.getOrDefault("BIRD", 0L).intValue());
        s.setAces(s.getAces() + f.getOrDefault("ACES", 0L).intValue());
        s.setLongestPutt(Math.max(s.getLongestPutt(), f.getOrDefault("LPUT", 0L).intValue()));
        s.setLongestDrive(Math.max(s.getLongestDrive(), f.getOrDefault("LDRV", 0L).intValue()));
        Long strokes = f.get("STROKES");
        if (strokes != null && played == 18) {
            s.setRounds(s.getRounds() + 1);
            s.setRoundStrokes(s.getRoundStrokes() + strokes.intValue());
            s.setRoundBirdies(s.getRoundBirdies() + f.getOrDefault("BIRD", 0L).intValue());
            s.setBestRound(s.getBestRound() == null ? strokes.intValue() : Math.min(s.getBestRound(), strokes.intValue()));
        }
    }

    /**
     * Convert statistics to the game's 56 values
     *
     * @param s the player's statistics, or null if the player has none
     * @return the values to pack
     */
    public static long[] values(TwPersonaStatsEntity s) {
        long[] v = TwStatsCodec.defaults();
        if (s == null) {
            return v;
        }
        long points = s.getMatchPoints() + s.getStrokePoints();
        set(v, Field.POINTS, points);
        set(v, Field.MATCH_POINTS, s.getMatchPoints());
        set(v, Field.STROKE_POINTS, s.getStrokePoints());
        long status = 0;
        for (int threshold : TIGER_STATUS_POINTS) {
            if (points >= threshold) {
                status++;
            }
        }
        set(v, Field.TIGER_STATUS, status);
        set(v, Field.MATCH_WINS, s.getMatchWins());
        set(v, Field.MATCH_LOSSES, s.getMatchLosses());
        set(v, Field.MATCH_TIES, s.getMatchTies());
        set(v, Field.STROKE_WINS, s.getStrokeWins());
        set(v, Field.STROKE_LOSSES, s.getStrokeLosses());
        set(v, Field.STROKE_TIES, s.getStrokeTies());
        set(v, Field.STROKE_STREAK, s.getStrokeStreak());
        set(v, Field.MATCH_STREAK, s.getMatchStreak());
        set(v, Field.STROKE_INCOMPLETE, s.getStrokeIncomplete());
        set(v, Field.MATCH_INCOMPLETE, s.getMatchIncomplete());
        set(v, Field.STROKE_COMPLETE, s.getStrokeComplete());
        set(v, Field.MATCH_COMPLETE, s.getMatchComplete());
        set(v, Field.EVENTS, s.getEvents());
        set(v, Field.EVENT_WINS, s.getEventWins());
        set(v, Field.TOP10, s.getTop10());
        set(v, Field.TOP25, s.getTop25());
        set(v, Field.EARNINGS, s.getEarnings() / 100);
        set(v, Field.EAGLES, s.getEagles());
        set(v, Field.BIRDIES, s.getBirdies());
        set(v, Field.ACES, s.getAces());
        set(v, Field.GIR, s.getGir());
        set(v, Field.LONGEST_PUTT, s.getLongestPutt());
        set(v, Field.FAIRWAYS, s.getFairways());
        set(v, Field.LONGEST_DRIVE, s.getLongestDrive());
        if (s.getGirHoles() > 0) {
            set(v, Field.GIR_PERCENT, Math.min(100, Math.round(100.0 * s.getGir() / s.getGirHoles())));
        }
        if (s.getPuttHoles() > 0) {
            set(v, Field.PUTTS_PER_HOLE, Math.round(100.0 * s.getPutts() / s.getPuttHoles()));
        }
        if (s.getDrives() > 0) {
            set(v, Field.DRIVING_ACCURACY, Math.min(100, Math.round(100.0 * s.getFairways() / s.getDrives())));
        }
        if (s.getEagles() > 0) {
            set(v, Field.HOLES_PER_EAGLE, Math.round((double) s.getHoles() / s.getEagles()));
        }
        if (s.getRounds() > 0) {
            set(v, Field.BEST_ROUND, s.getBestRound());
            set(v, Field.SCORING_AVERAGE, Math.round((double) s.getRoundStrokes() / s.getRounds()));
            set(v, Field.BIRDIE_AVERAGE, Math.round((double) s.getRoundBirdies() / s.getRounds()));
        }
        return v;
    }

    private static void set(long[] values, Field field, long value) {
        values[field.index] = value;
    }

    /**
     * Get a player's overall standing
     *
     * @param persona the player
     * @param vers    the game version
     * @return the standing, unranked if the player has no results
     */
    public Standing standing(PersonaEntity persona, String vers) {
        return twPersonaStatsRepository.findByPersonaIdAndVers(persona.getId(), vers)
                .map(s -> standing(s, 0))
                .orElseGet(() -> new Standing(persona.getId(), persona.getPers(), 0, 0, values(null)));
    }

    /**
     * Get the overall standings of several players, e.g. everyone in a room
     *
     * @param personas the players
     * @param vers     the game version
     * @return the standings by persona id
     */
    public Map<Long, Standing> standings(Collection<PersonaEntity> personas, String vers) {
        Map<Long, Standing> result = new HashMap<>();
        for (PersonaEntity persona : personas) {
            result.put(persona.getId(), new Standing(persona.getId(), persona.getPers(), 0, 0, values(null)));
        }
        for (TwPersonaStatsEntity s : twPersonaStatsRepository.findByPersonaIdInAndVers(result.keySet(), vers)) {
            result.put(s.getPersona().getId(), standing(s, 0));
        }
        return result;
    }

    /**
     * Get the stats and rank shown in the +usr record and onln reply (as in the other games' stats services)
     *
     * @param persona the player
     * @param vers    the game version
     * @return "stats" and "rank"
     */
    public Map<String, String> getStatsAndRank(PersonaEntity persona, String vers) {
        Standing standing = standing(persona, vers);
        return Map.of("stats", standing.packed(), "rank", String.valueOf(standing.rank()));
    }

    /**
     * @param board 0 = overall, 13 = Match Play, 14 = Stroke Play
     */
    private Standing standing(TwPersonaStatsEntity s, int board) {
        long points = switch (board) {
            case 13 -> s.getMatchPoints();
            case 14 -> s.getStrokePoints();
            default -> s.getMatchPoints() + s.getStrokePoints();
        };
        long rank = switch (board) {
            case 13 -> twPersonaStatsRepository.getMatchRankByPoints(s.getVers(), s.getMatchPoints());
            case 14 -> twPersonaStatsRepository.getStrokeRankByPoints(s.getVers(), s.getStrokePoints());
            default -> s.getMatchComplete() + s.getMatchIncomplete() + s.getStrokeComplete() + s.getStrokeIncomplete() + s.getEvents() > 0
                    ? twPersonaStatsRepository.getRankByPoints(s.getVers(), (int) points) : 0;
        };
        return new Standing(s.getPersona().getId(), s.getPersona().getPers(), (int) rank, points, values(s));
    }

    /**
     * Get a page of a head-to-head leaderboard
     *
     * @param vers  the game version
     * @param board 0 = overall, 13 = Match Play, 14 = Stroke Play, 32 = last seven days
     * @param start the first row (from 0)
     * @param count the number of rows
     * @param find  a player name to show alone, or empty
     * @return the rows
     */
    public List<Standing> board(String vers, int board, int start, int count, String find) {
        if (board == 32) {
            List<Standing> week = week(vers);
            return find.isEmpty()
                    ? week.stream().skip(start).limit(count).toList()
                    : week.stream().filter(row -> row.name().equalsIgnoreCase(find)).limit(count).toList();
        }
        if (!find.isEmpty()) {
            Optional<TwPersonaStatsEntity> found = personaRepository.findByPers(find)
                    .flatMap(persona -> twPersonaStatsRepository.findByPersonaIdAndVers(persona.getId(), vers));
            return found.filter(stats -> inBoard(stats, board)).map(stats -> List.of(standing(stats, board))).orElse(List.of());
        }
        List<TwPersonaStatsEntity> page = switch (board) {
            case 13 -> twPersonaStatsRepository.getMatchLeaderboardByVers(vers, count, start);
            case 14 -> twPersonaStatsRepository.getStrokeLeaderboardByVers(vers, count, start);
            default -> twPersonaStatsRepository.getLeaderboardByVers(vers, count, start);
        };
        return page.stream().map(s -> standing(s, board)).toList();
    }

    private static boolean inBoard(TwPersonaStatsEntity s, int board) {
        int match = s.getMatchComplete() + s.getMatchIncomplete();
        int stroke = s.getStrokeComplete() + s.getStrokeIncomplete();
        return switch (board) {
            case 13 -> match > 0;
            case 14 -> stroke > 0;
            default -> match + stroke + s.getEvents() > 0;
        };
    }

    /**
     * The last seven days' head-to-head results, totalled from the reports of that week
     */
    private List<Standing> week(String vers) {
        Map<Long, TwPersonaStatsEntity> totals = new LinkedHashMap<>();
        Map<Long, List<TwGameReportEntity>> games = new LinkedHashMap<>();
        for (TwGameReportEntity report : twGameReportRepository.findByVersSince(vers, LocalDateTime.now().minusDays(7))) {
            games.computeIfAbsent(report.getGameConnection().getGame().getId(), id -> new ArrayList<>()).add(report);
        }
        for (List<TwGameReportEntity> reports : games.values()) {
            if (reports.size() != 2) {
                continue;
            }
            TwPersonaStatsEntity[] stats = new TwPersonaStatsEntity[2];
            List<Map<String, Long>> cards = new ArrayList<>();
            for (TwGameReportEntity report : reports) {
                PersonaEntity persona = report.getGameConnection().getPersonaConnection().getPersona();
                stats[report.getSide()] = totals.computeIfAbsent(persona.getId(), id -> newStats(persona, vers));
            }
            reports.sort(Comparator.comparingInt(TwGameReportEntity::getSide));
            reports.forEach(report -> cards.add(card(report)));
            applyMatch(isMatchPlay(reports.getFirst().getGameConnection().getGame()), cards, stats);
        }
        List<TwPersonaStatsEntity> sorted = new ArrayList<>(totals.values());
        sorted.sort(Comparator.comparingLong((TwPersonaStatsEntity s) -> s.getMatchPoints() + s.getStrokePoints()).reversed()
                .thenComparing(s -> s.getPersona().getPers(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(s -> s.getPersona().getId()));
        List<Standing> result = new ArrayList<>();
        long previous = Long.MIN_VALUE;
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            TwPersonaStatsEntity s = sorted.get(i);
            long points = s.getMatchPoints() + s.getStrokePoints();
            if (points != previous) {
                rank = i + 1;
            }
            previous = points;
            result.add(new Standing(s.getPersona().getId(), s.getPersona().getPers(), rank, points, values(s)));
        }
        return result;
    }
}
