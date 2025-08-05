package com.ea.steps;

import com.ea.dto.BuddySocketWrapper;
import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.services.server.SocketManager;
import com.ea.utils.HexUtils;
import com.ea.utils.Props;
import com.ea.utils.SocketUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.nio.ByteBuffer;

@Slf4j
@RequiredArgsConstructor
@Component
public class SocketParser {

    private final Props props;
    private final SocketManager socketManager;
    private final SocketProcessor socketProcessor;

    /**
     * Parses input messages based on current content of the stream
     * Loops until all complete messages are parsed
     * Sends complete messages to the processor
     *
     * @param socket     the socket to exchange with
     * @param buffer     the buffer to read from
     * @param readLength the size of written content in buffer
     */
    public void parse(Socket socket, byte[] buffer, int readLength) {
        ByteBuffer messageBuffer = ByteBuffer.allocate(4096);
        handleSocketData(socket, buffer, readLength, messageBuffer);
    }

    private void handleSocketData(Socket socket, byte[] buffer, int readLength, ByteBuffer messageBuffer) {
        if (messageBuffer.remaining() < readLength) {
            ByteBuffer newBuffer = ByteBuffer.allocate(messageBuffer.capacity() + readLength);
            messageBuffer.flip();
            newBuffer.put(messageBuffer);
            messageBuffer = newBuffer;
        }

        messageBuffer.put(buffer, 0, readLength);
        messageBuffer.flip();

        while (messageBuffer.remaining() >= 12) {
            int messageSize = SocketUtils.getlength(messageBuffer.array(), messageBuffer.position());
            if (messageBuffer.remaining() >= messageSize) {
                byte[] message = new byte[messageSize];
                messageBuffer.get(message);
                processCompleteMessage(socket, message, messageSize);
            } else {
                break;
            }
        }

        messageBuffer.compact();
    }

    private void processCompleteMessage(Socket socket, byte[] message, int messageSize) {
        String id = new String(message, 0, 4);
        String content = new String(message, 12, messageSize - 12);
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