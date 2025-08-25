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
                    {"N", room.getName()}, // Room name
//                { "H", socketManager.getSocketWrapper(socket.getRemoteSocketAddress().toString()).getPers() }, // Room Host
                    {"D", room.getName()}, // Room description
//                { "F", "CK" }, // Attribute flags
                    {"T", String.valueOf(room.getPersonaIds().size())}, // Current room population
                    {"L", "50"}, // Max users allowed in room
//                { "P", "0" }, // Room ping
//                { "A", props.getTcpHost() }, // Room address
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
        // String name = getValueFromSocket(socketData.getInputMessage(), "NAME");

        long roomId = Long.parseLong(ident);
        addPersonaToRoom(roomId, socketWrapper);

        Room room = getRoomById(roomId);

        if (!ident.equals("0")) {
            Map<String, String> content = Stream.of(new String[][]{
                    {"IDENT", ident},
                    {"NAME", room.getName()},
                    {"COUNT", String.valueOf(room.getPersonaIds().size())},
                    {"FLAGS", room.getFlags()},
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
                        // Also notify each client in the room about the new user
                        if (!clientWrapper.getSocket().equals(socket)) {
                            socketWriter.write(clientWrapper.getSocket(), new SocketData("+usr", null, personaUtils.getPersonaInfo(socket, socketWrapper, room)));
                        }
                    }
                }
            }
        } else {
            socketWriter.write(socket, socketData);
        }
    }

    /**
     * Fetch room category information
     * It isn't clear how to use this method, but we can use it to apply custom logic
     * If we receive this packet, then :
     * - The user isn't in a room yet, so we can remove them from any room they might be in
     * - The user is requesting room categories, so we can send them the list of rooms available
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void rcat(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        socketWriter.write(socket, socketData);
        removePersonaFromRoom(socketWrapper.getPersonaConnectionEntity().getVers(), socketWrapper);
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

        Map<String, String> content = Stream.of(new String[][]{
                {"F", attr != null ? attr : "Z"}, // NHL07 uses "Z" for lobby messages (no ATTR specified)
                {"T", text},
                {"N", socketWrapper.getPersonaEntity().getPers()},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketData.setIdMessage("+msg");
        socketData.setOutputData(content);

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
