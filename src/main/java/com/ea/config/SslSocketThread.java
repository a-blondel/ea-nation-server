package com.ea.config;

import com.ea.steps.SocketReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLSocket;

/**
 * Thread to handle a unique SSL socket
 */
@Slf4j
@RequiredArgsConstructor
public class SslSocketThread implements Runnable {

    private final SSLSocket clientSocket;
    private final SocketReader socketReader;

    public void run() {
        log.info("SSL client session started: {}", clientSocket.getRemoteSocketAddress().toString());
        try {
            socketReader.read(clientSocket);
        } finally {
            log.info("SSL client session ended: {}", clientSocket.getRemoteSocketAddress().toString());
        }
    }

}
