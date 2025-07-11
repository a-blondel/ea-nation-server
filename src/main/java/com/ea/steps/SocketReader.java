package com.ea.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;

@Slf4j
@RequiredArgsConstructor
@Component
public class SocketReader {

    private final SocketParser parser;

    /**
     * Waits for data to come from the client
     * Calls a parser to handle input messages from the stream
     * @param socket the socket to read
     */
    public void read(Socket socket) {
        try {
            InputStream is = socket.getInputStream();
            byte[] buffer = new byte[4096];
            int readLength;
            while ((readLength = is.read(buffer)) != -1) {
                parser.parse(socket, buffer, readLength);
            }
        } catch (SocketException e) {
            log.warn("Socket closed, stop reading");
        } catch (IOException e) {
            log.error("Error reading from socket : {}", e.getMessage());
        }
    }
}