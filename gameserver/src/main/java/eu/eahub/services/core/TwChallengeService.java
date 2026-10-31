package eu.eahub.services.core;

import eu.eahub.dirtysdk.LobbyTagField;
import eu.eahub.dto.Room;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.dto.TwChallenge;
import eu.eahub.steps.SocketWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiger Woods head-to-head challenges, negotiated in private messages before a session starts
 * The challenger hosts: challenge, then accept from the opponent, then chal (ready) from both
 * A challenge is made in a room, or from Messenger to a player anywhere in the lobby; the setup's MFLG then gives the mode
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TwChallengeService {

    public static final int ATTR_PRIVATE = 0x10000;
    public static final int MODE_RANKED = TwChallenge.MODE_RANKED;
    private static final long PENDING_NANOS = 120_000_000_000L;
    private static final Set<String> REPLIES = Set.of("decline", "revoke", "busy", "ignore", "ignoreall");
    private static final Set<String> KEPT = Set.of("challenge", "accept");

    private final SocketWriter socketWriter;
    private final Map<Long, TwChallenge> challenges = new ConcurrentHashMap<>();
    // AUTH token sent in +ses -> game id, to check the report of a match
    private final Map<String, Long> sessionTokens = new ConcurrentHashMap<>();

    /**
     * Handle a challenge control message (challenge, accept, decline, revoke, busy, ignore, ignoreall)
     * Control messages go only to the opponent; echoing them to the sender would make its challenge state machine
     * consume its own invitation
     *
     * @param socket     the sender's socket
     * @param sender     the sender
     * @param senderRoom the sender's room, or null
     * @param other      the opponent, or null if not online
     * @param otherRoom  the opponent's room, or null
     * @param blocked    whether the opponent ignores the sender
     * @param text       the message text: the action, then the match setup as key=value lines
     * @param flags      the message attributes
     */
    public void message(Socket socket, SocketWrapper sender, Room senderRoom, SocketWrapper other, Room otherRoom,
                        boolean blocked, String text, int flags) {
        if (other == null || other == sender || blocked) {
            socketWriter.writeText(socket, "mesgnrom", "");
            return;
        }
        boolean sameRoom = senderRoom != null && otherRoom == senderRoom;
        String action = text.split("\n", 2)[0];
        Long roomId = sameRoom ? senderRoom.getId() : null;
        String error;
        synchronized (this) {
            error = "challenge".equals(action) ? challenge(sender, other, roomId, text) : reply(sender, other, sameRoom, action);
        }
        if (!error.isEmpty()) {
            socketWriter.writeText(socket, "mesg" + error, "");
            return;
        }
        socketWriter.writeTagFields(socket, "mesg", Map.of());
        socketWriter.writeTagFields(other.getSocket(), "+msg",
                Map.of("N", sender.getPersonaEntity().getPers(), "T", text, "F", LobbyTagField.encodeFlags(flags | ATTR_PRIVATE)));
        log.info("TW04 challenge: {} {} -> {}", sender.getPersonaEntity().getPers(), action, other.getPersonaEntity().getPers());
        if (!KEPT.contains(action)) {
            cancel(sender, false);
        }
    }

    /**
     * Record a new challenge
     *
     * @param sender the challenger
     * @param other  the opponent
     * @param roomId the room both players are in, or null
     * @param text   the message text, with the setup as key=value lines
     * @return the error code, or an empty string
     */
    private String challenge(SocketWrapper sender, SocketWrapper other, Long roomId, String text) {
        for (SocketWrapper player : List.of(sender, other)) {
            TwChallenge pending = challenges.get(personaId(player));
            if (pending != null && !pending.isStarted() && System.nanoTime() - pending.getCreated() > PENDING_NANOS) {
                cancel(player, true);
            }
        }
        if (challenges.containsKey(personaId(sender)) || challenges.containsKey(personaId(other))) {
            return "ingm";
        }
        TwChallenge challenge = new TwChallenge(sender, other, roomId);
        // The setup rides on the challenge as key=value lines; only it names the course and the mode
        for (String line : text.split("\n")) {
            if (line.startsWith("COUR=") && line.substring(5).strip().matches("[0-9]{1,3}")) {
                challenge.setCourse(Integer.parseInt(line.substring(5).strip()));
            } else if (line.startsWith("MFLG=") && line.substring(5).strip().matches("[0-9]{1,9}")) {
                challenge.setModeFlags(Integer.parseInt(line.substring(5).strip()));
            }
        }
        // Outside a room, only the setup tells Match from Stroke Play
        if (roomId == null && !challenge.hasMode()) {
            return "nrom";
        }
        challenges.put(personaId(sender), challenge);
        challenges.put(personaId(other), challenge);
        return "";
    }

    /**
     * Record a reply to a challenge
     *
     * @param sender   the replying player
     * @param other    the opponent
     * @param sameRoom whether both players are in the same room
     * @param action   the reply
     * @return the error code, or an empty string
     */
    private String reply(SocketWrapper sender, SocketWrapper other, boolean sameRoom, String action) {
        TwChallenge challenge = challenges.get(personaId(sender));
        if (challenge == null || challenge.getOpponent(sender) != other || challenge.isStarted()
                || (challenge.getRoomId() != null && !sameRoom)) {
            return "nrom";
        }
        if ("accept".equals(action)) {
            if (sender != challenge.getGuest()) {
                return "maut";
            }
            challenge.setAccepted(true);
        } else if (!REPLIES.contains(action)) {
            return "maut";
        }
        return "";
    }

    /**
     * Mark a player ready (chal HOST=0/1), and reply to them
     *
     * @param socket    the player's socket
     * @param wrapper   the player
     * @param other     the opponent named in PERS, or null
     * @param host      the HOST value sent
     * @param room      the player's room, or null
     * @param otherRoom the opponent's room, or null
     * @return the challenge to start, once both players are ready, otherwise null
     */
    public TwChallenge ready(Socket socket, SocketWrapper wrapper, SocketWrapper other, String host, Room room, Room otherRoom) {
        TwChallenge start = null;
        String error = "";
        synchronized (this) {
            TwChallenge challenge = wrapper.getPersonaEntity() == null ? null : challenges.get(personaId(wrapper));
            // A challenge made in a room holds while both players stay in it
            boolean inPlace = challenge != null && (challenge.getRoomId() == null
                    || (room != null && room == otherRoom && challenge.getRoomId().equals(room.getId())));
            if (challenge == null || other == null || challenge.getOpponent(wrapper) != other
                    || challenges.get(personaId(other)) != challenge || !inPlace) {
                error = "nrom";
            } else if (!challenge.isAccepted() || !(wrapper == challenge.getHost() ? "1" : "0").equals(host)) {
                error = "maut";
            } else {
                if (wrapper == challenge.getHost()) {
                    challenge.setHostReady(true);
                } else {
                    challenge.setGuestReady(true);
                }
                if (challenge.isHostReady() && challenge.isGuestReady() && !challenge.isStarted()) {
                    challenge.setStarted(true);
                    start = challenge;
                }
            }
        }
        if (!error.isEmpty()) {
            socketWriter.writeText(socket, "chal" + error, "");
            return null;
        }
        socketWriter.writeTagFields(socket, "chal", Map.of("MODE", "play"));
        return start;
    }

    /**
     * Get the challenge a player is part of
     *
     * @param wrapper the player
     * @return the challenge, or null
     */
    public TwChallenge getChallenge(SocketWrapper wrapper) {
        return wrapper.getPersonaEntity() == null ? null : challenges.get(personaId(wrapper));
    }

    /**
     * Drop a player's challenge, e.g. when they leave the room, cancel with chal, or their session ends
     *
     * @param wrapper the player
     * @param notify  whether to send the opponent a revoke for a challenge that has not started
     */
    public void cancel(SocketWrapper wrapper, boolean notify) {
        if (wrapper.getPersonaEntity() == null) {
            return;
        }
        TwChallenge challenge;
        synchronized (this) {
            challenge = challenges.remove(personaId(wrapper));
            if (challenge == null) {
                return;
            }
            challenges.remove(personaId(challenge.getOpponent(wrapper)), challenge);
        }
        SocketWrapper other = challenge.getOpponent(wrapper);
        if (notify && !challenge.isStarted() && other.getSocket() != null) {
            socketWriter.writeTagFields(other.getSocket(), "+msg", Map.of("N", wrapper.getPersonaEntity().getPers(), "T", "revoke", "F", "P3"));
        }
    }

    /**
     * Remember the AUTH token of a started session
     *
     * @param token  the token sent in +ses
     * @param gameId the game
     */
    public void addSessionToken(String token, Long gameId) {
        sessionTokens.put(token, gameId);
    }

    /**
     * Get the game a report's AUTH token belongs to
     *
     * @param token the token, may be null
     * @return the game id, or null
     */
    public Long getGameIdBySessionToken(String token) {
        return token == null ? null : sessionTokens.get(token);
    }

    /**
     * Forget the tokens of an ended session
     *
     * @param gameId the game
     */
    public void removeSessionToken(Long gameId) {
        sessionTokens.values().removeIf(gameId::equals);
    }

    /**
     * Get the persona id a challenge is keyed by
     *
     * @param wrapper the player
     * @return the persona id
     */
    private static Long personaId(SocketWrapper wrapper) {
        return wrapper.getPersonaEntity().getId();
    }
}
