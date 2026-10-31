package eu.eahub.steps;

import eu.eahub.dto.BuddySocketWrapper;
import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.services.server.SocketManager;
import eu.eahub.utils.HexUtils;
import eu.eahub.utils.Props;
import eu.eahub.utils.SocketUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Slf4j
@RequiredArgsConstructor
@Component
public class SocketParser {

    private final Props props;
    private final SocketManager socketManager;
    private final SocketProcessor socketProcessor;

    /**
     * Parses a complete message (12-byte header and body) read by SocketReader
     * Sends it to the processor
     *
     * @param socket  the socket to exchange with
     * @param message the complete message
     */
    public void parse(Socket socket, byte[] message) {
        String id = new String(message, 0, 4, StandardCharsets.ISO_8859_1);
        String content = new String(message, 12, message.length - 12, StandardCharsets.ISO_8859_1);
        SocketData socketData = new SocketData(id, content, null);

        String playerInfo = "";
        SocketWrapper socketWrapper = socketManager.getSocketWrapperBySocket(socket);
        if (socketWrapper != null) {
            playerInfo = SocketUtils.getPlayerInfo(socketWrapper);
        } else {
            BuddySocketWrapper buddySocketWrapper = socketManager.getBuddySocketWrapperBySocket(socket);
            if (buddySocketWrapper != null) {
                playerInfo = SocketUtils.getBuddyPlayerInfo(buddySocketWrapper);
            }
        }

        // TODO : Remove rank debug display when stats are fully implemented
        if (!props.getTcpDebugExclusions().contains(socketData.getIdMessage())) {
            log.info("<-- {} {} {}", socket.getRemoteSocketAddress().toString(),
                    props.isTcpDebugEnabled() || "rank".equals(socketData.getIdMessage()) ? playerInfo : socketData.getIdMessage(),
                    props.isTcpDebugEnabled() || "rank".equals(socketData.getIdMessage()) ? "\n" + HexUtils.formatHexDump(message) : playerInfo);
        }
        socketProcessor.process(socket, socketData);
    }

}