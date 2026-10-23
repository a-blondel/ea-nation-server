package eu.eahub.services.core;

import eu.eahub.dto.BuddySocketWrapper;
import eu.eahub.dto.SocketWrapper;

/**
 * Called when a client connection closes, so that a game can clean up its own state (e.g. TW04 challenges)
 */
public interface ConnectionListener {

    /**
     * A lobby connection closed, before its persona and game connections are ended
     *
     * @param socketWrapper the closed connection
     */
    default void onDisconnect(SocketWrapper socketWrapper) {
    }

    /**
     * A buddy connection closed
     *
     * @param buddySocketWrapper the closed connection
     */
    default void onBuddyDisconnect(BuddySocketWrapper buddySocketWrapper) {
    }
}
