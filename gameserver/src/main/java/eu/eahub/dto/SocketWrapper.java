package eu.eahub.dto;

import eu.eahub.entities.core.AccountEntity;
import eu.eahub.entities.core.PersonaConnectionEntity;
import eu.eahub.entities.core.PersonaEntity;
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
    private volatile String userparams = "";
    private volatile AccountEntity accountEntity;
    private volatile PersonaEntity personaEntity;
    private volatile PersonaConnectionEntity personaConnectionEntity;
    private volatile Thread gameSearchThread;
    private volatile Long userSetId;
    private volatile String auxText;
    // Lobby subscriptions requested with sele (used by TW04, which is only sent the updates it subscribed to)
    private volatile boolean roomUpdates;
    private volatile boolean userUpdates;
    private volatile boolean messageUpdates;
    // Address the client reported with addr (used by TW04 to reach players behind the same NAT)
    private volatile String reportedAddress = "";
    // Number of +usr records last sent, so that departed users can be removed (TW04)
    private volatile int rosterSize;
    // First day of the tournament season shown to the client (TW04)
    private volatile Integer seasonFirst;


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
