package eu.eahub.services.core;

import eu.eahub.dirtysdk.LobbyTagField;
import eu.eahub.dto.Room;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.steps.SocketWriter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiger Woods head-to-head challenges, negotiated in private messages before a session starts
 * The challenger hosts: challenge, then accept from the opponent, then chal (ready) from both
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TwChallengeService implements ConnectionListener {

    public static final int ATTR_PRIVATE = 0x10000;
    private static final long PENDING_NANOS = 120_000_000_000L;
    private static final Set<String> REPLIES = Set.of("decline", "revoke", "busy", "ignore", "ignoreall");

    private final SocketWriter socketWriter;
    private final Map<Long, Challenge> challenges = new ConcurrentHashMap<>();
    // AUTH token sent in +ses -> game id, to check the report of a match
    private final Map<String, Long> sessionTokens = new ConcurrentHashMap<>();

    @Getter
    @Setter
    public static final class Challenge {
        private final SocketWrapper host;
        private final SocketWrapper guest;
        private final Long roomId;
        private final long created = System.nanoTime();
        private boolean accepted;
        private boolean hostReady;
        private boolean guestReady;
        private boolean started;
        private Integer course;
        private Long gameId;

        Challenge(SocketWrapper host, SocketWrapper guest, Long roomId) {
            this.host = host;
            this.guest = guest;
            this.roomId = roomId;
        }

        public SocketWrapper opponent(SocketWrapper wrapper) {
            return wrapper == host ? guest : host;
        }
    }

    /**
     * Handle a challenge control message (challenge, accept, decline, revoke, busy, ignore, ignoreall)
     * Control messages go only to the opponent; echoing them to the sender would make its challenge state machine
     * consume its own invitation
     *
     * @param socket      the sender's socket
     * @param sender      the sender
     * @param senderRoom  the sender's room
     * @param other       the opponent, or null if not online
     * @param otherRoom   the opponent's room
     * @param blocked     whether the opponent ignores the sender
     * @param text        the message text: the action, then the match setup as key=value lines
     * @param flags       the message attributes
     */
    public synchronized void message(Socket socket, SocketWrapper sender, Room senderRoom, SocketWrapper other, Room otherRoom,
                                     boolean blocked, String text, int flags) {
        if (other == null || other == sender || senderRoom == null || otherRoom != senderRoom || blocked) {
            fail(socket, "mesg", "nrom");
            return;
        }
        String action = text.split("\n", 2)[0];
        if ("challenge".equals(action)) {
            for (SocketWrapper player : List.of(sender, other)) {
                Challenge pending = challenges.get(personaId(player));
                if (pending != null && !pending.isStarted() && System.nanoTime() - pending.getCreated() > PENDING_NANOS) {
                    cancel(player, true);
                }
            }
            if (challenges.containsKey(personaId(sender)) || challenges.containsKey(personaId(other))) {
                fail(socket, "mesg", "ingm");
                return;
            }
            Challenge challenge = new Challenge(sender, other, senderRoom.getId());
            // The setup rides on the challenge as key=value lines; only it names the course
            for (String line : text.split("\n")) {
                if (line.startsWith("COUR=") && line.substring(5).strip().matches("[0-9]{1,3}")) {
                    challenge.setCourse(Integer.parseInt(line.substring(5).strip()));
                }
            }
            challenges.put(personaId(sender), challenge);
            challenges.put(personaId(other), challenge);
        } else {
            Challenge challenge = challenges.get(personaId(sender));
            if (challenge == null || challenge.opponent(sender) != other || challenge.isStarted()) {
                fail(socket, "mesg", "nrom");
                return;
            }
            if ("accept".equals(action)) {
                if (sender != challenge.getGuest()) {
                    fail(socket, "mesg", "maut");
                    return;
                }
                challenge.setAccepted(true);
            } else if (!REPLIES.contains(action)) {
                fail(socket, "mesg", "maut");
                return;
            }
        }
        socketWriter.writeTagFields(socket, "mesg", Map.of());
        socketWriter.writeTagFields(other.getSocket(), "+msg",
                Map.of("N", sender.getPersonaEntity().getPers(), "T", text, "F", LobbyTagField.encodeFlags(flags | ATTR_PRIVATE)));
        log.info("TW04 challenge: {} {} -> {}", sender.getPersonaEntity().getPers(), action, other.getPersonaEntity().getPers());
        if (!Set.of("challenge", "accept").contains(action)) {
            cancel(sender, false);
        }
    }

    /**
     * Mark a player ready (chal HOST=0/1)
     *
     * @param wrapper the player
     * @param other   the opponent named in PERS, or null
     * @param host    the HOST value sent
     * @param sameRoom whether both players are in the same room
     * @return the challenge if both players are now ready, the error code otherwise ("" when only this player is ready)
     */
    public synchronized Ready ready(SocketWrapper wrapper, SocketWrapper other, String host, boolean sameRoom) {
        Challenge challenge = wrapper.getPersonaEntity() == null ? null : challenges.get(personaId(wrapper));
        if (challenge == null || other == null || challenge.opponent(wrapper) != other || challenges.get(personaId(other)) != challenge || !sameRoom) {
            return new Ready(null, "nrom");
        }
        if (!challenge.isAccepted() || !(wrapper == challenge.getHost() ? "1" : "0").equals(host)) {
            return new Ready(null, "maut");
        }
        if (wrapper == challenge.getHost()) {
            challenge.setHostReady(true);
        } else {
            challenge.setGuestReady(true);
        }
        if (challenge.isHostReady() && challenge.isGuestReady() && !challenge.isStarted()) {
            challenge.setStarted(true);
            return new Ready(challenge, "");
        }
        return new Ready(null, "");
    }

    public record Ready(Challenge start, String error) {
    }

    /**
     * Get the challenge a player is part of
     *
     * @param wrapper the player
     * @return the challenge, if any
     */
    public Optional<Challenge> challenge(SocketWrapper wrapper) {
        return wrapper.getPersonaEntity() == null ? Optional.empty() : Optional.ofNullable(challenges.get(personaId(wrapper)));
    }

    /**
     * Drop a player's challenge, e.g. when they leave the room or cancel with chal
     *
     * @param wrapper the player
     * @param notify  whether to send the opponent a revoke for a challenge that has not started
     */
    public synchronized void cancel(SocketWrapper wrapper, boolean notify) {
        if (wrapper.getPersonaEntity() == null) {
            return;
        }
        Challenge challenge = challenges.remove(personaId(wrapper));
        if (challenge == null) {
            return;
        }
        SocketWrapper other = challenge.opponent(wrapper);
        challenges.remove(personaId(other), challenge);
        if (notify && !challenge.isStarted() && other.getSocket() != null && !other.getSocket().isClosed()) {
            socketWriter.writeTagFields(other.getSocket(), "+msg", Map.of("N", wrapper.getPersonaEntity().getPers(), "T", "revoke", "F", "P3"));
        }
    }

    public void addSessionToken(String token, Long gameId) {
        sessionTokens.put(token, gameId);
    }

    public Long getGameIdBySessionToken(String token) {
        return token == null ? null : sessionTokens.get(token);
    }

    public void removeSessionToken(Long gameId) {
        sessionTokens.values().removeIf(gameId::equals);
    }

    @Override
    public void onDisconnect(SocketWrapper socketWrapper) {
        cancel(socketWrapper, true);
    }

    private static Long personaId(SocketWrapper wrapper) {
        return wrapper.getPersonaEntity().getId();
    }

    private void fail(Socket socket, String verb, String code) {
        socketWriter.writeText(socket, verb + code, "");
    }
}
