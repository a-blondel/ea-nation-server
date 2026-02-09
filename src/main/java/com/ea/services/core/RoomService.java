package com.ea.services.core;

import com.ea.config.GameServerConfig;
import com.ea.dto.Room;
import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.entities.core.GameConnectionEntity;
import com.ea.entities.core.GameEntity;
import com.ea.repositories.core.GameConnectionRepository;
import com.ea.repositories.core.GameRepository;
import com.ea.repositories.core.PersonaConnectionRepository;
import com.ea.services.server.GameServerService;
import com.ea.services.server.SocketManager;
import com.ea.steps.SocketWriter;
import com.ea.utils.GameUtils;
import com.ea.utils.PersonaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.services.server.GameServerService.PS2_FIFA_08;
import static com.ea.utils.SocketUtils.TAB_CHAR;
import static com.ea.utils.SocketUtils.getValueFromSocket;

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
    private final List<Room> rooms = new ArrayList<>();

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
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void room(Socket socket, SocketData socketData) {
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
        if (PS2_FIFA_08.equals(socketWrapper.getPersonaConnectionEntity().getVers())) {
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
        List<String> vers = gameServerService.getRelatedVers(wrapper.getPersonaConnectionEntity().getVers());
        int playersInLobby = personaConnectionRepository.countPlayersInLobby(vers);
        int playersInGame = gameConnectionRepository.countPlayersInGame(vers);
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
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void peek(Socket socket, SocketData socketData) {
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
                .filter(room ->
                        room.getVers().equals(vers) || gameServerConfig.getServers().stream()
                                .filter(server -> server.getVers().equals(room.getVers()))
                                .filter(server -> server.getDedicated() != null && server.getDedicated().getVers() != null)
                                .anyMatch(server -> server.getDedicated().getVers().equals(vers))
                )
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
            Room room = new Room();
            room.setId(rooms.size() + 1L);
            room.setName(server.getVers());
            room.setVers(server.getVers());
            rooms.add(room);
        }
    }
}
