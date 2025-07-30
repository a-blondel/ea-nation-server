package com.ea.steps;

import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.services.server.SocketManager;
import com.ea.utils.HexUtils;
import com.ea.utils.Props;
import com.ea.utils.SocketUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static com.ea.utils.SocketUtils.NEWLINE_CHAR;
import static java.util.stream.Collectors.joining;

@Slf4j
@RequiredArgsConstructor
@Component
public class SocketWriter {

    private final Props props;
    private final SocketManager socketManager;

    public void write(Socket socket, SocketData socketData) {
        write(socket, socketData, NEWLINE_CHAR);
    }

    /**
     * Builds the full output message based on the data id and content
     * Then sends it through the socket
     *
     * @param socket     the socket to write into
     * @param socketData the object to use to write the message
     */
    public void write(Socket socket, SocketData socketData, String joiner) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             DataOutputStream writer = new DataOutputStream(buffer)) {

            writer.write(socketData.getIdMessage().getBytes(StandardCharsets.UTF_8));
            if (socketData.getIdMessage().length() == 4) {
                writer.writeInt(0);
            }
            int outputLength = 12;

            if (socketData.getOutputData() != null) {
                byte[] contentBytes = (socketData.getOutputData().entrySet()
                        .stream()
                        .map(param -> param.getKey() + "=" + param.getValue())
                        .collect(joining(joiner)) + "\0").getBytes(StandardCharsets.UTF_8);

                outputLength += contentBytes.length;
                writer.writeInt(outputLength);
                writer.write(contentBytes);
            } else {
                writer.writeInt(outputLength);
            }

            byte[] bufferBytes = buffer.toByteArray();
            SocketWrapper socketWrapper = socketManager.getSocketWrapper(socket);
            String playerInfo = SocketUtils.getPlayerInfo(socketWrapper);
            if (!props.getTcpDebugExclusions().contains(socketData.getIdMessage())) {
                log.info("--> {} {} {}",
                        socket.getRemoteSocketAddress().toString(),
                        props.isTcpDebugEnabled() ? playerInfo : socketData.getIdMessage(),
                        props.isTcpDebugEnabled() ? "\n" + HexUtils.formatHexDump(bufferBytes) : playerInfo);
            }

            synchronized (this) {
                if (socket.isClosed() || !socket.isConnected() || socket.isOutputShutdown()) {
                    log.warn("Trying to write in a closed socket: {}", socket.getRemoteSocketAddress());
                    return;
                }
                socket.getOutputStream().write(bufferBytes);
            }

        } catch (IOException e) {
            log.error("Error: writing to closed socket", e);
        }
    }

}