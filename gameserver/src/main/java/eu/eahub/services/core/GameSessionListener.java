package eu.eahub.services.core;

import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.GameEntity;

import java.util.Map;

/**
 * Called when a server-started session begins and ends (e.g. to route its peer traffic through a relay)
 */
public interface GameSessionListener {

    enum EndReason {
        REPORTED,
        CANCELLED,
        EXPIRED
    }

    /**
     * A session is about to start
     *
     * @param game  the game
     * @param host  the host's connection
     * @param guest the guest's connection
     * @return the address each player should connect to, by persona id, or an empty map for direct connections
     * @throws IllegalStateException if the session cannot start
     */
    default Map<Long, String> beforeStart(GameEntity game, SocketWrapper host, SocketWrapper guest) {
        return Map.of();
    }

    /**
     * A session ended
     *
     * @param game   the game
     * @param reason why it ended
     */
    default void onEnd(GameEntity game, EndReason reason) {
    }
}
