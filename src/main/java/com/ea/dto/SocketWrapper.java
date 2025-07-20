package com.ea.dto;

import com.ea.entities.core.AccountEntity;
import com.ea.entities.core.PersonaConnectionEntity;
import com.ea.entities.core.PersonaEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Slf4j
public class SocketWrapper {
    private final AtomicBoolean isDedicatedHost = new AtomicBoolean(false);
    private final AtomicBoolean isGps = new AtomicBoolean(false);
    private final AtomicBoolean isHosting = new AtomicBoolean(false);
    private Socket socket;
    private String identifier;
    private volatile String lkey;
    private volatile String userflags = "1";
    private volatile AccountEntity accountEntity;
    private volatile PersonaEntity personaEntity;
    private volatile PersonaConnectionEntity personaConnectionEntity;
    private volatile Thread gameSearchThread;


    public void cleanupOnSocketClose(SocketWrapper socketWrapper) {
        synchronized (this) {
            Thread searchThread = socketWrapper.getGameSearchThread();
            if (searchThread != null && searchThread.isAlive()) {
                log.debug("Game search thread for socket {} finished", socket.getRemoteSocketAddress());
                searchThread.interrupt();
                socketWrapper.setGameSearchThread(null);
            }
        }
    }

}
