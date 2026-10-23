package eu.eahub.services.stats;

import eu.eahub.dirtysdk.LobbyTagField;
import eu.eahub.dto.Room;
import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.enums.LeaderboardLabel;
import eu.eahub.services.core.GameService;
import eu.eahub.services.core.GameSessionListener;
import eu.eahub.services.core.RoomService;
import eu.eahub.services.core.TwChallengeService;
import eu.eahub.services.core.UserSetService;
import eu.eahub.services.server.GameServerService;
import eu.eahub.steps.SocketWriter;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eu.eahub.services.server.GameServerService.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class StatsService {

    private final SocketWriter socketWriter;
    private final GameServerService gameServerService;
    private final MohhStatsService mohhStatsService;
    private final NfsStatsService nfsStatsService;
    private final NhlStatsService nhlStatsService;
    private final FifaStatsService fifaStatsService;
    private final GameService gameService;
    private final RoomService roomService;
    private final NfsRankService nfsRankService;
    private final UserSetService userSetService;
    private final TwStatsService twStatsService;
    private final TwTournamentService twTournamentService;
    private final TwChallengeService twChallengeService;

    /**
     * Retrieve ranking categories
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void cate(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (ALL_MOH.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
            mohhStatsService.cate(socketData, socketWrapper);
        } else if (ALL_NHL.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
            nhlStatsService.cate(socketData);
        } else if (ALL_PSP_NFS.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
            nfsStatsService.cate(socketData, socketWrapper);
        } else if (ALL_FIFA.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
            fifaStatsService.cate(socketData);
        } else {
            Map<String, String> content = Stream.of(new String[][]{
                    {"CC", "1"}, // <total # of categories in this view>
                    {"IC", "1"}, // <total # of indices in this view>
                    {"VC", "1"}, // <total # of variations in this view>
                    {"SYMS", "\"" + LeaderboardLabel.TOP_100.name + "\""},
                    {"SS", String.valueOf(LeaderboardLabel.TOP_100.name.length() + 1)},
                    {"R", "0,1,1,1,1,1,1,1"},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

            socketData.setOutputData(content);
        }
        socketWriter.write(socket, socketData);
    }

    /**
     * Request ranking snapshot
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void snap(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (ALL_TW.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
            twSnap(socket, socketData, socketWrapper);
        } else if (ALL_MOH.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
            mohhStatsService.snap(socket, socketData, socketWrapper);
        } else if (ALL_NHL.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
            nhlStatsService.snap(socket, socketData, socketWrapper);
        } else if (ALL_PSP_NFS.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
            nfsStatsService.snap(socket, socketData, socketWrapper);
        } else if (ALL_FIFA.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
            fifaStatsService.snap(socket, socketData, socketWrapper);
        } else {
            socketWriter.write(socket, socketData);
        }
    }

    /**
     * Send ranking results.
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    @Transactional
    public void rank(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (socketWrapper != null && socketWrapper.getPersonaConnectionEntity() != null
                && ALL_TW.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
            twRank(socket, socketData, socketWrapper);
            return;
        }
        socketWriter.write(socket, socketData);
        if (socketWrapper == null) {
            log.warn("SocketWrapper is null for socket: {}", socket);
            return;
        }

        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        if (PSP_MOH07.equals(vers)) {
            mohhStatsService.rank(socketData);
        } else {
            if (gameServerService.isP2P(vers)) {
                if (ALL_NHL.contains(vers)) {
                    nhlStatsService.rank(socketData);
                } else if (ALL_PSP_NFS.contains(vers)) {
                    nfsRankService.rank(socketData);
                } else if (ALL_FIFA.contains(vers)) {
                    fifaStatsService.rank(socketData);
                }
                // Close the game and gameConnections if the game is P2P
                gameService.endGame(socketWrapper);
                if (USERSETS_GAMES.contains(vers)) {
                    // Send +who to update G= and US=
                    userSetService.who(socket, socketWrapper);
                    // Send +ust and +usm updates to all members
                    userSetService.sendRankPostGameUpdates(socketWrapper);
                } else {
                    // Remove the persona from the room (back to main menu)
                    roomService.removePersonaFromRoom(vers, socketWrapper);
                }
            }
        }
    }

    /**
     * sviw - Request names for each fields in a user's stats record.
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void sviw(Socket socket, SocketData socketData) {
        // Dummy data from ghostline, requires analysis to understand the fields and populate them correctly
        Map<String, String> content = Stream.of(new String[][]{
                {"N", "16"},
                {"NAMES", "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16"},
                {"DESCS", "1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1"},
                {"PARAMS", "2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2"},
                {"WIDTHS", "5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    /**
     * Custom user commands: TW04 tournaments and profile
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void cusr(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (ALL_TW.contains(gameServerService.getNameByPort(socket.getLocalPort()))) {
            twTournamentService.cusr(socket, socketData, socketWrapper);
        } else {
            socketWriter.write(socket, socketData);
        }
    }

    private static int integer(Map<String, String> fields, String key, int fallback) {
        try {
            return Integer.parseInt(fields.getOrDefault(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * TW04 leaderboards: 13 = Match Play, 14 = Stroke Play, 32 = last seven days, the others are tournament boards
     * The snap acknowledgement arms the channel (4 or 5) before its +snp rows arrive
     */
    private void twSnap(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (socketWrapper.getPersonaEntity() == null) {
            socketWriter.writeText(socket, "snapnusr", "");
            return;
        }
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        int index = integer(fields, "INDEX", -1);
        int channel = integer(fields, "CHAN", 4);
        if (channel != 4 && channel != 5) {
            socketWriter.writeText(socket, "snapdata", "");
            return;
        }
        int start = Math.max(0, integer(fields, "START", 0));
        int count = Math.clamp(integer(fields, "RANGE", 25), 0, 50);
        String find = fields.getOrDefault("FIND", "");
        record Row(String name, int rank, long points, String body) {
        }
        List<Row> page;
        if (Set.of(13, 14, 32).contains(index)) {
            page = twStatsService.board(vers, index, start, count, find).stream()
                    .map(r -> new Row(r.name(), r.rank(), r.points(), r.packed()))
                    .toList();
        } else {
            int seasonFirst = socketWrapper.getSeasonFirst() == null ? twTournamentService.today() - 7 : socketWrapper.getSeasonFirst();
            List<Row> board = twTournamentService.board(vers, index, seasonFirst).stream()
                    .map(r -> new Row(r.name(), r.rank(), r.sort(), r.text()))
                    .toList();
            page = find.isEmpty()
                    ? board.stream().skip(start).limit(count).toList()
                    : board.stream().filter(row -> row.name().equalsIgnoreCase(find)).limit(count).toList();
        }
        String chan = String.valueOf(channel);
        String size = String.valueOf(page.size());
        socketWriter.writeTagFields(socket, "snap", Map.of("CHAN", chan, "COUNT", size, "RANGE", size, "MORE", "0"));
        for (Row row : page) {
            socketWriter.writeTagFields(socket, "+snp", Map.of("CHAN", chan, "R", String.valueOf(row.rank()), "P", String.valueOf(row.points()),
                    "N", row.name(), "S", row.body()));
        }
        log.info("TW04 leaderboard index {} channel {}: {} rows", index, channel, page.size());
    }

    /**
     * TW04 match result, sent by both players once they are back in the lobby
     * The match counts once; it then ends, and the room's roster is sent again with the new ranks
     */
    private void twRank(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (socketWrapper.getPersonaEntity() == null) {
            socketWriter.writeText(socket, "ranknusr", "");
            return;
        }
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        TwStatsService.Result result = twStatsService.rank(socketWrapper, fields, twChallengeService.getGameIdBySessionToken(fields.get("AUTH")));
        switch (result.receipt()) {
            case UNAUTHORIZED -> socketWriter.writeText(socket, "rankmaut", "");
            case INVALID -> socketWriter.writeText(socket, "rankdata", "");
            default -> {
                gameService.endSession(result.game(), GameSessionListener.EndReason.REPORTED);
                socketWriter.writeTagFields(socket, "rank", Map.of("TITLE", "SUCCESS", "MESG", "Game results received. Statistics saved."));
                log.info("TW04 result {} from {}", result.receipt(), socketWrapper.getPersonaEntity().getPers());
                Room room = roomService.getRoomByPersonaId(socketWrapper.getPersonaEntity().getId());
                if (room != null) {
                    roomService.twBroadcastUsers(socketWrapper.getPersonaConnectionEntity().getVers(), room);
                }
            }
        }
    }
}
