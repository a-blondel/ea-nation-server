package com.ea.services.stats;

import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.services.core.GameService;
import com.ea.services.core.RoomService;
import com.ea.services.server.GameServerService;
import com.ea.steps.SocketWriter;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;

import static com.ea.services.server.GameServerService.MOH07_OR_MOH08;
import static com.ea.services.server.GameServerService.MOH07_OR_UHS;

@Slf4j
@RequiredArgsConstructor
@Service
public class StatsService {

    private final SocketWriter socketWriter;
    private final GameServerService gameServerService;
    private final MohhStatsService mohhStatsService;
    private final GameService gameService;
    private final RoomService roomService;

    /**
     * Retrieve ranking categories
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void cate(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (MOH07_OR_MOH08.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
            mohhStatsService.cate(socket, socketData);
        } else {
            socketWriter.write(socket, socketData);
        }
    }

    /**
     * Request ranking snapshot
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void snap(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (MOH07_OR_MOH08.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
            mohhStatsService.snap(socket, socketData, socketWrapper);
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
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        if (MOH07_OR_UHS.contains(vers)) {
            mohhStatsService.rank(socket, socketData);
        } else {
            if (gameServerService.isP2P(vers)) {
                // Close the game and gameConnections if the game is P2P
                gameService.endGame(socketWrapper);

                // Remove the persona from the room (back to main menu)
                roomService.removePersonaFromRoom(vers, socketWrapper);
            }
            socketWriter.write(socket, socketData);
        }
    }

}
