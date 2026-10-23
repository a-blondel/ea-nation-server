package eu.eahub.steps;

import eu.eahub.dirtysdk.LobbyTagField;
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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static eu.eahub.utils.SocketUtils.NEWLINE_CHAR;
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
        write(socket, socketData, joiner, null);
    }

    /**
     * Sends a plain text body instead of key=value pairs (e.g. TW04 news and tournament data)
     *
     * @param socket the socket to write into
     * @param id     the message id
     * @param text   the body
     */
    public void writeText(Socket socket, String id, String text) {
        write(socket, new SocketData(id, null, null), NEWLINE_CHAR, text);
    }

    /**
     * Sends fields encoded as tag fields (quoted and escaped values, in the map's order), as used by TW04
     *
     * @param socket the socket to write into
     * @param id     the message id
     * @param fields the fields to send
     */
    public void writeTagFields(Socket socket, String id, Map<String, String> fields) {
        writeText(socket, id, LobbyTagField.encode(fields));
    }

    private void write(Socket socket, SocketData socketData, String joiner, String text) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             DataOutputStream writer = new DataOutputStream(buffer)) {

            writer.write(socketData.getIdMessage().getBytes(StandardCharsets.ISO_8859_1));
            if (socketData.getIdMessage().length() == 4) {
                writer.writeInt(0);
            }
            int outputLength = 12;

            if (text != null) {
                byte[] contentBytes = (text + "\0").getBytes(StandardCharsets.ISO_8859_1);
                outputLength += contentBytes.length;
                writer.writeInt(outputLength);
                writer.write(contentBytes);
            } else if (socketData.getOutputData() != null) {
                byte[] contentBytes = (socketData.getOutputData().entrySet()
                        .stream()
                        .map(param -> param.getKey() + "=" + param.getValue())
                        .collect(joining(joiner)) + "\0").getBytes(StandardCharsets.ISO_8859_1);

                outputLength += contentBytes.length;
                writer.writeInt(outputLength);
                writer.write(contentBytes);
            } else {
                writer.writeInt(outputLength);
            }

            byte[] bufferBytes = buffer.toByteArray();

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
            if (!props.getTcpDebugExclusions().contains(socketData.getIdMessage())) {
                log.info("--> {} {} {}",
                        socket.getRemoteSocketAddress().toString(),
                        props.isTcpDebugEnabled() ? playerInfo : socketData.getIdMessage(),
                        props.isTcpDebugEnabled() ? "\n" + HexUtils.formatHexDump(bufferBytes) : playerInfo);
            }

            synchronized (this) {
                socket.getOutputStream().write(bufferBytes);
            }

        } catch (IOException e) {
            log.error("Error: writing to closed socket");
        }
    }

}