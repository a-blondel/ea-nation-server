package eu.eahub.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

@Slf4j
@RequiredArgsConstructor
@Component
public class SocketReader {

    static final int HEADER_SIZE = 12;
    static final int MAX_MESSAGE_SIZE = 1024 * 1024;
    static final int INITIAL_MESSAGE_BUFFER_SIZE = 8 * 1024;

    private final SocketParser parser;

    /**
     * Waits for data to come from the client
     * Reads one complete message at a time (12-byte header, then the body), as TCP can split or merge messages at any byte,
     * then calls a parser to handle it
     *
     * @param socket the socket to read
     */
    public void read(Socket socket) {
        try {
            InputStream is = socket.getInputStream();
            while (true) {
                byte[] header = is.readNBytes(HEADER_SIZE);
                if (header.length == 0) {
                    return;
                }
                if (header.length < HEADER_SIZE) {
                    log.warn("Incomplete message header, stop reading");
                    return;
                }
                int length = ByteBuffer.wrap(header).getInt(8);
                if (length < HEADER_SIZE || length > MAX_MESSAGE_SIZE) {
                    log.warn("Invalid message length {}, stop reading", Integer.toUnsignedString(length));
                    return;
                }
                byte[] message = readMessage(is, header, length);
                if (message == null) {
                    log.warn("Incomplete message body, stop reading");
                    return;
                }
                parser.parse(socket, message);
            }
        } catch (SocketException e) {
            log.warn("Socket closed, stop reading");
        } catch (IOException e) {
            log.error("Error reading from socket : {}", e.getMessage());
        }
    }

    /**
     * Grow the message buffer as body bytes arrive instead of reserving the
     * entire declared message size before the body has been read.
     */
    private byte[] readMessage(InputStream is, byte[] header, int length) throws IOException {
        int initialSize = Math.min(length, INITIAL_MESSAGE_BUFFER_SIZE);
        byte[] message = Arrays.copyOf(header, initialSize);
        int offset = HEADER_SIZE;

        while (offset < length) {
            if (offset == message.length) {
                message = Arrays.copyOf(message, Math.min(length, message.length * 2));
            }

            int count = is.read(message, offset, message.length - offset);
            if (count < 0) {
                return null;
            }
            if (count == 0) {
                int next = is.read();
                if (next < 0) {
                    return null;
                }
                message[offset++] = (byte) next;
            } else {
                offset += count;
            }
        }

        return message;
    }
}
