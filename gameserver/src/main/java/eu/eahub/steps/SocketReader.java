package eu.eahub.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

@Slf4j
@RequiredArgsConstructor
@Component
public class SocketReader {

    static final int HEADER_SIZE = 12;
    static final int MAX_MESSAGE_SIZE = 1024 * 1024;

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
                byte[] message = new byte[length];
                System.arraycopy(header, 0, message, 0, HEADER_SIZE);
                if (is.readNBytes(message, HEADER_SIZE, length - HEADER_SIZE) < length - HEADER_SIZE) {
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
}
