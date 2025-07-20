package com.ea.services.stats;

import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.entities.core.GameConnectionEntity;
import com.ea.entities.core.PersonaEntity;
import com.ea.entities.stats.NhlGameReportEntity;
import com.ea.entities.stats.NhlPersonaStatsEntity;
import com.ea.repositories.core.GameConnectionRepository;
import com.ea.repositories.stats.NhlGameReportRepository;
import com.ea.repositories.stats.NhlPersonaStatsRepository;
import com.ea.steps.SocketWriter;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.utils.SocketUtils.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class NhlStatsService {

    private final NhlPersonaStatsRepository nhlPersonaStatsRepository;
    private final NhlGameReportRepository nhlGameReportRepository;
    private final GameConnectionRepository gameConnectionRepository;
    private final SocketWriter socketWriter;

    /**
     * Get stats in hex format
     *
     * @param totalGames            Total number of games played
     * @param nhlPersonaStatsEntity The NHL persona stats entity
     * @param hasStats              Whether the persona has stats
     * @return A string containing the stats in hex format
     */
    private static String getStats(long totalGames, NhlPersonaStatsEntity nhlPersonaStatsEntity, boolean hasStats) {
        long dnf = totalGames > 0 ? Math.round((double) nhlPersonaStatsEntity.getQuit() / totalGames * 100) : 0;
        dnf = Math.min(dnf, 100); // Ensure DNF percentage does not exceed 100

        // Hex values. 2nd value is wins, 3rd is losses, 5ths is DNF (Did Not Finish Percentage)
        return "," +
                (hasStats ? Long.toHexString(nhlPersonaStatsEntity.getWins() + nhlPersonaStatsEntity.getOtWins()) : "0") +
                "," +
                (hasStats ? Long.toHexString(nhlPersonaStatsEntity.getLosses() + nhlPersonaStatsEntity.getOtLosses()) : "0") +
                ",," +
                (hasStats ? Long.toHexString(dnf) : "0") +
                ",,,,";
    }

    /**
     * Retrieve ranking categories
     *
     * @param socketData The socket data
     */
    public void cate(SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][]{
                {"CC", "2"}, // <total # of categories in this view>
                {"IC", "2"}, // <total # of indices in this view>
                {"VC", "2"}, // <total # of variations in this view>
                {"U", "2"},
                {"SYMS", "2"},
                {"SS", "2"},
                {"R", String.join(",", Collections.nCopies(22, "1"))}, // <comma-separated-list of category,index,view data>
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
        //String cols = getValueFromSocket(socketData.getInputMessage(), "COLS"); // send column information or not
        String start = getValueFromSocket(socketData.getInputMessage(), "START"); // <start ranking> (index)
        //String categoryIndex = getValueFromSocket(socketData.getInputMessage(), "CI"); // <category-index>

        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        List<NhlPersonaStatsEntity> nhlPersonaStatsEntityList = nhlPersonaStatsRepository.getLeaderboardByVers(vers, 100, 0);

        Map<String, String> content = Stream.of(new String[][]{
                {"CHAN", chan}, // <matching request value>
                {"START", start}, // <actual start used>
                {"RANGE", String.valueOf(nhlPersonaStatsEntityList.size())}, // <actual range used>
                {"SEQN", seqn}, // <value provided in request>
                {"CC", "23"}, // <number of columns>
                {"FC", "1"}, // <number of fixed columns>
                {"DESC", ""}, // <list-description>
                {"PARAMS", "1,1,1,1,1,1,1,1,1,1,1,1"}, // <comma-separated list of integer parameters>
                {"CN0", "RNK"},
                {"CD0", "\"Leaderboard Ranking\""},
                {"CN1", "Persona"},
                {"CD1", "Persona"},
                {"CN2", "Points"},
                {"CD2", "Points"},
                {"CN3", "\"Goal diff\""},
                {"CD3", "\"Goal difference\""},
                {"CN4", "Wins"},
                {"CD4", "Wins"},
                {"CN5", "\"OT wins\""},
                {"CD5", "\"Overtime wins\""},
                {"CN6", "Losses"},
                {"CD6", "Losses"},
                {"CN7", "\"OT losses\""},
                {"CD7", "\"Overtime losses\""},
                {"CN8", "Streak"},
                {"CD8", "Streak"},
                {"CN9", "Win%"},
                {"CD9", "\"Win percentage\""},
                {"CN10", "Draws"},
                {"CD10", "Draws"},
                {"CN11", "DNF"},
                {"CD11", "\"Did not finish\""},
                {"CN12", "DNF%"},
                {"CD12", "\"Did not finish percentage\""},
                {"CN13", "Shots"},
                {"CD13", "Shots"},
                {"CN14", "Goals"},
                {"CD14", "Goals"},
                {"CN15", "\"Goals against\""},
                {"CD15", "\"Goals against\""},
                {"CN16", "Hits"},
                {"CD16", "Hits"},
                {"CN17", "Penalties"},
                {"CD17", "\"Penalties minutes\""},
                {"CN18", "PPG"},
                {"CD18", "\"Power play goals\""},
                {"CN19", "PPO"},
                {"CD19", "\"Power play opportunities\""},
                {"CN20", "SHG"},
                {"CD20", "\"Shorthanded goals\""},
                {"CN21", "Home"},
                {"CD21", "\"Home games played\""},
                {"CN22", "Away"},
                {"CD22", "\"Away games played\""},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        snp(socket, nhlPersonaStatsEntityList, 0);
    }

    /**
     * Send ranking snapshot
     *
     * @param socket                    The socket to write the response to
     * @param nhlPersonaStatsEntityList The list of persona stats entities
     * @param offset                    The offset for the ranking
     */
    public void snp(Socket socket, List<NhlPersonaStatsEntity> nhlPersonaStatsEntityList, long offset) {
        List<Map<String, String>> rankingList = new ArrayList<>();
        for (NhlPersonaStatsEntity nhlPersonaStatsEntity : nhlPersonaStatsEntityList) {
            String name = nhlPersonaStatsEntity.getPersona().getPers();
            String rank = String.valueOf(++offset);
            String points = String.valueOf(nhlPersonaStatsEntity.getPoints());
            int totalWins = nhlPersonaStatsEntity.getWins() + nhlPersonaStatsEntity.getOtWins();
            int totalLosses = nhlPersonaStatsEntity.getLosses() + nhlPersonaStatsEntity.getOtLosses();
            String stats = String.join(",", // <stats>
                    String.valueOf(offset),
                    nhlPersonaStatsEntity.getPersona().getPers(),
                    points,
                    String.valueOf(nhlPersonaStatsEntity.getScore() - nhlPersonaStatsEntity.getScoreAgainst()), // Goal difference
                    String.valueOf(nhlPersonaStatsEntity.getWins()),
                    String.valueOf(nhlPersonaStatsEntity.getOtWins()),
                    String.valueOf(nhlPersonaStatsEntity.getLosses()),
                    String.valueOf(nhlPersonaStatsEntity.getOtLosses()),
                    String.valueOf(nhlPersonaStatsEntity.getStreak()),
                    String.format("%.0f", totalWins * 100.0 / (totalWins + totalLosses)),
                    String.valueOf(nhlPersonaStatsEntity.getDraw()),
                    String.valueOf(nhlPersonaStatsEntity.getQuit()),
                    String.format("%.0f", nhlPersonaStatsEntity.getQuit() * 100.0 / (totalWins + totalLosses + nhlPersonaStatsEntity.getDraw())),
                    String.valueOf(nhlPersonaStatsEntity.getShots()),
                    String.valueOf(nhlPersonaStatsEntity.getScore()),
                    String.valueOf(nhlPersonaStatsEntity.getScoreAgainst()),
                    String.valueOf(nhlPersonaStatsEntity.getHits()),
                    String.valueOf(nhlPersonaStatsEntity.getPenmin()),
                    String.valueOf(nhlPersonaStatsEntity.getPpg()),
                    String.valueOf(nhlPersonaStatsEntity.getPpo()),
                    String.valueOf(nhlPersonaStatsEntity.getShg()),
                    String.valueOf(nhlPersonaStatsEntity.getHome()),
                    String.valueOf(nhlPersonaStatsEntity.getAway())
            );
            rankingList.add(Stream.of(new String[][]{
                    {"N", name}, // <persona name>
                    {"R", rank}, // <rank>
                    {"P", points}, // <points>
                    {"O", "0"}, // <online> ?
                    {"S", stats}, // <stats>
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
        }

        for (Map<String, String> ranking : rankingList) {
            SocketData socketData = new SocketData("+snp", null, ranking);
            socketWriter.write(socket, socketData);
        }
    }

    /**
     * Process NHL rank packet
     *
     * @param socketData The socket data containing the rank packet
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
            GameConnectionEntity gameConnection0 = gameConnectionsPlayer0.get(0);
            if (!nhlGameReportRepository.existsByGameConnectionId(gameConnection0.getId())) {
                // Create and save game report for Player 0
                NhlGameReportEntity gameReport0 = new NhlGameReportEntity();
                gameReport0.setGameConnection(gameConnection0);
                populateGameReportFromStats(gameReport0, player0Stats);
                nhlGameReportRepository.save(gameReport0);

                // Update persona stats if ranked
                int rnk = (Integer) player0Stats.get("RNK");
                if (rnk == 1) {
                    updatePersonaStats(gameConnection0, player0Stats, player1Stats);
                }
            }
        }

        // Process Player 1 if game connection found and report doesn't exist
        if (!gameConnectionsPlayer1.isEmpty()) {
            GameConnectionEntity gameConnection1 = gameConnectionsPlayer1.get(0);
            if (!nhlGameReportRepository.existsByGameConnectionId(gameConnection1.getId())) {
                // Create and save game report for Player 1
                NhlGameReportEntity gameReport1 = new NhlGameReportEntity();
                gameReport1.setGameConnection(gameConnection1);
                populateGameReportFromStats(gameReport1, player1Stats);
                nhlGameReportRepository.save(gameReport1);

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
        stats.put("SCORE", Integer.parseInt(getValueFromSocket(inputMessage, "SCORE" + playerIndex, TAB_CHAR)));
        stats.put("DSCORE", Integer.parseInt(getValueFromSocket(inputMessage, "DSCORE" + playerIndex, TAB_CHAR)));
        stats.put("HITS", Integer.parseInt(getValueFromSocket(inputMessage, "HITS" + playerIndex, TAB_CHAR)));
        stats.put("SHOTS", Integer.parseInt(getValueFromSocket(inputMessage, "SHOTS" + playerIndex, TAB_CHAR)));
        stats.put("PENMIN", Integer.parseInt(getValueFromSocket(inputMessage, "PENMIN" + playerIndex, TAB_CHAR)));
        stats.put("PPG", Integer.parseInt(getValueFromSocket(inputMessage, "PPG" + playerIndex, TAB_CHAR)));
        stats.put("PPO", Integer.parseInt(getValueFromSocket(inputMessage, "PPO" + playerIndex, TAB_CHAR)));
        stats.put("SHG", Integer.parseInt(getValueFromSocket(inputMessage, "SHG" + playerIndex, TAB_CHAR)));
        stats.put("TEAM", Integer.parseInt(getValueFromSocket(inputMessage, "TEAM" + playerIndex, TAB_CHAR)));
        stats.put("HOME", Integer.parseInt(getValueFromSocket(inputMessage, "HOME" + playerIndex, TAB_CHAR)));
        stats.put("DISC", Integer.parseInt(getValueFromSocket(inputMessage, "DISC" + playerIndex, TAB_CHAR)));
        stats.put("QUIT", Integer.parseInt(getValueFromSocket(inputMessage, "QUIT" + playerIndex, TAB_CHAR)));
        stats.put("CHEAT", Integer.parseInt(getValueFromSocket(inputMessage, "CHEAT" + playerIndex, TAB_CHAR)));
        stats.put("WEIGHT", Integer.parseInt(getValueFromSocket(inputMessage, "WEIGHT" + playerIndex, TAB_CHAR)));

        // Extract common fields
        stats.put("RNK", Integer.parseInt(getValueFromSocket(inputMessage, "RNK", TAB_CHAR)));
        stats.put("VENUE", Integer.parseInt(getValueFromSocket(inputMessage, "VENUE", TAB_CHAR)));
        stats.put("TYPE", Integer.parseInt(getValueFromSocket(inputMessage, "TYPE", TAB_CHAR)));
        stats.put("PNUM", Integer.parseInt(getValueFromSocket(inputMessage, "PNUM", TAB_CHAR)));
        stats.put("PLEN", Integer.parseInt(getValueFromSocket(inputMessage, "PLEN", TAB_CHAR)));
        stats.put("OT", Integer.parseInt(getValueFromSocket(inputMessage, "OT", TAB_CHAR)));
        stats.put("TIME", Integer.parseInt(getValueFromSocket(inputMessage, "TIME", TAB_CHAR)));
        stats.put("DTIME", Integer.parseInt(getValueFromSocket(inputMessage, "DTIME", TAB_CHAR)));
        stats.put("SKIL", Integer.parseInt(getValueFromSocket(inputMessage, "SKIL", TAB_CHAR)));
        stats.put("TID", Integer.parseInt(getValueFromSocket(inputMessage, "TID", TAB_CHAR)));
        stats.put("TMID", Integer.parseInt(getValueFromSocket(inputMessage, "TMID", TAB_CHAR)));

        return stats;
    }

    /**
     * Populate game report entity from player stats
     *
     * @param gameReport  The game report entity to populate
     * @param playerStats The player stats map
     */
    private void populateGameReportFromStats(NhlGameReportEntity gameReport, Map<String, Object> playerStats) {
        gameReport.setRnk((Integer) playerStats.get("RNK"));
        gameReport.setScore((Integer) playerStats.get("SCORE"));
        gameReport.setDscore((Integer) playerStats.get("DSCORE"));
        gameReport.setHits((Integer) playerStats.get("HITS"));
        gameReport.setShots((Integer) playerStats.get("SHOTS"));
        gameReport.setPenmin((Integer) playerStats.get("PENMIN"));
        gameReport.setPpg((Integer) playerStats.get("PPG"));
        gameReport.setPpo((Integer) playerStats.get("PPO"));
        gameReport.setShg((Integer) playerStats.get("SHG"));
        gameReport.setTeam((Integer) playerStats.get("TEAM"));
        gameReport.setHome((Integer) playerStats.get("HOME"));
        gameReport.setDisc((Integer) playerStats.get("DISC"));
        gameReport.setQuit((Integer) playerStats.get("QUIT"));
        gameReport.setCheat((Integer) playerStats.get("CHEAT"));
        gameReport.setWeight((Integer) playerStats.get("WEIGHT"));
        gameReport.setSkil((Integer) playerStats.get("SKIL"));
        gameReport.setTid((Integer) playerStats.get("TID"));
        gameReport.setTmid((Integer) playerStats.get("TMID"));
        gameReport.setVenue((Integer) playerStats.get("VENUE"));
        gameReport.setType((Integer) playerStats.get("TYPE"));
        gameReport.setPnum((Integer) playerStats.get("PNUM"));
        gameReport.setPlen((Integer) playerStats.get("PLEN"));
        gameReport.setOt((Integer) playerStats.get("OT"));
        gameReport.setTime((Integer) playerStats.get("TIME"));
        gameReport.setDtime((Integer) playerStats.get("DTIME"));
    }

    /**
     * Update persona stats with new game data
     *
     * @param gameConnection The game connection entity
     * @param playerStats    The player's stats
     * @param opponentStats  The opponent's stats
     */
    private void updatePersonaStats(GameConnectionEntity gameConnection, Map<String, Object> playerStats, Map<String, Object> opponentStats) {
        PersonaEntity persona = gameConnection.getPersonaConnection().getPersona();
        String vers = gameConnection.getGame().getVers();
        String slus = gameConnection.getGame().getSlus();

        NhlPersonaStatsEntity personaStats = nhlPersonaStatsRepository.findByPersonaIdAndVers(persona.getId(), vers);

        if (personaStats == null) {
            personaStats = new NhlPersonaStatsEntity();
            personaStats.setPersona(persona);
            personaStats.setVers(vers);
            personaStats.setSlus(slus);
        }

        // Calculate WIN/LOSS/DRAW according to business rules
        calculateWinLoss(personaStats, playerStats, opponentStats);

        // Update cumulative stats
        personaStats.setScore(personaStats.getScore() + (Integer) playerStats.get("SCORE"));
        personaStats.setScoreAgainst(personaStats.getScoreAgainst() + (Integer) opponentStats.get("SCORE"));
        personaStats.setHits(personaStats.getHits() + (Integer) playerStats.get("HITS"));
        personaStats.setShots(personaStats.getShots() + (Integer) playerStats.get("SHOTS"));
        personaStats.setPenmin(personaStats.getPenmin() + (Integer) playerStats.get("PENMIN"));
        personaStats.setPpg(personaStats.getPpg() + (Integer) playerStats.get("PPG"));
        personaStats.setPpo(personaStats.getPpo() + (Integer) playerStats.get("PPO"));
        personaStats.setShg(personaStats.getShg() + (Integer) playerStats.get("SHG"));
        personaStats.setTime(personaStats.getTime() + (Integer) playerStats.get("TIME"));
        personaStats.setWeight(personaStats.getWeight() + (Integer) playerStats.get("WEIGHT"));
        personaStats.setSkil(personaStats.getSkil() + (Integer) playerStats.get("SKIL"));

        // Update status indicators
        personaStats.setDisc(personaStats.getDisc() + (Integer) playerStats.get("DISC"));
        personaStats.setQuit(personaStats.getQuit() + (Integer) playerStats.get("QUIT"));
        personaStats.setCheat(personaStats.getCheat() + (Integer) playerStats.get("CHEAT"));

        // Update HOME/AWAY games
        int home = (Integer) playerStats.get("HOME");
        if (home == 1) {
            personaStats.setHome(personaStats.getHome() + 1);
        } else {
            personaStats.setAway(personaStats.getAway() + 1);
        }

        nhlPersonaStatsRepository.save(personaStats);
    }

    /**
     * Calculate WIN/LOSS/DRAW with custom business rules
     *
     * @param personaStats  The persona stats entity to update
     * @param playerStats   The player's stats
     * @param opponentStats The opponent's stats
     */
    private void calculateWinLoss(NhlPersonaStatsEntity personaStats, Map<String, Object> playerStats, Map<String, Object> opponentStats) {
        int playerScore = (Integer) playerStats.get("SCORE");
        int opponentScore = (Integer) opponentStats.get("SCORE");
        int playerQuit = (Integer) playerStats.get("QUIT");
        int opponentQuit = (Integer) opponentStats.get("QUIT");
        int ot = (Integer) playerStats.get("OT");

        // Business rules for WIN/LOSS/DRAW:
        // 1. Score determines win/loss, except if the winning player quits
        // 2. Otherwise, it's a draw regardless of quits


        int playerStreak = personaStats.getStreak();
        if (playerScore > opponentScore && playerQuit == 0) { // Player wins
            if (playerStreak > 0) {
                personaStats.setStreak(personaStats.getStreak() + 1);
            } else {
                personaStats.setStreak(1); // Reset streak to 1 if it was negative or zero
            }
            if (ot > 0) {
                personaStats.setOtWins(personaStats.getOtWins() + 1);
            } else {
                personaStats.setWins(personaStats.getWins() + 1);
            }
            personaStats.setPoints(personaStats.getPoints() + 2); // 2 points for a win
            return;
        } else if (playerScore < opponentScore && opponentQuit == 0) { // Player loses
            if (playerStreak < 0) {
                personaStats.setStreak(personaStats.getStreak() - 1);
            } else {
                personaStats.setStreak(-1); // Reset streak to -1 if it was positive or zero
            }
            if (ot > 0) {
                personaStats.setOtLosses(personaStats.getOtLosses() + 1);
                personaStats.setPoints(personaStats.getPoints() + 1); // 1 point for an OT loss
            } else {
                personaStats.setLosses(personaStats.getLosses() + 1);
            }
            return;
        }
        personaStats.setDraw(personaStats.getDraw() + 1);
    }

    /**
     * Get stats and rank for a persona
     *
     * @param personaEntity The persona entity to get stats for
     * @param vers          The version of the game
     * @return A map containing stats and rank
     */
    public Map<String, String> getStatsAndRank(PersonaEntity personaEntity, String vers) {
        Map<String, String> result = new HashMap<>();

        NhlPersonaStatsEntity nhlPersonaStatsEntity = nhlPersonaStatsRepository.findByPersonaIdAndVers(personaEntity.getId(), vers);
        boolean hasStats = null != nhlPersonaStatsEntity;

        long totalGames = (hasStats ? getTotalGames(nhlPersonaStatsEntity) : 0);
        String stats = getStats(totalGames, nhlPersonaStatsEntity, hasStats);
        String rank = hasStats ? String.valueOf(nhlPersonaStatsRepository.getRankByPersonaIdAndVers(nhlPersonaStatsEntity.getPersona().getId(), vers)) : "";

        result.put("stats", stats);
        result.put("rank", rank);
        return result;
    }

    private long getTotalGames(NhlPersonaStatsEntity nhlPersonaStatsEntity) {
        return nhlPersonaStatsEntity.getWins()
                + nhlPersonaStatsEntity.getOtWins()
                + nhlPersonaStatsEntity.getLosses()
                + nhlPersonaStatsEntity.getOtLosses()
                + nhlPersonaStatsEntity.getDraw();
    }
}
