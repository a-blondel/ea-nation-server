package com.ea.services.stats;

import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.entities.core.GameConnectionEntity;
import com.ea.entities.core.PersonaEntity;
import com.ea.entities.stats.FifaGameReportEntity;
import com.ea.entities.stats.FifaPersonaStatsEntity;
import com.ea.repositories.core.GameConnectionRepository;
import com.ea.repositories.stats.FifaGameReportRepository;
import com.ea.repositories.stats.FifaPersonaStatsRepository;
import com.ea.steps.SocketWriter;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.enums.LeaderboardLabel.MY_LEADERBOARD;
import static com.ea.enums.LeaderboardLabel.TOP_100;
import static com.ea.services.server.GameServerService.PSP_WOLRDCUP_06;
import static com.ea.utils.SocketUtils.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class FifaStatsService {

    private final SocketWriter socketWriter;
    private final GameConnectionRepository gameConnectionRepository;
    private final FifaPersonaStatsRepository fifaPersonaStatsRepository;
    private final FifaGameReportRepository fifaGameReportRepository;

    private static String getStats(boolean hasStats, FifaPersonaStatsEntity fifaPersonaStatsEntity) {
        long totalGames = hasStats ? (fifaPersonaStatsEntity.getWins() + fifaPersonaStatsEntity.getLosses() + fifaPersonaStatsEntity.getDraw()) : 0;
        long dnf = totalGames > 0 ? Math.round(((double) fifaPersonaStatsEntity.getQuit() + fifaPersonaStatsEntity.getDisc()) / totalGames * 100) : 0;
        dnf = Math.min(dnf, 100); // Ensure DNF percentage does not exceed 100

        if (hasStats && fifaPersonaStatsEntity.getVers().equals(PSP_WOLRDCUP_06)) {
            return ",," +
                    Long.toHexString(fifaPersonaStatsEntity.getWins()) +
                    "," +
                    Long.toHexString(fifaPersonaStatsEntity.getLosses()) +
                    ",,,,," +
                    Long.toHexString(dnf);
        } else {
            return "," +
                    (hasStats ? Long.toHexString(fifaPersonaStatsEntity.getWins()) : "0") +
                    "," +
                    (hasStats ? Long.toHexString(fifaPersonaStatsEntity.getLosses()) : "0") +
                    ",," +
                    (hasStats ? Long.toHexString(dnf) : "0") +
                    ",,,,";
        }
    }

    /**
     * Get stats and rank for a persona
     *
     * @param persona The persona entity
     * @param vers    The game version
     * @return Map containing stats and rank
     */
    public Map<String, String> getStatsAndRank(PersonaEntity persona, String vers) {
        FifaPersonaStatsEntity stats = fifaPersonaStatsRepository.findByPersonaIdAndVers(persona.getId(), vers);
        boolean hasStats = stats != null;

        String statsString = getStats(hasStats, stats);

        Long rank = hasStats ? fifaPersonaStatsRepository.getRankByPersonaIdAndVers(persona.getId(), vers) : null;
        String rankString = rank != null ? rank.toString() : "";

        Map<String, String> result = new HashMap<>();
        result.put("stats", statsString);
        result.put("rank", rankString);
        return result;
    }

    /**
     * Retrieve ranking categories
     *
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper
     */
    public void cate(SocketData socketData, SocketWrapper socketWrapper) {
        Map<String, String> content = Stream.of(new String[][]{
                {"CC", "2"}, // <total # of categories in this view>
                {"IC", "2"}, // <total # of indices in this view>
                {"VC", "2"}, // <total # of variations in this view>
                {"SYMS", "\"" + MY_LEADERBOARD.name + "\",\"" + TOP_100.name + "\""},
                {"SS", String.valueOf(MY_LEADERBOARD.name.length() + 1 + TOP_100.name.length() + 1)},
                {"R", "0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
    }

    /**
     * Request ranking snapshot
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void snap(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String chan = getValueFromSocket(socketData.getInputMessage(), "CHAN");
        String seqn = getValueFromSocket(socketData.getInputMessage(), "SEQN");
        String start = getValueFromSocket(socketData.getInputMessage(), "START");
        String ci = getValueFromSocket(socketData.getInputMessage(), "CI");

        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        List<FifaPersonaStatsEntity> fifaPersonaStatsEntityList;
        long offset = 0;

        int categoryIndex = Integer.parseInt(ci != null ? ci : "0");

        if (categoryIndex == 0) { // My Leaderboard
            Long rank = fifaPersonaStatsRepository.getRankByPersonaIdAndVers(socketWrapper.getPersonaEntity().getId(), vers);
            offset = (rank != null) ? Math.max(rank - 50, 0) : 0;
            fifaPersonaStatsEntityList = fifaPersonaStatsRepository.getLeaderboardByVers(vers, 100, offset);
        } else { // Top 100
            fifaPersonaStatsEntityList = fifaPersonaStatsRepository.getLeaderboardByVers(vers, 100, offset);
        }

        Map<String, String> content = Stream.of(new String[][]{
                {"CHAN", chan},
                {"START", start != null ? start : "0"},
                {"RANGE", String.valueOf(fifaPersonaStatsEntityList.size())},
                {"SEQN", seqn},
                {"CC", "11"},
                {"FC", "1"},
                {"DESC", ""},
                {"PARAMS", "1,1,1,1,1,1,1,1,1,1,1"},
                {"CN0", "RNK"},
                {"CD0", "\"Leaderboard Ranking\""},
                {"CN1", "Persona"},
                {"CD1", "Persona"},
                {"CN2", "Score"},
                {"CD2", "Score"},
                {"CN3", "\"Goals diff\""},
                {"CD3", "\"Goals difference\""},
                {"CN4", "Wins"},
                {"CD4", "Wins"},
                {"CN5", "Losses"},
                {"CD5", "Losses"},
                {"CN6", "\"Goals for\""},
                {"CD6", "\"Goals scored\""},
                {"CN7", "\"Goals against\""},
                {"CD7", "\"Goals conceded\""},
                {"CN8", "Draws"},
                {"CD8", "Draws"},
                {"CN9", "DNF"},
                {"CD9", "\"Did not finish\""},
                {"CN10", "\"DNF%\""},
                {"CD10", "\"Did not finish percentage\""},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        snp(socket, fifaPersonaStatsEntityList, offset);
    }

    /**
     * Send individual ranking snapshot entries
     *
     * @param socket                     The socket to write the response to
     * @param fifaPersonaStatsEntityList The list of FIFA persona stats entities
     * @param offset                     The offset for ranking calculation
     */
    public void snp(Socket socket, List<FifaPersonaStatsEntity> fifaPersonaStatsEntityList, long offset) {
        List<Map<String, String>> rankingList = new ArrayList<>();

        for (int i = 0; i < fifaPersonaStatsEntityList.size(); i++) {
            FifaPersonaStatsEntity stats = fifaPersonaStatsEntityList.get(i);
            long actualRank = offset + i + 1;
            String name = stats.getPersona().getPers();
            String points = String.valueOf(stats.getWins() - stats.getLosses());

            long totalGames = stats.getWins() + stats.getLosses() + stats.getDraw();
            long dnf = stats.getQuit() + stats.getDisc();
            long dnfPercent = totalGames > 0 ? Math.round(((double) dnf) / totalGames * 100) : 0;

            String statsString = String.join(",",
                    String.valueOf(actualRank),
                    name,
                    String.valueOf(stats.getWins() - stats.getLosses()),
                    String.valueOf(stats.getScore() - stats.getScoreAgainst()),
                    String.valueOf(stats.getWins()),
                    String.valueOf(stats.getLosses()),
                    String.valueOf(stats.getScore()),
                    String.valueOf(stats.getScoreAgainst()),
                    String.valueOf(stats.getDraw()),
                    String.valueOf(dnf),
                    String.valueOf(dnfPercent)
            );

            rankingList.add(Stream.of(new String[][]{
                    {"N", name},
                    {"R", String.valueOf(actualRank)},
                    {"P", points},
                    {"O", "0"},
                    {"S", statsString},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
        }

        for (Map<String, String> ranking : rankingList) {
            SocketData snpSocketData = new SocketData("+snp", null, ranking);
            socketWriter.write(socket, snpSocketData);
        }
    }

    /**
     * Send ranking results.
     *
     * @param socketData The socket data
     */
    @Transactional
    public void rank(SocketData socketData) {
        String startTime = getValueFromSocket(socketData.getInputMessage(), "WHEN", TAB_CHAR);
        String name0 = getValueFromSocket(socketData.getInputMessage(), "NAME0", TAB_CHAR);
        String name1 = getValueFromSocket(socketData.getInputMessage(), "NAME1", TAB_CHAR);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATETIME_FORMAT);
        LocalDateTime parsedStartTime = LocalDateTime.parse(startTime, formatter);

        // Find game connections for both players
        List<GameConnectionEntity> gameConnectionsPlayer0 = gameConnectionRepository.findMatchingGameConnections(name0, parsedStartTime, true);
        List<GameConnectionEntity> gameConnectionsPlayer1 = gameConnectionRepository.findMatchingGameConnections(name1, parsedStartTime, true);

        // Extract player stats from the packet
        Map<String, Object> player0Stats = extractPlayerStats(socketData.getInputMessage(), "0");
        Map<String, Object> player1Stats = extractPlayerStats(socketData.getInputMessage(), "1");

        // Process Player 0 if game connection found and report doesn't exist
        if (!gameConnectionsPlayer0.isEmpty()) {
            GameConnectionEntity gameConnection0 = gameConnectionsPlayer0.getFirst();
            if (!fifaGameReportRepository.existsById(gameConnection0.getId())) {
                // Create and save game report for Player 0
                FifaGameReportEntity gameReport0 = new FifaGameReportEntity();
                gameReport0.setGameConnection(gameConnection0);
                populateGameReportFromStats(gameReport0, player0Stats);
                fifaGameReportRepository.save(gameReport0);

                // Update persona stats if ranked
                int rnk = (Integer) player0Stats.get("RNK");
                if (rnk == 1) {
                    updatePersonaStats(gameConnection0, player0Stats, player1Stats);
                }
            }
        }

        // Process Player 1 if game connection found and report doesn't exist
        if (!gameConnectionsPlayer1.isEmpty()) {
            GameConnectionEntity gameConnection1 = gameConnectionsPlayer1.getFirst();
            if (!fifaGameReportRepository.existsById(gameConnection1.getId())) {
                // Create and save game report for Player 1
                FifaGameReportEntity gameReport1 = new FifaGameReportEntity();
                gameReport1.setGameConnection(gameConnection1);
                populateGameReportFromStats(gameReport1, player1Stats);
                fifaGameReportRepository.save(gameReport1);

                // Update persona stats if ranked
                int rnk = (Integer) player1Stats.get("RNK");
                if (rnk == 1) {
                    updatePersonaStats(gameConnection1, player1Stats, player0Stats);
                }
            }
        }
    }

    /**
     * Extract player stats from the rank packet
     *
     * @param inputMessage The input message containing the rank data
     * @param playerIndex  The player index ("0" or "1")
     * @return Map containing the player's stats
     */
    private Map<String, Object> extractPlayerStats(String inputMessage, String playerIndex) {
        Map<String, Object> stats = new HashMap<>();

        // Extract all stats for the specified player
        stats.put("TEAM", parseIntOrDefault(getValueFromSocket(inputMessage, "TEAM" + playerIndex, TAB_CHAR), 0));
        stats.put("SHT", parseIntOrDefault(getValueFromSocket(inputMessage, "SHT" + playerIndex, TAB_CHAR), 0));
        stats.put("PASM", parseIntOrDefault(getValueFromSocket(inputMessage, "PASM" + playerIndex, TAB_CHAR), 0));
        stats.put("PASS", parseIntOrDefault(getValueFromSocket(inputMessage, "PASS" + playerIndex, TAB_CHAR), 0));
        stats.put("COR", parseIntOrDefault(getValueFromSocket(inputMessage, "COR" + playerIndex, TAB_CHAR), 0));
        stats.put("OFF", parseIntOrDefault(getValueFromSocket(inputMessage, "OFF" + playerIndex, TAB_CHAR), 0));
        stats.put("POS", parseIntOrDefault(getValueFromSocket(inputMessage, "POS" + playerIndex, TAB_CHAR), 0));
        stats.put("TCM", parseIntOrDefault(getValueFromSocket(inputMessage, "TCM" + playerIndex, TAB_CHAR), 0));
        stats.put("TCS", parseIntOrDefault(getValueFromSocket(inputMessage, "TCS" + playerIndex, TAB_CHAR), 0));
        stats.put("FLS", parseIntOrDefault(getValueFromSocket(inputMessage, "FLS" + playerIndex, TAB_CHAR), 0));
        stats.put("YLW", parseIntOrDefault(getValueFromSocket(inputMessage, "YLW" + playerIndex, TAB_CHAR), 0));
        stats.put("RED", parseIntOrDefault(getValueFromSocket(inputMessage, "RED" + playerIndex, TAB_CHAR), 0));
        stats.put("DISC", parseIntOrDefault(getValueFromSocket(inputMessage, "DISC" + playerIndex, TAB_CHAR), 0));
        stats.put("QUIT", parseIntOrDefault(getValueFromSocket(inputMessage, "QUIT" + playerIndex, TAB_CHAR), 0));
        stats.put("CHEAT", parseIntOrDefault(getValueFromSocket(inputMessage, "CHEAT" + playerIndex, TAB_CHAR), 0));
        stats.put("SCORE", parseIntOrDefault(getValueFromSocket(inputMessage, "SCORE" + playerIndex, TAB_CHAR), 0));
        stats.put("WEIGHT", parseIntOrDefault(getValueFromSocket(inputMessage, "WEIGHT" + playerIndex, TAB_CHAR), -1));
        stats.put("DSCORE", parseIntOrDefault(getValueFromSocket(inputMessage, "DSCORE" + playerIndex, TAB_CHAR), 0));
        stats.put("HOME", parseIntOrDefault(getValueFromSocket(inputMessage, "HOME" + playerIndex, TAB_CHAR), 0));

        // Extract common fields
        stats.put("VENUE", parseIntOrDefault(getValueFromSocket(inputMessage, "VENUE", TAB_CHAR), 0));
        stats.put("TYPE", parseIntOrDefault(getValueFromSocket(inputMessage, "TYPE", TAB_CHAR), 0));
        stats.put("TIME", parseIntOrDefault(getValueFromSocket(inputMessage, "TIME", TAB_CHAR), 0));
        stats.put("SKIL", parseIntOrDefault(getValueFromSocket(inputMessage, "SKIL", TAB_CHAR), 0));
        stats.put("PLEN", parseIntOrDefault(getValueFromSocket(inputMessage, "PLEN", TAB_CHAR), 0));
        stats.put("PNUM", parseIntOrDefault(getValueFromSocket(inputMessage, "PNUM", TAB_CHAR), 0));
        stats.put("RNK", parseIntOrDefault(getValueFromSocket(inputMessage, "RNK", TAB_CHAR), 0));
        stats.put("PK", parseIntOrDefault(getValueFromSocket(inputMessage, "PK", TAB_CHAR), 0));
        stats.put("GLD", parseIntOrDefault(getValueFromSocket(inputMessage, "GLD", TAB_CHAR), 0));
        stats.put("DTIME", parseIntOrDefault(getValueFromSocket(inputMessage, "DTIME", TAB_CHAR), 0));

        return stats;
    }

    /**
     * Populate game report from extracted stats
     *
     * @param gameReport The game report to populate
     * @param stats      The extracted stats
     */
    private void populateGameReportFromStats(FifaGameReportEntity gameReport, Map<String, Object> stats) {
        gameReport.setVenue((Integer) stats.get("VENUE"));
        gameReport.setTeam((Integer) stats.get("TEAM"));
        gameReport.setPk((Integer) stats.get("PK"));
        gameReport.setGld((Integer) stats.get("GLD"));
        gameReport.setSht((Integer) stats.get("SHT"));
        gameReport.setPasm((Integer) stats.get("PASM"));
        gameReport.setPass((Integer) stats.get("PASS"));
        gameReport.setCor((Integer) stats.get("COR"));
        gameReport.setOff((Integer) stats.get("OFF"));
        gameReport.setPos((Integer) stats.get("POS"));
        gameReport.setTcm((Integer) stats.get("TCM"));
        gameReport.setTcs((Integer) stats.get("TCS"));
        gameReport.setFls((Integer) stats.get("FLS"));
        gameReport.setYlw((Integer) stats.get("YLW"));
        gameReport.setRed((Integer) stats.get("RED"));
        gameReport.setTime((Integer) stats.get("TIME"));
        gameReport.setType((Integer) stats.get("TYPE"));
        gameReport.setSkil((Integer) stats.get("SKIL"));
        gameReport.setPlen((Integer) stats.get("PLEN"));
        gameReport.setPnum((Integer) stats.get("PNUM"));
        gameReport.setDisc((Integer) stats.get("DISC"));
        gameReport.setQuit((Integer) stats.get("QUIT"));
        gameReport.setCheat((Integer) stats.get("CHEAT"));
        gameReport.setDtime((Integer) stats.get("DTIME"));
        gameReport.setScore((Integer) stats.get("SCORE"));
        gameReport.setWeight((Integer) stats.get("WEIGHT"));
        gameReport.setDscore((Integer) stats.get("DSCORE"));
        gameReport.setHome((Integer) stats.get("HOME"));
        gameReport.setRnk((Integer) stats.get("RNK"));
    }

    /**
     * Update persona stats based on game report
     */
    private void updatePersonaStats(GameConnectionEntity gameConnection, Map<String, Object> playerStats, Map<String, Object> opponentStats) {
        PersonaEntity persona = gameConnection.getPersonaConnection().getPersona();
        String vers = gameConnection.getPersonaConnection().getVers();
        String slus = gameConnection.getPersonaConnection().getSlus();

        FifaPersonaStatsEntity stats = fifaPersonaStatsRepository.findByPersonaIdAndVers(persona.getId(), vers);

        if (stats == null) {
            stats = new FifaPersonaStatsEntity();
            stats.setPersona(persona);
            stats.setVers(vers);
            stats.setSlus(slus);
        }

        int playerScore = (Integer) playerStats.get("SCORE");
        int playerQuit = (Integer) playerStats.get("QUIT");
        int playerDisc = (Integer) playerStats.get("DISC");
        int opponentScore = (Integer) opponentStats.get("SCORE");
        int opponentQuit = (Integer) opponentStats.get("QUIT");
        int opponentDisc = (Integer) opponentStats.get("DISC");

        if (playerScore > opponentScore && playerQuit == 0 && playerDisc == 0) {
            stats.setWins(stats.getWins() + 1);
        } else if (playerScore < opponentScore && opponentQuit == 0 && opponentDisc == 0) {
            stats.setLosses(stats.getLosses() + 1);
        } else {
            stats.setDraw(stats.getDraw() + 1);
        }

        stats.setScore(stats.getScore() + playerScore);
        stats.setScoreAgainst(stats.getScoreAgainst() + opponentScore);
        stats.setSht(stats.getSht() + (Integer) playerStats.get("SHT"));
        stats.setPasm(stats.getPasm() + (Integer) playerStats.get("PASM"));
        stats.setPass(stats.getPass() + (Integer) playerStats.get("PASS"));
        stats.setCor(stats.getCor() + (Integer) playerStats.get("COR"));
        stats.setOff(stats.getOff() + (Integer) playerStats.get("OFF"));
        stats.setPos(stats.getPos() + (Integer) playerStats.get("POS"));
        stats.setTcm(stats.getTcm() + (Integer) playerStats.get("TCM"));
        stats.setTcs(stats.getTcs() + (Integer) playerStats.get("TCS"));
        stats.setFls(stats.getFls() + (Integer) playerStats.get("FLS"));
        stats.setYlw(stats.getYlw() + (Integer) playerStats.get("YLW"));
        stats.setRed(stats.getRed() + (Integer) playerStats.get("RED"));
        stats.setTime(stats.getTime() + (Integer) playerStats.get("TIME"));
        stats.setSkil(stats.getSkil() + (Integer) playerStats.get("SKIL"));
        stats.setDisc(stats.getDisc() + (Integer) playerStats.get("DISC"));
        stats.setQuit(stats.getQuit() + (Integer) playerStats.get("QUIT"));
        stats.setCheat(stats.getCheat() + (Integer) playerStats.get("CHEAT"));
        stats.setWeight(stats.getWeight() + (Integer) playerStats.get("WEIGHT"));

        if ((Integer) playerStats.get("HOME") == 1) {
            stats.setHome(stats.getHome() + 1);
        } else {
            stats.setAway(stats.getAway() + 1);
        }

        fifaPersonaStatsRepository.save(stats);
    }

    /**
     * Parse integer value or return default
     */
    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
