package com.ea.config;

import com.ea.dto.BuddySocketWrapper;
import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.services.core.GameService;
import com.ea.services.core.PersonaService;
import com.ea.services.server.SocketManager;
import com.ea.steps.SocketReader;
import com.ea.steps.SocketWriter;
import com.ea.utils.SocketUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Thread to handle a unique tcp socket
 */
@Slf4j
@RequiredArgsConstructor
public class TcpSocketThread implements Runnable {

    private final Socket clientSocket;
    private final SocketManager socketManager;
    private final SocketReader socketReader;
    private final SocketWriter socketWriter;
    private final PersonaService personaService;
    private final GameService gameService;
    private ScheduledExecutorService pingExecutor;

    @Override
    public void run() {
        log.info("TCP client session started: {}", clientSocket.getRemoteSocketAddress());
        try {
            pingExecutor = Executors.newSingleThreadScheduledExecutor();
            pingExecutor.scheduleAtFixedRate(() -> png(clientSocket), 20, 20, TimeUnit.SECONDS);
            socketReader.read(clientSocket);
        } catch (Exception e) {
            log.error("Exception in TcpSocketThread: ", e);
        } finally {
            if (pingExecutor != null && !pingExecutor.isShutdown()) {
                pingExecutor.shutdownNow();
            }

            String playerInfo = "";
            // Find socket wrapper using exact Socket object match
            SocketWrapper socketWrapper = socketManager.getSocketWrapperBySocket(clientSocket);
            if (socketWrapper != null) {
                playerInfo = SocketUtils.getPlayerInfo(socketWrapper);
                socketManager.removeSocket(socketWrapper.getIdentifier());
                if (socketWrapper.getPersonaEntity() != null) {
                    gameService.endGameConnection(socketWrapper);
                    personaService.endPersonaConnection(socketWrapper);
                    socketWrapper.cleanupOnSocketClose(socketWrapper);
                }
            } else {
                // Find buddy socket wrapper using exact Socket object match
                BuddySocketWrapper buddySocketWrapper = socketManager.getBuddySocketWrapperBySocket(clientSocket);
                if (buddySocketWrapper != null) {
                    playerInfo = SocketUtils.getBuddyPlayerInfo(buddySocketWrapper);
                    socketManager.removeBuddySocket(buddySocketWrapper.getIdentifier());
                } else {
                    log.warn("No SocketWrapper found for socket: {}", clientSocket.getRemoteSocketAddress());
                }
            }
            log.info("TCP client session ended: {} {}", clientSocket.getRemoteSocketAddress(), playerInfo);
        }
    }

    private void png(Socket socket) {
        SocketData socketData = new SocketData("~png", null, null);
        socketWriter.write(socket, socketData);
    }
}