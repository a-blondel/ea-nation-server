package com.ea.services.stats;

import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.enums.LeaderboardLabel;
import com.ea.services.core.GameService;
import com.ea.services.core.RoomService;
import com.ea.services.core.UserSetService;
import com.ea.services.server.GameServerService;
import com.ea.steps.SocketWriter;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.services.server.GameServerService.*;

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
        if (ALL_MOH.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
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

}
