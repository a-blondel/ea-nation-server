package eu.eahub.config;

import eu.eahub.dto.BuddySocketWrapper;
import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.services.core.GameService;
import eu.eahub.services.core.PersonaService;
import eu.eahub.services.core.UserSetService;
import eu.eahub.services.server.SocketManager;
import eu.eahub.steps.SocketReader;
import eu.eahub.steps.SocketWriter;
import eu.eahub.utils.SocketUtils;
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
    private final UserSetService userSetService;
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
                    userSetService.endUserSetMembership(socketWrapper);
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