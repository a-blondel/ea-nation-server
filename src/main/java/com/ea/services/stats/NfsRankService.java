package com.ea.services.stats;

import com.ea.dto.SocketData;
import com.ea.entities.core.GameConnectionEntity;
import com.ea.entities.stats.NfsGameReportEntity;
import com.ea.entities.stats.NfsPersonaStatsEntity;
import com.ea.repositories.core.GameConnectionRepository;
import com.ea.repositories.stats.NfsGameReportRepository;
import com.ea.repositories.stats.NfsPersonaStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.ea.services.server.GameServerService.PSP_NFS06;
import static com.ea.utils.SocketUtils.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class NfsRankService {

    private final NfsGameReportRepository nfsGameReportRepository;
    private final NfsPersonaStatsRepository nfsPersonaStatsRepository;
    private final GameConnectionRepository gameConnectionRepository;

    @Transactional
    public void rank(SocketData socketData) {
        // Extract common game info
        String startTime = getValueFromSocket(socketData.getInputMessage(), "WHEN", TAB_CHAR);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATETIME_FORMAT);
        LocalDateTime parsedStartTime = LocalDateTime.parse(startTime, formatter);

        // Get number of players
        String playerCountStr = getValueFromSocket(socketData.getInputMessage(), "NUMPLYRS", TAB_CHAR);
        // Default to 2 if not found (MW don't send NUMPLYRS but always has 2 players)
        int numPlayers = playerCountStr != null ? Integer.parseInt(playerCountStr) : 2;

        // Process each player individually
        for (int i = 0; i < numPlayers; i++) {
            processPlayerRank(socketData, String.valueOf(i), parsedStartTime);
        }
    }

    private void processPlayerRank(SocketData socketData, String playerIndex, LocalDateTime startTime) {
        // Get player name
        String playerName = getValueFromSocket(socketData.getInputMessage(), "NAME" + playerIndex, TAB_CHAR);

        // Find game connections for this player
        List<GameConnectionEntity> gameConnections = gameConnectionRepository.findMatchingGameConnections(playerName, startTime, true);

        if (!gameConnections.isEmpty()) {
            GameConnectionEntity gameConnection = gameConnections.getFirst();

            // Check if report already exists for this game connection
            if (!nfsGameReportRepository.existsByGameConnectionId(gameConnection.getId())) {
                // Create and save game report
                NfsGameReportEntity gameReport = createGameReport(socketData.getInputMessage(), playerIndex, gameConnection);
                nfsGameReportRepository.save(gameReport);

                // Update persona stats if ranked
                int rnk = Integer.parseInt(getValueFromSocket(socketData.getInputMessage(), "RNK", TAB_CHAR));
                if (rnk == 1) {
                    updatePersonaStats(gameConnection, gameReport);
                }
            }
        }
    }

    private NfsGameReportEntity createGameReport(String inputMessage, String playerIndex, GameConnectionEntity gameConnection) {
        NfsGameReportEntity gameReport = new NfsGameReportEntity();
        gameReport.setGameConnection(gameConnection);

        // Extract common stats (non-player specific)
        String typeStr = getValueFromSocket(inputMessage, "GTYP", TAB_CHAR);
        // Map TYPE to GTYP if GTYP is not present
        if (typeStr == null) {
            typeStr = getValueFromSocket(inputMessage, "TYPE", TAB_CHAR);
        }
        gameReport.setGtyp(parseIntSafely(typeStr));

        String numlapsStr = getValueFromSocket(inputMessage, "NUMLAPS", TAB_CHAR);
        // Map PNUM to NUMLAPS if NUMLAPS is not present
        if (numlapsStr == null) {
            numlapsStr = getValueFromSocket(inputMessage, "PNUM", TAB_CHAR);
        }
        gameReport.setNumlaps(parseIntSafely(numlapsStr));

        String numplyrsStr = getValueFromSocket(inputMessage, "NUMPLYRS", TAB_CHAR);
        // Map NUMPLYRS to 2 if NUMPLYRS is not present
        if (numplyrsStr == null) {
            numplyrsStr = "2"; // Default to 2 if not found (MW don't send NUMPLYRS but always has 2 players)
        }
        gameReport.setNumplyrs(parseIntSafely(numplyrsStr));

        gameReport.setVenue(parseIntSafely(getValueFromSocket(inputMessage, "VENUE", TAB_CHAR)));
        gameReport.setCarrest(parseIntSafely(getValueFromSocket(inputMessage, "CARREST", TAB_CHAR)));
        gameReport.setPoints(parseIntSafely(getValueFromSocket(inputMessage, "POINTS", TAB_CHAR)));
        gameReport.setSkil(parseIntSafely(getValueFromSocket(inputMessage, "SKIL", TAB_CHAR)));
        gameReport.setTime(parseIntSafely(getValueFromSocket(inputMessage, "TIME", TAB_CHAR)));
        gameReport.setDtime(parseIntSafely(getValueFromSocket(inputMessage, "DTIME", TAB_CHAR)));
        gameReport.setAuth(getValueFromSocket(inputMessage, "AUTH", TAB_CHAR));
        gameReport.setRnk(parseIntSafely(getValueFromSocket(inputMessage, "RNK", TAB_CHAR)));

        String dirStr = getValueFromSocket(inputMessage, "DIR", TAB_CHAR);
        if (dirStr == null) {
            dirStr = gameConnection.getGame().getParams().split(",")[1]; // Extract DIR from game params if not present (MW)
        }
        gameReport.setDir(parseIntSafely(dirStr));

        // Extract player-specific stats
        String carStr = getValueFromSocket(inputMessage, "CAR" + playerIndex, TAB_CHAR);
        // Map CARUSED to CAR if CAR is not present
        if (carStr == null) {
            carStr = getValueFromSocket(inputMessage, "CARUSED" + playerIndex, TAB_CHAR);
        }
        gameReport.setCar(parseIntSafely(carStr));

        String posStr = getValueFromSocket(inputMessage, "POS" + playerIndex, TAB_CHAR);
        // Map SCORE to POS if POS is not present
        if (posStr == null) {
            posStr = getValueFromSocket(inputMessage, "SCORE" + playerIndex, TAB_CHAR);
        }
        gameReport.setPos(parseIntSafely(posStr));

        gameReport.setRacetime(parseIntSafely(getValueFromSocket(inputMessage, "RACETIME" + playerIndex, TAB_CHAR)));
        gameReport.setLapscomp(parseIntSafely(getValueFromSocket(inputMessage, "LAPSCOMP" + playerIndex, TAB_CHAR)));
        gameReport.setLap(parseIntSafely(getValueFromSocket(inputMessage, "LAP" + playerIndex, TAB_CHAR)));
        gameReport.setTeam(parseIntSafely(getValueFromSocket(inputMessage, "TEAM" + playerIndex, TAB_CHAR)));
        gameReport.setWeight(parseIntSafely(getValueFromSocket(inputMessage, "WEIGHT" + playerIndex, TAB_CHAR)));
        gameReport.setDscore(parseIntSafely(getValueFromSocket(inputMessage, "DSCORE" + playerIndex, TAB_CHAR)));
        gameReport.setHome(parseIntSafely(getValueFromSocket(inputMessage, "HOME" + playerIndex, TAB_CHAR)));

        // Map CHEAT and DIDCHEAT to DIDCHEAT (both represent the same thing)
        String cheat = getValueFromSocket(inputMessage, "CHEAT" + playerIndex, TAB_CHAR);
        String didcheat = getValueFromSocket(inputMessage, "DIDCHEAT" + playerIndex, TAB_CHAR);
        gameReport.setDidcheat(parseIntSafely(cheat != null ? cheat : didcheat));

        // Map QUIT and DIDQUIT to DIDQUIT (both represent the same thing)
        String quit = getValueFromSocket(inputMessage, "QUIT" + playerIndex, TAB_CHAR);
        String didquit = getValueFromSocket(inputMessage, "DIDQUIT" + playerIndex, TAB_CHAR);
        gameReport.setDidquit(parseIntSafely(quit != null ? quit : didquit));

        // Map DISC to DIDDISC (different from common DISC column)
        String disc = getValueFromSocket(inputMessage, "DISC" + playerIndex, TAB_CHAR);
        String diddisc = getValueFromSocket(inputMessage, "DIDDISC" + playerIndex, TAB_CHAR);
        gameReport.setDiddisc(parseIntSafely(disc != null ? disc : diddisc));

        // Common DISC column (different from player-specific DISC)
        gameReport.setDisc(parseIntSafely(getValueFromSocket(inputMessage, "DISC", TAB_CHAR)));

        return gameReport;
    }

    private void updatePersonaStats(GameConnectionEntity gameConnection, NfsGameReportEntity gameReport) {
        String vers = gameConnection.getGame().getVers();

        NfsPersonaStatsEntity stats = nfsPersonaStatsRepository.findByPersonaIdAndVers(
                gameConnection.getPersonaConnection().getPersona().getId(), vers);

        if (stats == null) {
            stats = new NfsPersonaStatsEntity();
            stats.setPersona(gameConnection.getPersonaConnection().getPersona());
            stats.setVers(vers);
        }

        // Update cumulative stats
        updateCumulativeStats(stats, gameReport);
        nfsPersonaStatsRepository.save(stats);
    }

    private void updateCumulativeStats(NfsPersonaStatsEntity stats, NfsGameReportEntity gameReport) {
        // Determine win/loss based on position
        if (gameReport.getPos() == 1 && gameReport.getRacetime() > 0) {
            stats.setWins(stats.getWins() + 1);
        } else if (gameReport.getPos() > 1 || gameReport.getDidquit() == 1 || gameReport.getDiddisc() == 1 ||
                (gameReport.getPos() == 0 && PSP_NFS06.equals(gameReport.getGameConnection().getGame().getVers()))) {
            stats.setLosses(stats.getLosses() + 1);
        }

        // Update skill
        stats.setSkil(stats.getSkil() + gameReport.getSkil());

        // Update total time
        stats.setTime(stats.getTime() + gameReport.getTime());

        // Update race time
        stats.setRacetime(stats.getRacetime() + gameReport.getRacetime());

        // Update laps completed
        if (gameReport.getLapscomp() != null) {
            stats.setLapscomp(stats.getLapscomp() + gameReport.getLapscomp());
        }

        // Update cheat count
        if (gameReport.getDidcheat() > 0) {
            stats.setDidcheat(stats.getDidcheat() + 1);
        }

        // Update quit count
        if (gameReport.getDidquit() > 0) {
            stats.setDidquit(stats.getDidquit() + 1);
        }

        // Update disconnect count
        if (gameReport.getDiddisc() > 0) {
            stats.setDiddisc(stats.getDiddisc() + 1);
        }
    }

    // Utility method for safe parsing
    private Integer parseIntSafely(String value) {
        try {
            return value != null && !value.isEmpty() ? Integer.parseInt(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
