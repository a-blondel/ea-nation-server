package eu.eahub.services.core;

import eu.eahub.config.GameServerConfig;
import eu.eahub.dirtysdk.LobbyTagField;
import eu.eahub.dto.Room;
import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.GameConnectionEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.repositories.buddy.BuddyRepository;
import eu.eahub.repositories.core.GameConnectionRepository;
import eu.eahub.repositories.core.GameRepository;
import eu.eahub.repositories.core.PersonaConnectionRepository;
import eu.eahub.services.server.GameServerService;
import eu.eahub.services.server.SocketManager;
import eu.eahub.services.social.ChatHistoryService;
import eu.eahub.services.stats.TwStatsService;
import eu.eahub.steps.SocketWriter;
import eu.eahub.utils.GameUtils;
import eu.eahub.utils.PersonaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eu.eahub.services.server.GameServerService.ALL_TW;
import static eu.eahub.services.server.GameServerService.PS2_FIFA08;
import static eu.eahub.utils.SocketUtils.TAB_CHAR;
import static eu.eahub.utils.SocketUtils.getValueFromSocket;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {
    private final GameServerConfig gameServerConfig;
    private final PersonaConnectionRepository personaConnectionRepository;
    private final GameConnectionRepository gameConnectionRepository;
    private final GameRepository gameRepository;
    private final GameServerService gameServerService;
    private final GameUtils gameUtils;
    private final PersonaUtils personaUtils;
    private final SocketManager socketManager;
    private final SocketWriter socketWriter;
    private final TwStatsService twStatsService;
    private final TwChallengeService twChallengeService;
    private final ChatHistoryService chatHistoryService;
    private final BuddyRepository buddyRepository;
    private final List<Room> rooms = new CopyOnWriteArrayList<>();
    private final AtomicLong nextRoomId = new AtomicLong(1);

    /**
     * Distribute room change updates
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void rom(Socket socket, SocketData socketData) {
        String vers = socketManager.getSocketWrapperBySocket(socket).getPersonaConnectionEntity().getVers();
        Room room = getRoomByVers(vers);

        if (room != null) {
            Map<String, String> content = Stream.of(new String[][]{
                    {"I", String.valueOf(room.getId())}, // Room identifier
                    {"N", "LVL.1"}, // Room name
                    {"DN", room.getName()}, // Actual room name displayed
                    {"D", ""}, // Description (132 bytes max)
                    {"F", "A"}, // Room flags
                    {"T", String.valueOf(room.getPersonaIds().size())}, // Current room population
                    {"L", "50"}, // Max users allowed in room
            }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

            socketData.setOutputData(content);
            socketData.setIdMessage("+rom");
            socketWriter.write(socket, socketData);
        }
    }

    /**
     * Move to a different room or out of a room altogether.
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void move(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (isTw(socket)) {
            twMove(socket, socketData, socketWrapper);
            return;
        }
        String ident = getValueFromSocket(socketData.getInputMessage(), "IDENT");
        String name = getValueFromSocket(socketData.getInputMessage(), "NAME");

        if (ident == null) {
            if (name != null) {
                // SSX3 only sends the NAME field with the value "LVL.1" (fixed name for every game),
                // then we use the game version to determine the room name instead of relying on the IDENT field
                ident = rooms.stream()
                        .filter(r -> r.getName().equals(socketWrapper.getPersonaConnectionEntity().getVers()))
                        .findFirst()
                        .map(r -> r.getId().toString())
                        .orElse(null);
            }
            if (ident == null) {
                ident = "0"; // Default to room 0 to leave room (SSX3 sends empty instead of 0 when leaving room)
            }
        }

        long roomId = Long.parseLong(ident);
        addPersonaToRoom(roomId, socketWrapper);

        Room room = getRoomById(roomId);

        if (!ident.equals("0")) {
            Map<String, String> content = Stream.of(new String[][]{
                    {"IDENT", ident},
                    {"NAME", "LVL.1"},
                    {"COUNT", String.valueOf(room.getPersonaIds().size())},
                    {"FLAGS", room.getFlags()},
                    {"LIMIT", "50"},
                    {"DESC", ""},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
            socketData.setOutputData(content);
            socketWriter.write(socket, socketData);

            // Notify about all games in the room
            for (Long gameId : room.getGameIds()) {
                Optional<GameEntity> gameEntityOpt = gameRepository.findById(gameId);
                gameEntityOpt.ifPresent(gameEntity -> socketWriter.write(socket, new SocketData("+agm", null, gameUtils.getGameInfo(gameEntity))));
            }
            // Notify about all players in the room
            for (SocketWrapper clientWrapper : socketManager.getSocketWrapperByVers(socketWrapper.getPersonaConnectionEntity().getVers())) {
                if (clientWrapper.getPersonaEntity() != null) {
                    Room clientRoom = getRoomByPersonaId(clientWrapper.getPersonaEntity().getId());
                    if (clientRoom != null && clientRoom.getId().equals(roomId)) {
                        socketWriter.write(socket, new SocketData("+usr", null, personaUtils.getPersonaInfo(clientWrapper.getSocket(), clientWrapper, room)));
                        socketWriter.write(socket, new SocketData("+who", null, personaUtils.getPersonaInfo(clientWrapper.getSocket(), clientWrapper, room)));
                        // Also notify each client in the room about the new user
                        if (!clientWrapper.getSocket().equals(socket)) {
                            socketWriter.write(clientWrapper.getSocket(), new SocketData("+usr", null, personaUtils.getPersonaInfo(socket, socketWrapper, room)));
                            socketWriter.write(clientWrapper.getSocket(), new SocketData("+who", null, personaUtils.getPersonaInfo(clientWrapper.getSocket(), clientWrapper, room)));
                        }
                        // Remember the player who he is... (FIFA on PS2 forget about his country otherwise)
                        socketWriter.write(socket, new SocketData("+who", null, personaUtils.getPersonaInfo(socket, socketWrapper, room)));
                    }
                }
            }
        } else {
            socketWriter.write(socket, socketData);
        }
    }

    /**
     * Create a room
     * Since rooms are pre-defined, we deny it
     * Attributes:
     * - NAME=URR.xxx
     * - DESC=D_OS44
     * - LVLRNG=-1
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void room(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (isTw(socket)) {
            twCreate(socket, socketData, socketWrapper);
            return;
        }
        socketData.setIdMessage("roomfull");
        socketWriter.write(socket, socketData);
    }

    /**
     * Fetch room category information
     * Sends the list of available categories to the client.
     * Format: CAT0 = "name,params,prefix,res0,res1,res2,res3,auto_create"
     * <p>
     * CRITICAL: The "prefix" field determines room filtering!
     * Rooms will only display if their name (field N) starts with "prefix."
     * Example: prefix="LVL" → rooms must be named "LVL.xxx"
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void rcat(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        /**
         * "LVL" - Level
         * "CNT" - Country
         * "CLB" - Club
         */
        Map<String, String> content = Stream.of(new String[][]{
                {"CAT0", "Default,Global,LVL,,,,,1"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        // PS2/FIFA08 wants at least 2 categories with one having auto_create=0
        if (PS2_FIFA08.equals(socketWrapper.getPersonaConnectionEntity().getVers())) {
            content.put("CAT1", "Manual,Local,LVL,,,,,0");
        }

        socketData.setOutputData(content);

        socketWriter.write(socket, socketData);
        removePersonaFromRoom(socketWrapper.getPersonaConnectionEntity().getVers(), socketWrapper);
        rom(socket, socketData);
    }

    /**
     * Auto-generate rooms for categories (arom)
     * Called by the client after receiving rcat with auto_create=1
     * Expected request args: CAT<x>=<prefix>, PARM<x>=<params>
     * Expected response: COUNT=<n> followed by n +rom async messages
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void arom(Socket socket, SocketData socketData) {

        // Count how many CAT/PARM pairs we received by checking cat0, cat1, cat2...
        int count = 0;
        while (getValueFromSocket(socketData.getInputMessage(), "cat" + count) != null) {
            count++;
        }

        Map<String, String> content = Stream.of(new String[][]{
                {"COUNT", String.valueOf(count)},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        rom(socket, socketData);
    }

    /**
     * Notify all clients in the room about the new population
     *
     * @param wrapper The socket wrapper of the client that triggered the update
     */
    public void pop(SocketWrapper wrapper) {
        String vers = wrapper.getPersonaConnectionEntity().getVers();
        Room room = getRoomByVers(vers);
        for (SocketWrapper clientWrapper : socketManager.getSocketWrapperByVers(vers)) {
            // Do not send to dedicated server hosts
            if (!clientWrapper.getPersonaConnectionEntity().isHost()) {
                socketWriter.write(clientWrapper.getSocket(), new SocketData("+pop", null, Collections.singletonMap("Z", room.getId().toString() + "/" + room.getPersonaIds().size())));
                sst(clientWrapper.getSocket(), clientWrapper);
            }
        }
    }

    /**
     * Send server status to the client
     * This method is used to send the current number of players in the lobby and in game
     *
     * @param socket  The socket to write the response to
     * @param wrapper The socket wrapper of the client
     */
    public void sst(Socket socket, SocketWrapper wrapper) {
        String name = wrapper.getPersonaConnectionEntity().getVers();
        int playersInLobby = personaConnectionRepository.countPlayersInLobby(name);
        int playersInGame = gameConnectionRepository.countPlayersInGame(name);
        Map<String, String> content = Stream.of(new String[][]{
                {"UIL", String.valueOf(playersInLobby)},
                {"UIG", String.valueOf(playersInGame)},
                {"UIR", "0"},
                {"GIP", "0"},
                {"GCR", "0"},
                {"GCM", "0"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketWriter.write(socket, new SocketData("+sst", null, content));
    }

    /**
     * Send a chat message
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void mesg(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (isTw(socket)) {
            twMesg(socket, socketData, socketWrapper);
            return;
        }
        socketWriter.write(socket, socketData);

        if (socketWrapper.getPersonaEntity() == null) {
            // If the persona is not set, we cannot send a message (seen in logs)
            return;
        }

        String text = getValueFromSocket(socketData.getInputMessage(), "TEXT");
        String attr = getValueFromSocket(socketData.getInputMessage(), "ATTR");
        String priv = getValueFromSocket(socketData.getInputMessage(), "PRIV");

        // ATTR contains encoded flags using the table @ABCDEFGHIJKLMNOPQRSTUVWXYZ0123- where each char is a bit
        // For private messages, add P (bit 16 = 0x10000) to the flags
        String flags;
        if (priv != null) {
            // Private message: ATTR + P (priv flag)
            // The client expects F to contain the original ATTR flags plus P for private routing
            flags = (attr != null ? attr : "") + "P";
        } else {
            // Public/broadcast message: use ATTR as-is
            flags = attr != null ? attr : "Z"; // NHL07 uses "Z" for lobby messages (no ATTR specified)
        }

        Map<String, String> content = Stream.of(new String[][]{
                {"F", flags},
                {"T", text},
                {"N", socketWrapper.getPersonaEntity().getPers()},
                {"U", ""},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketData.setIdMessage("+msg");
        socketData.setOutputData(content);

        // Handle private messages with PRIV field (game sync messages, invitations, etc.)
        if (priv != null) {
            // Find the target player by persona name
            SocketWrapper targetWrapper = socketManager.getSocketWrapperByPersonaNameAndVers(
                    priv,
                    socketWrapper.getPersonaConnectionEntity().getVers()
            );

            if (targetWrapper != null) {
                // Send the message to the target player
                socketWriter.write(targetWrapper.getSocket(), socketData, TAB_CHAR);
            } else {
                log.warn("Target player {} not found for private message from {}",
                        priv,
                        socketWrapper.getPersonaEntity().getPers());
            }
            return;
        }

        GameEntity gameEntity = gameRepository.findCurrentGameOfPersona(socketWrapper.getPersonaConnectionEntity().getId())
                .stream()
                .max(Comparator.comparing(GameEntity::getStartTime))
                .orElse(null);

        // User is in a Game Room (ATTR=G), broadcast the message to the game room only
        if (gameEntity != null) {
            List<GameConnectionEntity> gameConnections = gameEntity.getGameConnections().stream().
                    filter(gameConnection -> gameConnection.getPersonaConnection() != null)
                    .toList();

            for (GameConnectionEntity gameConnection : gameConnections) {
                SocketWrapper gameSocketWrapper = socketManager.getSocketWrapperByPersonaConnectionId(gameConnection.getPersonaConnection().getId());
                if (gameSocketWrapper != null) {
                    socketWriter.write(gameSocketWrapper.getSocket(), socketData, TAB_CHAR);
                }
            }
        } else { // User is in a Lobby Room, broadcast the message to all clients in the lobby except if they are in a game room or in-game
            for (SocketWrapper clientWrapper : socketManager.getSocketWrapperByVers(socketWrapper.getPersonaConnectionEntity().getVers())) {
                if (clientWrapper.getPersonaConnectionEntity() != null) { // In case someone is connected without a persona yet
                    if (gameRepository.findCurrentGameOfPersona(clientWrapper.getPersonaConnectionEntity().getId()).isEmpty()) {
                        socketWriter.write(clientWrapper.getSocket(), socketData, TAB_CHAR);
                    }
                }
            }
        }
    }

    /**
     * Peek in a room
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void peek(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        if (isTw(socket)) {
            twPeek(socket, socketData, socketWrapper);
            return;
        }
        socketWriter.write(socket, socketData);
    }

    /**
     * Add a persona to a room or remove them from the current room
     * If the persona is already in a room, they will be removed from that room first and then added to the new room
     * If the room ID is 0, the persona will be removed from any room they are in
     *
     * @param roomId  The ID of the room to join (0 to leave current room)
     * @param wrapper The socket wrapper of the persona to add to the room
     */
    public void addPersonaToRoom(Long roomId, SocketWrapper wrapper) {
        removePersonaFromRoom(wrapper.getPersonaConnectionEntity().getVers(), wrapper);

        if (roomId > 0L) {
            Long personaId = wrapper.getPersonaEntity().getId();
            rooms.stream()
                    .filter(r -> r.getId().equals(roomId))
                    .findFirst().ifPresent(room -> room.getPersonaIds().add(personaId));
        }
        pop(wrapper);
    }

    public void removePersonaFromRoom(String vers, SocketWrapper wrapper) {
        if (ALL_TW.contains(vers)) {
            twJoin(wrapper, null, null);
            return;
        }
        Room room = getRoomByVers(vers);
        if (room != null) {
            Long personaId = wrapper.getPersonaEntity().getId();
            if (room.getPersonaIds().contains(personaId)) {
                room.getPersonaIds().remove(personaId);
                pop(wrapper);
                // Broadcast to each client in the room about the user leaving
                for (SocketWrapper clientWrapper : socketManager.getSocketWrapperByVers(vers)) {
                    if (clientWrapper.getPersonaEntity() != null) {
                        Room oldRoom = getRoomByVers(vers);
                        Room clientRoom = getRoomByPersonaId(clientWrapper.getPersonaEntity().getId());
                        if (clientRoom != null && clientRoom.getId().equals(oldRoom.getId())) {
                            Map<String, String> content = Collections.singletonMap("I", wrapper.getPersonaEntity().getId().toString());
                            socketWriter.write(clientWrapper.getSocket(), new SocketData("+usr", null, content));
                        }
                    }
                }
            }
        }
    }

    /**
     * Broadcast room users to all online players in a room
     *
     * @param vers   The game version
     * @param roomId The room ID
     */
    public void broadcastRoomUsers(String vers, Long roomId) {
        Room room = getRoomById(roomId);
        if (room == null) {
            return;
        }

        // Get all online players for this game version
        List<SocketWrapper> onlinePlayers = socketManager.getSocketWrapperByVers(vers);

        for (SocketWrapper playerWrapper : onlinePlayers) {
            if (playerWrapper.getPersonaEntity() != null) {
                Room playerRoom = getRoomByPersonaId(playerWrapper.getPersonaEntity().getId());

                // Only broadcast to players in the same room
                if (playerRoom != null && playerRoom.getId().equals(roomId)) {
                    // Send +usr for all players in the room
                    for (SocketWrapper roomPlayerWrapper : onlinePlayers) {
                        if (roomPlayerWrapper.getPersonaEntity() != null) {
                            Room roomPlayerRoom = getRoomByPersonaId(roomPlayerWrapper.getPersonaEntity().getId());
                            if (roomPlayerRoom != null && roomPlayerRoom.getId().equals(roomId)) {
                                socketWriter.write(playerWrapper.getSocket(),
                                        new SocketData("+usr", null,
                                                personaUtils.getPersonaInfo(roomPlayerWrapper.getSocket(), roomPlayerWrapper, room)));
                            }
                        }
                    }
                    // Send +who to the player themselves with their own persona info
                    // Remember the player who he is... (FIFA on PS2 forget about his country otherwise)
                    socketWriter.write(playerWrapper.getSocket(),
                            new SocketData("+who", null,
                                    personaUtils.getPersonaInfo(playerWrapper.getSocket(), playerWrapper, room)));
                }
            }
        }
    }

    public void removeGameFromRoom(GameEntity game, SocketWrapper socketWrapper) {
        Room room = getRoomByVers(game.getVers());
        if (room != null) {
            room.getGameIds().remove(game.getId());
            broadcastGameRemoval(game, socketWrapper);
        }
    }

    public void broadcastGameRemoval(GameEntity game, SocketWrapper socketWrapper) {
        socketManager.getSocketWrapperByVers(game.getVers())
                .forEach(wrapper -> {
                    Socket gameSocket = wrapper.getSocket();
                    socketWriter.write(gameSocket, new SocketData("+agmugam", null,
                            Collections.singletonMap("IDENT", String.valueOf(game.getId()))));
                    // If the socket is not the one that initiated the game removal, send the game update
                    if (!wrapper.getPersonaConnectionEntity().getId().equals(socketWrapper.getPersonaConnectionEntity().getId())) {
                        // Only send to players that are not in a game (or if the game ID is identical to the one being removed)
                        List<GameEntity> currentGames = gameRepository.findCurrentGameOfPersona(wrapper.getPersonaConnectionEntity().getId());
                        if (currentGames.isEmpty() || currentGames.stream().anyMatch(g -> g.getId().equals(game.getId()))) {
                            socketWriter.write(gameSocket, new SocketData("+mgmugam", null,
                                    Collections.singletonMap("IDENT", String.valueOf(game.getId()))));
                        }
                    }
                });
    }

    public Room getRoomById(Long roomId) {
        return rooms.stream()
                .filter(room -> Objects.equals(room.getId(), roomId))
                .findFirst()
                .orElse(null);
    }

    public Room getRoomByVers(String vers) {
        return rooms.stream()
                .filter(room -> room.getVers().equals(vers))
                .findFirst()
                .orElse(null);
    }

    public Room getRoomByPersonaId(Long personaId) {
        return rooms.stream()
                .filter(room -> room.getPersonaIds().contains(personaId))
                .findFirst()
                .orElse(null);
    }

    public void generateRooms() {
        for (GameServerConfig.GameServer server : gameServerConfig.getServers()) {
            if (server.getRooms() != null && !server.getRooms().getNames().isEmpty()) {
                // Games that list rooms by name get every configured room
                for (String name : server.getRooms().getNames()) {
                    addRoom(server.getName(), name, "", server.getRooms().getLimit(), false);
                }
            } else {
                addRoom(server.getName(), server.getName(), "", 50, false);
            }
        }
    }

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

    public Room getRoomByName(String vers, String name) {
        return rooms.stream()
                .filter(room -> room.getVers().equals(vers) && room.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tiger Woods PGA Tour 2004: rooms are listed and joined by name, players can create rooms, and each client is only
    // sent the room, user and message updates it subscribed to with sele. +usr records are indexed by position in the
    // room's roster, so the roster is sent again whenever it changes
    // ---------------------------------------------------------------------------------------------------------------

    private static final int ATTR_IGNORE = 0x200000;
    private static final int ATTR_CHALLENGE = 0x40000000;
    private static final int MAX_MESSAGE_LENGTH = 1023;

    private boolean isTw(Socket socket) {
        return ALL_TW.contains(gameServerService.getNameByPort(socket.getLocalPort()));
    }

    private void twFail(Socket socket, String verb, String code) {
        socketWriter.writeText(socket, verb + code, "");
    }

    private GameServerConfig.RoomConfig roomConfig(String vers) {
        return gameServerService.getServerByName(vers).map(GameServerConfig.GameServer::getRooms).orElse(new GameServerConfig.RoomConfig());
    }

    private static String vers(SocketWrapper wrapper) {
        return wrapper.getPersonaConnectionEntity().getVers();
    }

    private void twCreate(Socket socket, SocketData socketData, SocketWrapper wrapper) {
        if (wrapper.getPersonaEntity() == null) {
            twFail(socket, "room", "nusr");
            return;
        }
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String vers = vers(wrapper);
        GameServerConfig.RoomConfig config = roomConfig(vers);
        String name = fields.getOrDefault("NAME", "");
        synchronized (rooms) {
            if (config.getCreatePattern().isEmpty() || name.length() > config.getNameLength() || !name.matches(config.getCreatePattern())) {
                twFail(socket, "room", "name");
                return;
            }
            if (getRoomByName(vers, name) != null) {
                twFail(socket, "room", "dupl");
                return;
            }
            if (rooms.stream().filter(room -> room.getVers().equals(vers)).count() >= config.getMax()) {
                twFail(socket, "room", "full");
                return;
            }
            int limit;
            try {
                limit = Integer.parseInt(fields.getOrDefault("MAX", String.valueOf(config.getLimit())));
            } catch (NumberFormatException e) {
                limit = config.getLimit();
            }
            Room room = addRoom(vers, name, fields.getOrDefault("PASS", ""), Math.clamp(limit, 1, config.getLimit()), true);
            twJoin(wrapper, room, "room");
        }
    }

    private void twMove(Socket socket, SocketData socketData, SocketWrapper wrapper) {
        if (wrapper.getPersonaEntity() == null) {
            twFail(socket, "move", "nusr");
            return;
        }
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String name = fields.getOrDefault("NAME", "");
        Room room = null;
        if (!name.isEmpty()) {
            room = getRoomByName(vers(wrapper), name);
            if (room == null) {
                twFail(socket, "move", "name");
                return;
            }
            if (!room.getPassword().isEmpty() && !room.getPassword().equals(fields.getOrDefault("PASS", ""))) {
                twFail(socket, "move", "pass");
                return;
            }
            if (!room.getPersonaIds().contains(wrapper.getPersonaEntity().getId()) && room.getPersonaIds().size() >= room.getLimit()) {
                twFail(socket, "move", "full");
                return;
            }
        }
        twJoin(wrapper, room, "move");
    }

    /**
     * Move a player to a room, or out of their room (room null), then send the updates
     * A pending challenge is dropped when the player changes room, and an empty created room is removed
     *
     * @param wrapper The player
     * @param room    The room to join, or null to leave
     * @param verb    The reply to send the player (room or move), or null when they are leaving the lobby
     */
    private void twJoin(SocketWrapper wrapper, Room room, String verb) {
        if (wrapper.getPersonaEntity() == null) {
            return;
        }
        Long personaId = wrapper.getPersonaEntity().getId();
        Room previous = getRoomByPersonaId(personaId);
        if (previous != room) {
            twChallengeService.cancel(wrapper, true);
            if (previous != null) {
                previous.getPersonaIds().remove(personaId);
                if (previous.isCreated() && previous.getPersonaIds().isEmpty() && previous.getGameIds().isEmpty()) {
                    rooms.remove(previous);
                }
            }
            if (room != null) {
                room.getPersonaIds().add(personaId);
            }
        }
        String vers = vers(wrapper);
        if (verb != null) {
            socketWriter.writeTagFields(wrapper.getSocket(), verb, Map.of("NAME", room == null ? "" : room.getName(),
                    "COUNT", String.valueOf(room == null ? 0 : twOccupants(vers, room).size())));
        }
        log.info("TW04 lobby: {} {} {}", wrapper.getPersonaEntity().getPers(), room == null ? "left" : "joined",
                room == null ? previous == null ? "" : previous.getName() : room.getName());
        twBroadcastRooms(vers);
        if (previous != null) {
            twBroadcastUsers(vers, previous);
        }
        if (room != null && room != previous) {
            twBroadcastUsers(vers, room);
        }
        if (room == null && verb != null && wrapper.isUserUpdates()) {
            twPushUsers(wrapper.getSocket(), wrapper, List.of());
        }
    }

    private void twPeek(Socket socket, SocketData socketData, SocketWrapper wrapper) {
        if (wrapper.getPersonaEntity() == null) {
            twFail(socket, "peek", "nusr");
            return;
        }
        Room room = getRoomByName(vers(wrapper), LobbyTagField.decode(socketData.getInputMessage()).getOrDefault("NAME", ""));
        List<SocketWrapper> users = room == null ? List.of() : twOccupants(vers(wrapper), room);
        socketWriter.writeTagFields(socket, "peek", Map.of("COUNT", String.valueOf(users.size())));
        if (wrapper.isUserUpdates()) {
            twPushUsers(socket, wrapper, users);
        }
    }

    /**
     * Send a client the room list, as requested with sele ROOMS=1
     *
     * @param socket The socket to write the response to
     * @param vers   The game version
     */
    public void twPushRooms(Socket socket, String vers) {
        for (Room room : rooms) {
            if (room.getVers().equals(vers)) {
                socketWriter.writeTagFields(socket, "+rom", Map.of("I", String.valueOf(room.getId()), "N", room.getName(), "H", "",
                        "T", String.valueOf(twOccupants(vers, room).size()), "A", "0", "F", "0"));
            }
        }
    }

    /**
     * Send a client its room's users, as requested with sele USERS=1
     *
     * @param socket  The socket to write the response to
     * @param wrapper The socket wrapper of the client
     */
    public void twPushRoomUsers(Socket socket, SocketWrapper wrapper) {
        Room room = wrapper.getPersonaEntity() == null ? null : getRoomByPersonaId(wrapper.getPersonaEntity().getId());
        if (room != null) {
            twPushUsers(socket, wrapper, twOccupants(vers(wrapper), room));
        }
    }

    private void twBroadcastRooms(String vers) {
        for (SocketWrapper client : socketManager.getSocketWrapperByVers(vers)) {
            if (client.isRoomUpdates()) {
                twPushRooms(client.getSocket(), vers);
            }
        }
    }

    /**
     * Send the room's roster to everyone in the room who subscribed to user updates (e.g. after a result changes a rank)
     *
     * @param vers The game version
     * @param room The room
     */
    public void twBroadcastUsers(String vers, Room room) {
        List<SocketWrapper> users = twOccupants(vers, room);
        for (SocketWrapper client : socketManager.getSocketWrapperByVers(vers)) {
            if (client.isUserUpdates() && client.getPersonaEntity() != null && room.getPersonaIds().contains(client.getPersonaEntity().getId())) {
                twPushUsers(client.getSocket(), client, users);
            }
        }
    }

    private List<SocketWrapper> twOccupants(String vers, Room room) {
        return socketManager.getSocketWrapperByVers(vers).stream()
                .filter(client -> client.getPersonaEntity() != null && room.getPersonaIds().contains(client.getPersonaEntity().getId()))
                .sorted(Comparator.comparing(client -> client.getPersonaEntity().getPers()))
                .toList();
    }

    private void twPushUsers(Socket socket, SocketWrapper recipient, List<SocketWrapper> users) {
        Map<Long, TwStatsService.Standing> standings = twStatsService.standings(users.stream().map(SocketWrapper::getPersonaEntity).toList(),
                vers(recipient));
        for (int i = 0; i < users.size(); i++) {
            PersonaEntity persona = users.get(i).getPersonaEntity();
            TwStatsService.Standing standing = standings.get(persona.getId());
            socketWriter.writeTagFields(socket, "+usr", Map.of("I", String.valueOf(i), "N", persona.getPers(), "P", "good",
                    "A", twPeerAddress(users.get(i), recipient), "R", String.valueOf(standing.rank()), "S", standing.packed()));
        }
        // An index without N removes the previous occupant from the client's list
        for (int i = users.size(); i < recipient.getRosterSize(); i++) {
            socketWriter.writeTagFields(socket, "+usr", Map.of("I", String.valueOf(i)));
        }
        recipient.setRosterSize(users.size());
    }

    /**
     * The address a player gives another to reach them
     * PCSX2 Sockets gives every guest the same virtual address, so prefer the TCP source, except for real console LAN
     * addresses behind a shared NAT
     *
     * @param peer   The player to reach
     * @param viewer The player who connects to them
     * @return The address
     */
    public String twPeerAddress(SocketWrapper peer, SocketWrapper viewer) {
        String observed = peer.getSocket().getInetAddress().getHostAddress();
        String viewerAddress = viewer.getSocket().getInetAddress().getHostAddress();
        String reported = peer.getReportedAddress();
        if (observed.equals(viewerAddress) && !reported.startsWith("192.0.2.") && isLanIpv4(reported)) {
            return reported;
        }
        return observed.startsWith("::ffff:") ? observed.substring(7) : observed;
    }

    private static boolean isLanIpv4(String address) {
        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (!part.matches("[0-9]{1,3}") || Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return !address.equals("0.0.0.0") && !address.startsWith("127.");
    }

    /**
     * A session set up in a room has ended: an empty created room can now be removed
     *
     * @param vers   The game version
     * @param roomId The room ID
     * @param gameId The game ID
     */
    public void removeSessionFromRoom(String vers, Long roomId, Long gameId) {
        Room room = getRoomById(roomId);
        if (room != null) {
            room.getGameIds().remove(gameId);
            if (room.isCreated() && room.getPersonaIds().isEmpty() && room.getGameIds().isEmpty()) {
                rooms.remove(room);
                twBroadcastRooms(vers);
            }
        }
    }

    private boolean blocks(SocketWrapper owner, SocketWrapper target) {
        return buddyRepository.existsByFromPersonaIdAndToPersonaIdAndList(owner.getPersonaEntity().getId(), target.getPersonaEntity().getId(), "I");
    }

    private void twMesg(Socket socket, SocketData socketData, SocketWrapper sender) {
        if (sender.getPersonaEntity() == null) {
            twFail(socket, "mesg", "nusr");
            return;
        }
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String target = fields.getOrDefault("PRIV", "");
        String text = fields.getOrDefault("TEXT", "");
        if (text.length() > MAX_MESSAGE_LENGTH) {
            twFail(socket, "mesg", "full");
            return;
        }
        String vers = vers(sender);
        int attr = LobbyTagField.decodeFlags(fields.getOrDefault("ATTR", "")) & ~ATTR_IGNORE;
        SocketWrapper targetWrapper = target.isEmpty() ? null : socketManager.getSocketWrapperByPersonaNameAndVers(target, vers);
        Room senderRoom = getRoomByPersonaId(sender.getPersonaEntity().getId());
        if ((attr & ATTR_CHALLENGE) != 0) {
            Room targetRoom = targetWrapper == null || targetWrapper.getPersonaEntity() == null ? null
                    : getRoomByPersonaId(targetWrapper.getPersonaEntity().getId());
            twChallengeService.message(socket, sender, senderRoom, targetWrapper, targetRoom,
                    targetWrapper != null && targetWrapper.getPersonaEntity() != null && blocks(targetWrapper, sender), text, attr);
            return;
        }
        socketWriter.writeTagFields(socket, "mesg", Map.of());
        if (!target.isEmpty() && (targetWrapper == null || targetWrapper.getPersonaEntity() == null || blocks(targetWrapper, sender))) {
            return;
        }
        chatHistoryService.add(sender.getPersonaEntity().getId(), sender.getPersonaEntity().getPers(),
                targetWrapper == null ? 0 : targetWrapper.getPersonaEntity().getId(),
                senderRoom == null ? "" : senderRoom.getName(), text);
        String flags = LobbyTagField.encodeFlags(target.isEmpty() ? attr : attr | TwChallengeService.ATTR_PRIVATE);
        for (SocketWrapper other : socketManager.getSocketWrapperByVers(vers)) {
            if (other.getPersonaEntity() == null || !other.isMessageUpdates()) {
                continue;
            }
            boolean receives = target.isEmpty()
                    ? senderRoom != null && senderRoom.getPersonaIds().contains(other.getPersonaEntity().getId())
                    : other == targetWrapper || other == sender;
            if (receives && !blocks(other, sender)) {
                socketWriter.writeTagFields(other.getSocket(), "+msg", Map.of("N", sender.getPersonaEntity().getPers(), "T", text, "F", flags));
            }
        }
    }
}
