package eu.eahub.services.core;

import eu.eahub.dirtysdk.LobbyTagField;
import eu.eahub.dto.Room;
import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.dto.TwStanding;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.repositories.buddy.BuddyRepository;
import eu.eahub.services.server.SocketManager;
import eu.eahub.services.social.TwBuddyService;
import eu.eahub.services.social.TwChatHistoryService;
import eu.eahub.services.stats.TwStatsService;
import eu.eahub.steps.SocketWriter;
import eu.eahub.utils.TwAddressUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Tiger Woods PGA Tour 2004 lobby: rooms are listed and joined by name, players can create rooms, and each client is
 * only sent the room, user and message updates it subscribed to with sele. +usr records are indexed by position in the
 * room's roster, so the roster is sent again whenever it changes
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TwRoomService {

    // The game's own rooms: Match Play and Stroke Play, by category letter (Tour, International, Golf Beginner...)
    private static final List<String> ROOM_NAMES = List.of("Match.T.East", "Match.I.West", "Match.G.Beginner", "Match.E.Advanced",
            "Match.R.Open", "Stroke.T.East", "Stroke.I.West", "Stroke.G.Beginner", "Stroke.E.Advanced", "Stroke.R.Open");
    // Players can create rooms in the game's Created category (C)
    private static final String CREATE_PATTERN = "(Match|Stroke)\\.C\\.[ -~]+";
    private static final int NAME_LENGTH = 31; // The game's room name buffer is 32 bytes
    private static final int MAX_ROOMS = 74;
    private static final int ROOM_LIMIT = 50;
    private static final int ATTR_IGNORE = 0x200000;
    private static final int ATTR_CHALLENGE = 0x40000000;
    private static final int MAX_MESSAGE_LENGTH = 1023;
    // PCSX2's Sockets mode gives every guest this virtual address, which no other player can reach
    private static final String PCSX2_ADDRESS_PREFIX = "192.0.2.";

    private final SocketManager socketManager;
    private final SocketWriter socketWriter;
    private final BuddyRepository buddyRepository;
    private final TwStatsService twStatsService;
    private final TwChallengeService twChallengeService;
    private final TwChatHistoryService twChatHistoryService;
    private final TwBuddyService twBuddyService;
    private final List<Room> rooms = new CopyOnWriteArrayList<>();
    private final AtomicLong nextRoomId = new AtomicLong(1);

    /**
     * Create the game's rooms
     *
     * @param vers The game version
     */
    public void generateRooms(String vers) {
        for (String name : ROOM_NAMES) {
            addRoom(vers, name, "", ROOM_LIMIT, false);
        }
    }

    /**
     * Add a room
     *
     * @param vers     The game version
     * @param name     The room name
     * @param password The room password, or an empty string
     * @param limit    The maximum number of players
     * @param created  Whether a player created it
     * @return The room
     */
    private Room addRoom(String vers, String name, String password, int limit, boolean created) {
        Room room = new Room();
        room.setId(nextRoomId.getAndIncrement());
        room.setName(name);
        room.setVers(vers);
        room.setPassword(password);
        room.setLimit(limit);
        room.setCreated(created);
        rooms.add(room);
        return room;
    }

    /**
     * Get a room by name
     *
     * @param vers The game version
     * @param name The room name
     * @return The room, or null
     */
    public Room getRoomByName(String vers, String name) {
        return rooms.stream()
                .filter(room -> room.getVers().equals(vers) && room.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get the room a player is in
     *
     * @param personaId The persona ID
     * @return The room, or null
     */
    public Room getRoomByPersonaId(Long personaId) {
        return rooms.stream()
                .filter(room -> room.getPersonaIds().contains(personaId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Create a room (room), and join it
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void room(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (socketWrapper.getPersonaEntity() == null) {
            socketWriter.writeText(socket, "roomnusr", "");
            return;
        }
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String vers = vers(socketWrapper);
        String name = fields.getOrDefault("NAME", "");
        Room room;
        synchronized (this) {
            if (name.length() > NAME_LENGTH || !name.matches(CREATE_PATTERN)) {
                socketWriter.writeText(socket, "roomname", "");
                return;
            }
            if (getRoomByName(vers, name) != null) {
                socketWriter.writeText(socket, "roomdupl", "");
                return;
            }
            if (rooms.stream().filter(existing -> existing.getVers().equals(vers)).count() >= MAX_ROOMS) {
                socketWriter.writeText(socket, "roomfull", "");
                return;
            }
            int limit;
            try {
                limit = Integer.parseInt(fields.getOrDefault("MAX", String.valueOf(ROOM_LIMIT)));
            } catch (NumberFormatException e) {
                limit = ROOM_LIMIT;
            }
            room = addRoom(vers, name, fields.getOrDefault("PASS", ""), Math.clamp(limit, 1, ROOM_LIMIT), true);
        }
        join(socketWrapper, room, "room");
    }

    /**
     * Join a room by name, or leave the current room without NAME (move)
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void move(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (socketWrapper.getPersonaEntity() == null) {
            socketWriter.writeText(socket, "movenusr", "");
            return;
        }
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String name = fields.getOrDefault("NAME", "");
        Room room = null;
        if (!name.isEmpty()) {
            room = getRoomByName(vers(socketWrapper), name);
            if (room == null) {
                socketWriter.writeText(socket, "movename", "");
                return;
            }
            if (!room.getPassword().isEmpty() && !room.getPassword().equals(fields.getOrDefault("PASS", ""))) {
                socketWriter.writeText(socket, "movepass", "");
                return;
            }
            if (!room.getPersonaIds().contains(socketWrapper.getPersonaEntity().getId()) && room.getPersonaIds().size() >= room.getLimit()) {
                socketWriter.writeText(socket, "movefull", "");
                return;
            }
        }
        join(socketWrapper, room, "move");
    }

    /**
     * Peek in a room (peek): its player count, and its roster for a client subscribed to user updates
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void peek(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (socketWrapper.getPersonaEntity() == null) {
            socketWriter.writeText(socket, "peeknusr", "");
            return;
        }
        Room room = getRoomByName(vers(socketWrapper), LobbyTagField.decode(socketData.getInputMessage()).getOrDefault("NAME", ""));
        List<SocketWrapper> users = room == null ? List.of() : occupants(vers(socketWrapper), room);
        socketWriter.writeTagFields(socket, "peek", Map.of("COUNT", String.valueOf(users.size())));
        if (socketWrapper.isUserUpdates()) {
            pushUsers(socket, socketWrapper, users, standings(users, vers(socketWrapper)));
        }
    }

    /**
     * Send a room message, or a private message with PRIV (mesg)
     * Challenge control messages (ATTR with the challenge bit) are handled by the challenge service
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void mesg(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (socketWrapper.getPersonaEntity() == null) {
            socketWriter.writeText(socket, "mesgnusr", "");
            return;
        }
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String target = fields.getOrDefault("PRIV", "");
        String text = fields.getOrDefault("TEXT", "");
        if (text.length() > MAX_MESSAGE_LENGTH) {
            socketWriter.writeText(socket, "mesgfull", "");
            return;
        }
        String vers = vers(socketWrapper);
        PersonaEntity sender = socketWrapper.getPersonaEntity();
        int attr = LobbyTagField.decodeFlags(fields.getOrDefault("ATTR", "")) & ~ATTR_IGNORE;
        SocketWrapper targetWrapper = target.isEmpty() ? null : socketManager.getSocketWrapperByPersonaNameAndVers(target, vers);
        if (targetWrapper != null && targetWrapper.getPersonaEntity() == null) {
            targetWrapper = null;
        }
        Room senderRoom = getRoomByPersonaId(sender.getId());
        Set<Long> ignoredBy = ignoredBy(sender);
        if ((attr & ATTR_CHALLENGE) != 0) {
            Room targetRoom = targetWrapper == null ? null : getRoomByPersonaId(targetWrapper.getPersonaEntity().getId());
            boolean blocked = targetWrapper != null && ignoredBy.contains(targetWrapper.getPersonaEntity().getId());
            twChallengeService.message(socket, socketWrapper, senderRoom, targetWrapper, targetRoom, blocked, text, attr);
            return;
        }
        socketWriter.writeTagFields(socket, "mesg", Map.of());
        if (!target.isEmpty() && (targetWrapper == null || ignoredBy.contains(targetWrapper.getPersonaEntity().getId()))) {
            return;
        }
        twChatHistoryService.add(sender.getId(), sender.getPers(), targetWrapper == null ? 0 : targetWrapper.getPersonaEntity().getId(),
                senderRoom == null ? "" : senderRoom.getName(), text);
        String flags = LobbyTagField.encodeFlags(target.isEmpty() ? attr : attr | TwChallengeService.ATTR_PRIVATE);
        for (SocketWrapper other : socketManager.getSocketWrapperByVers(vers)) {
            if (other.getPersonaEntity() == null || !other.isMessageUpdates() || ignoredBy.contains(other.getPersonaEntity().getId())) {
                continue;
            }
            boolean receives = target.isEmpty()
                    ? senderRoom != null && senderRoom.getPersonaIds().contains(other.getPersonaEntity().getId())
                    : other == targetWrapper || other == socketWrapper;
            if (receives) {
                socketWriter.writeTagFields(other.getSocket(), "+msg", Map.of("N", sender.getPers(), "T", text, "F", flags));
            }
        }
    }

    /**
     * The lobby connection closed: the player's challenge is dropped, they leave their room, and their Messenger
     * connections are closed
     *
     * @param socketWrapper The closed connection
     */
    public void endPersonaConnection(SocketWrapper socketWrapper) {
        twChallengeService.cancel(socketWrapper, true);
        join(socketWrapper, null, null);
        twBuddyService.endLobbyConnection(socketWrapper);
    }

    /**
     * Move a player to a room, or out of their room (room null), then send the updates
     * A pending challenge is dropped when the player changes room, and an empty created room is removed
     *
     * @param socketWrapper The player
     * @param room          The room to join, or null to leave
     * @param verb          The reply to send the player (room or move), or null when they are leaving the lobby
     */
    private void join(SocketWrapper socketWrapper, Room room, String verb) {
        if (socketWrapper.getPersonaEntity() == null) {
            return;
        }
        Long personaId = socketWrapper.getPersonaEntity().getId();
        Room previous;
        synchronized (this) {
            previous = getRoomByPersonaId(personaId);
            if (previous != room) {
                if (previous != null) {
                    previous.getPersonaIds().remove(personaId);
                    removeIfEmpty(previous);
                }
                if (room != null) {
                    room.getPersonaIds().add(personaId);
                }
            }
        }
        if (previous != room) {
            twChallengeService.cancel(socketWrapper, true);
        }
        String vers = vers(socketWrapper);
        if (verb != null) {
            socketWriter.writeTagFields(socketWrapper.getSocket(), verb, Map.of("NAME", room == null ? "" : room.getName(),
                    "COUNT", String.valueOf(room == null ? 0 : occupants(vers, room).size())));
        }
        if (room == null) {
            log.info("TW04 lobby: {} left {}", socketWrapper.getPersonaEntity().getPers(), previous == null ? "the lobby" : previous.getName());
        } else {
            log.info("TW04 lobby: {} joined {}", socketWrapper.getPersonaEntity().getPers(), room.getName());
        }
        if (previous != null || room != null) {
            broadcastRooms(vers);
        }
        if (previous != null) {
            broadcastUsers(vers, previous);
        }
        if (room != null && room != previous) {
            broadcastUsers(vers, room);
        }
        if (room == null && verb != null && socketWrapper.isUserUpdates()) {
            pushUsers(socketWrapper.getSocket(), socketWrapper, List.of(), Map.of());
        }
    }

    /**
     * Remove a created room once it has no players and no open session
     *
     * @param room The room
     * @return true if it was removed
     */
    private boolean removeIfEmpty(Room room) {
        if (room.isCreated() && room.getPersonaIds().isEmpty() && room.getGameIds().isEmpty()) {
            return rooms.remove(room);
        }
        return false;
    }

    /**
     * A session set up in a room has ended: an empty created room can now be removed
     *
     * @param game The ended game
     */
    public void removeSession(GameEntity game) {
        Room room = rooms.stream().filter(candidate -> candidate.getId().equals(game.getRoomId().longValue())).findFirst().orElse(null);
        if (room != null) {
            room.getGameIds().remove(game.getId());
            if (removeIfEmpty(room)) {
                broadcastRooms(game.getVers());
            }
        }
    }

    /**
     * Send a client the room list, as requested with sele ROOMS=1
     *
     * @param socket The socket to write the response to
     * @param vers   The game version
     */
    public void pushRooms(Socket socket, String vers) {
        for (Room room : rooms) {
            if (room.getVers().equals(vers)) {
                socketWriter.writeTagFields(socket, "+rom", Map.of("I", String.valueOf(room.getId()), "N", room.getName(), "H", "",
                        "T", String.valueOf(occupants(vers, room).size()), "A", "0", "F", "0"));
            }
        }
    }

    /**
     * Send a client its room's users, as requested with sele USERS=1
     *
     * @param socket        The socket to write the response to
     * @param socketWrapper The socket wrapper of the client
     */
    public void pushRoomUsers(Socket socket, SocketWrapper socketWrapper) {
        Room room = socketWrapper.getPersonaEntity() == null ? null : getRoomByPersonaId(socketWrapper.getPersonaEntity().getId());
        if (room != null) {
            List<SocketWrapper> users = occupants(vers(socketWrapper), room);
            pushUsers(socket, socketWrapper, users, standings(users, vers(socketWrapper)));
        }
    }

    /**
     * Send the room list to every client subscribed to room updates
     *
     * @param vers The game version
     */
    private void broadcastRooms(String vers) {
        for (SocketWrapper client : socketManager.getSocketWrapperByVers(vers)) {
            if (client.isRoomUpdates()) {
                pushRooms(client.getSocket(), vers);
            }
        }
    }

    /**
     * Send the room's roster to everyone in the room who subscribed to user updates (e.g. after a result changes a rank)
     *
     * @param vers The game version
     * @param room The room
     */
    public void broadcastUsers(String vers, Room room) {
        List<SocketWrapper> users = occupants(vers, room);
        Map<Long, TwStanding> standings = standings(users, vers);
        for (SocketWrapper client : users) {
            if (client.isUserUpdates()) {
                pushUsers(client.getSocket(), client, users, standings);
            }
        }
    }

    /**
     * Get the connected players of a room, by name
     *
     * @param vers The game version
     * @param room The room
     * @return The players
     */
    private List<SocketWrapper> occupants(String vers, Room room) {
        return socketManager.getSocketWrapperByVers(vers).stream()
                .filter(client -> client.getPersonaEntity() != null && room.getPersonaIds().contains(client.getPersonaEntity().getId()))
                .sorted(Comparator.comparing(client -> client.getPersonaEntity().getPers()))
                .toList();
    }

    /**
     * Get the players' ranks and statistics
     *
     * @param users The players
     * @param vers  The game version
     * @return The standings by persona ID
     */
    private Map<Long, TwStanding> standings(List<SocketWrapper> users, String vers) {
        return twStatsService.standings(users.stream().map(SocketWrapper::getPersonaEntity).toList(), vers);
    }

    /**
     * Send a client a roster: one +usr per player, then one per stale index to remove the players who left
     *
     * @param socket    The socket to write the response to
     * @param recipient The socket wrapper of the client
     * @param users     The players
     * @param standings The players' standings
     */
    private void pushUsers(Socket socket, SocketWrapper recipient, List<SocketWrapper> users, Map<Long, TwStanding> standings) {
        for (int index = 0; index < users.size(); index++) {
            PersonaEntity persona = users.get(index).getPersonaEntity();
            TwStanding standing = standings.get(persona.getId());
            socketWriter.writeTagFields(socket, "+usr", Map.of("I", String.valueOf(index), "N", persona.getPers(), "P", "good",
                    "A", peerAddress(users.get(index), recipient), "R", String.valueOf(standing.getRank()), "S", standing.getPacked()));
        }
        // An index without N removes the previous occupant from the client's list
        for (int index = users.size(); index < recipient.getRosterSize(); index++) {
            socketWriter.writeTagFields(socket, "+usr", Map.of("I", String.valueOf(index)));
        }
        recipient.setRosterSize(users.size());
    }

    /**
     * The address a player gives another to reach them: the address their lobby connection came from, except for
     * two players behind the same router, who reach each other on the LAN address reported with addr
     *
     * @param peer   The player to reach
     * @param viewer The player who connects to them
     * @return The address
     */
    public String peerAddress(SocketWrapper peer, SocketWrapper viewer) {
        String observed = peer.getSocket().getInetAddress().getHostAddress();
        String viewerAddress = viewer.getSocket().getInetAddress().getHostAddress();
        String reported = peer.getReportedAddress();
        if (observed.equals(viewerAddress) && !reported.startsWith(PCSX2_ADDRESS_PREFIX) && TwAddressUtils.isPeerIpv4(reported)) {
            return reported;
        }
        return observed;
    }

    /**
     * Get the players who ignore a player
     *
     * @param persona The player
     * @return The persona IDs of the players who have them on their ignore list
     */
    private Set<Long> ignoredBy(PersonaEntity persona) {
        return buddyRepository.findByPersonaInEitherDirectionAndList(persona, "I").stream()
                .filter(buddy -> buddy.getToPersona().getId().equals(persona.getId()))
                .map(buddy -> buddy.getFromPersona().getId())
                .collect(Collectors.toSet());
    }

    /**
     * Get the game version of a connection
     *
     * @param socketWrapper The connection
     * @return The game version
     */
    private static String vers(SocketWrapper socketWrapper) {
        return socketWrapper.getPersonaConnectionEntity().getVers();
    }
}
