package com.ea.services.core;

import com.ea.dto.Room;
import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.entities.core.AccountEntity;
import com.ea.entities.core.GameConnectionEntity;
import com.ea.entities.core.GameEntity;
import com.ea.entities.core.PersonaConnectionEntity;
import com.ea.mappers.SocketMapper;
import com.ea.repositories.core.*;
import com.ea.services.server.GameServerService;
import com.ea.services.server.SocketManager;
import com.ea.services.stats.MohhStatsService;
import com.ea.steps.SocketWriter;
import com.ea.utils.GameUtils;
import com.ea.utils.SocketUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.services.server.GameServerService.*;
import static com.ea.utils.SocketUtils.getValueFromSocket;

@Slf4j
@RequiredArgsConstructor
@Service
public class GameService {

    private final GameRepository gameRepository;
    private final GameConnectionRepository gameConnectionRepository;
    private final PersonaConnectionRepository personaConnectionRepository;
    private final AccountRepository accountRepository;
    private final BlacklistRepository blacklistRepository;
    private final SocketMapper socketMapper;
    private final PersonaService personaService;
    private final GameServerService gameServerService;
    private final RoomService roomService;
    private final MohhStatsService mohhStatsService;
    private final SocketWriter socketWriter;
    private final SocketManager socketManager;
    private final GameUtils gameUtils;


    /**
     * Join the best matching game based on provided criteria
     * Most arguments are game-specific, but the MODE defines how the search will be performed
     * Modes are :
     * - LOBBYAPI_GQWK_MODE_FAIL   - Fail with EC_NOT_FOUND error code
     * - LOBBYAPI_GQWK_MODE_CREATE - Create a game
     * - LOBBYAPI_GQWK_MODE_WAIT   - Wait until a game or user becomes
     * - LOBBYAPI_GQWK_MODE_CANCEL - Cancel a MODE_WAIT request
     * <p>
     * NHL uses MODE=2, so it will wait until a game is available,
     * and it will send MODE=3 when the user wants to cancel the request
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data containing game search criteria
     * @param socketWrapper The socket wrapper of current connection
     */
    public void gqwk(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        socketWriter.write(socket, socketData);
        String mode = getValueFromSocket(socketData.getInputMessage(), "MODE");

        if ("2".equals(mode)) { // Wait for a game to become available
            // Cancel any existing search thread for this socket
            synchronized (socketWrapper) {
                Thread existingThread = socketWrapper.getGameSearchThread();
                if (existingThread != null && existingThread.isAlive()) {
                    existingThread.interrupt();
                }
            }

            // Create a new thread to handle the game search with timer
            Thread searchThread = new Thread(() -> {
                String vers = socketWrapper.getPersonaConnectionEntity().getVers();
                List<String> relatedVers = gameServerService.getRelatedVers(vers);

                while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
                    log.debug("Searching for game for socket: {}", socket.getRemoteSocketAddress());
                    List<GameEntity> gameEntities = gameRepository.findByVersInAndEndTimeIsNull(relatedVers);

                    // Game must not be full, not use pw, not be started, and have at least one active game connection
                    gameEntities = gameEntities.stream()
                            .filter(gameEntity -> !gameEntity.isStarted() && gameEntity.getGameConnections().stream()
                                    .anyMatch(connection -> connection.getEndTime() == null))
                            .filter(gameEntity -> gameEntity.getGameConnections().stream()
                                    .filter(connection -> connection.getEndTime() == null).count() < gameEntity.getMaxsize())
                            .filter(gameEntity -> StringUtils.isEmpty(gameEntity.getPass()))
                            .toList();

                    // Theoretically we should filter the game entities based on the criteria provided in the socket data
                    // Given the low player count, no need to filter by params

                    if (!gameEntities.isEmpty()) {
                        if (!socket.isClosed()) {
                            GameEntity gameEntity = gameEntities.get(0); // Get the first game found
                            // It seems like the "Play Now" features is auto-start, it doesn't join the lobby with only +mgm
                            joinGame(socket, socketData, socketWrapper, gameEntity);
                            gsta(socket, socketData, socketWrapper);
                        }
                        break; // End the thread when a game is found
                    }

                    try {
                        Thread.sleep(15000); // Wait 15 seconds before next search
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // Clean up the thread reference when done
                synchronized (socketWrapper) {
                    if (socketWrapper.getGameSearchThread() == Thread.currentThread()) {
                        log.debug("Game search thread for socket {} finished", socket.getRemoteSocketAddress());
                        socketWrapper.setGameSearchThread(null);
                    }
                }
            });

            // Store the search thread for this socket wrapper
            synchronized (socketWrapper) {
                socketWrapper.setGameSearchThread(searchThread);
            }
            searchThread.start();

        } else if ("3".equals(mode)) {
            // Cancel a MODE_WAIT request
            synchronized (socketWrapper) {
                Thread searchThread = socketWrapper.getGameSearchThread();
                if (searchThread != null && searchThread.isAlive()) {
                    log.debug("Cancelling game search for socket: {}", socket.getRemoteSocketAddress());
                    searchThread.interrupt();
                    socketWrapper.setGameSearchThread(null);
                }
            }
        }
    }

    /**
     * Start the game
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void gsta(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        socketWriter.write(socket, socketData);

        // If game is P2P then we send +ses to all players in the lobby
        if (gameServerService.isP2P(socketWrapper.getPersonaConnectionEntity().getVers())) {
            GameConnectionEntity gameConnectionEntity = gameConnectionRepository.findByPersonaConnectionIdAndEndTimeIsNull(
                    socketWrapper.getPersonaConnectionEntity().getId()).orElse(null);

            if (gameConnectionEntity != null) {
                GameEntity gameEntity = gameConnectionEntity.getGame();
                if (gameEntity != null) {
                    for (GameConnectionEntity connection : gameConnectionRepository.findByGameIdAndEndTimeIsNull(gameEntity.getId())) {
                        SocketWrapper connectionSocketWrapper = socketManager.getSocketWrapperByPersonaConnectionId(connection.getPersonaConnection().getId());
                        if (connectionSocketWrapper != null) {
                            ses(connectionSocketWrapper.getSocket(), gameEntity);
                        }
                        connection.setStartTime(LocalDateTime.now());
                        gameConnectionRepository.save(connection);
                    }
                    // Don't update start time here, the WHEN attribute of the 'rank' packet uses the first declared start time (and it's used as an identifier for the game)
                    gameEntity.setStarted(true);
                    gameRepository.save(gameEntity);
                }
            }
        }
    }

    /**
     * Set game parameters
     * This is used to update the game parameters, such as name, params, sysflags, etc.
     * It is also used to on map rotation on dedicated servers, so it will end the current game and create a new one with the new parameters.
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void gset(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        socketWriter.write(socket, socketData);

        PersonaConnectionEntity personaConnectionEntity = socketWrapper.getPersonaConnectionEntity();
        if (gameServerService.isP2P(personaConnectionEntity.getVers())) {
            // On NHL, the value is 0 or 1 to know if the client is ready or not
            String userflags = getValueFromSocket(socketData.getInputMessage(), "USERFLAGS");

            if (userflags != null) {
                synchronized (this) {
                    socketWrapper.setUserflags(userflags);
                }
                // Broadcast the userflags to all players in the game
                Optional<GameConnectionEntity> gameConnectionOpt = gameConnectionRepository.findByPersonaConnectionIdAndEndTimeIsNull(personaConnectionEntity.getId());

                if (gameConnectionOpt.isPresent()) {
                    GameConnectionEntity gameConnectionEntity = gameConnectionOpt.get();
                    GameEntity gameEntity = gameConnectionEntity.getGame();
                    List<GameConnectionEntity> gameConnections = gameConnectionRepository.findByGameIdAndEndTimeIsNull(gameEntity.getId());
                    for (GameConnectionEntity firstLevelConnection : gameConnections) { // For each player in the game, send +agm and +mgm
                        SocketWrapper firstLevelSocketWrapper = socketManager.getSocketWrapperByPersonaConnectionId(firstLevelConnection.getPersonaConnection().getId());
                        if (firstLevelSocketWrapper != null) {
                            agm(firstLevelSocketWrapper.getSocket(), gameEntity);
                            mgm(firstLevelSocketWrapper.getSocket(), gameEntity);
                        }
                    }
                }
            }
        }

        // For MoHH dedicated server, gset means a map rotation, but instead of just updating the game parameters,
        // we end the current game and create a new one with the new parameters.
        if (MOH07_OR_UHS.contains(personaConnectionEntity.getVers())) {
            handleMapRotation(socketData, socketWrapper);
        }
    }

    /**
     * Handle map rotation for MoHH dedicated servers
     * This will end the current game and create a new one with the new parameters.
     *
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    private void handleMapRotation(SocketData socketData, SocketWrapper socketWrapper) {
        //String name = getValueFromSocket(socketData.getInputMessage(), "NAME");
        String params = getValueFromSocket(socketData.getInputMessage(), "PARAMS");
        String sysflags = getValueFromSocket(socketData.getInputMessage(), "SYSFLAGS");

        GameEntity gameEntity = gameConnectionRepository.findByPersonaConnectionIdAndEndTimeIsNull(
                        socketWrapper.getPersonaConnectionEntity().getId())
                .filter(GameConnectionEntity::isHost)
                .map(GameConnectionEntity::getGame).orElse(null);

        LocalDateTime now = LocalDateTime.now();
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                if (gameEntity != null) {
                    List<GameConnectionEntity> gameConnections = gameConnectionRepository.findByGameIdAndEndTimeIsNull(gameEntity.getId());
                    for (GameConnectionEntity gameConnectionEntity : gameConnections) {
                        gameConnectionEntity.setEndTime(now);
                        gameConnectionRepository.save(gameConnectionEntity);
                    }
                    gameEntity.setEndTime(now);
                    gameRepository.save(gameEntity);

                    GameEntity newGameEntity = new GameEntity();
                    newGameEntity.setOriginalId(Optional.ofNullable(gameEntity.getOriginalId()).orElse(gameEntity.getId()));
                    newGameEntity.setVers(gameEntity.getVers());
                    newGameEntity.setSlus(gameEntity.getSlus());
                    newGameEntity.setName(gameEntity.getName());
                    newGameEntity.setParams(params);
                    newGameEntity.setSysflags(sysflags);
                    newGameEntity.setStartTime(now);
                    newGameEntity.setPass(gameEntity.getPass());
                    newGameEntity.setMinsize(gameEntity.getMinsize());
                    newGameEntity.setMaxsize(gameEntity.getMaxsize());
                    newGameEntity.setStarted(true);
                    newGameEntity.setRoomId(gameEntity.getRoomId());
                    gameRepository.save(newGameEntity);

                    for (GameConnectionEntity gameConnectionEntity : gameConnections) {
                        GameConnectionEntity newGameConnectionEntity = new GameConnectionEntity();
                        newGameConnectionEntity.setGame(newGameEntity);
                        newGameConnectionEntity.setPersonaConnection(gameConnectionEntity.getPersonaConnection());
                        newGameConnectionEntity.setHost(gameConnectionEntity.isHost());
                        newGameConnectionEntity.setStartTime(now);
                        gameConnectionRepository.save(newGameConnectionEntity);
                    }
                    updateHostInfo(newGameEntity);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Game search
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void gsea(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        List<String> relatedVers = gameServerService.getRelatedVers(vers);
        List<GameEntity> gameEntities = gameRepository.findByVersInAndEndTimeIsNull(relatedVers);

        Map<String, String> paramsMap = SocketUtils.getMapFromSocket(socketData.getInputMessage());
        List<GameEntity> filteredGameEntities = filterGameEntities(gameEntities, paramsMap, vers);

        Map<String, String> content = Collections.singletonMap("COUNT", String.valueOf(filteredGameEntities.size()));
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        gam(socket, filteredGameEntities);
    }

    /**
     * Filter game entities based on the criteria provided in the paramsMap
     * Each game version may have different criteria for filtering games
     *
     * @param gameEntities The list of game entities to filter
     * @param paramsMap    The parameters map from the socket data
     * @param vers         The version of the game
     * @return A list of filtered game entities
     */
    private List<GameEntity> filterGameEntities(List<GameEntity> gameEntities, Map<String, String> paramsMap, String vers) {
        int count = Integer.parseInt(paramsMap.get("COUNT"));
        return gameEntities.stream()
                .filter(gameEntity -> matchesCriteria(gameEntity, paramsMap, vers))
                .limit(count)
                .toList();
    }

    /**
     * Check if the game entity matches the criteria based on the version
     * Each game version may have different criteria for filtering games
     *
     * @param gameEntity The game entity to check
     * @param paramsMap  The parameters map from the socket data
     * @param vers       The version of the game
     * @return true if the game entity matches the criteria, false otherwise
     */
    private boolean matchesCriteria(GameEntity gameEntity, Map<String, String> paramsMap, String vers) {
        if (vers.equals(PSP_MOH_07_UHS) || MOH07_OR_MOH08.contains(vers)) {
            return mohhStatsService.matchesCriteria(gameEntity, paramsMap, vers);
        } else {
            return true;
        }
    }

    /**
     * A game row
     *
     * @param socket       The socket to write the response to
     * @param gameEntities List of game entities to send
     */
    public void gam(Socket socket, List<GameEntity> gameEntities) {
        List<Map<String, String>> games = new ArrayList<>();

        for (GameEntity gameEntity : gameEntities) {
            String sysflags = gameEntity.getSysflags();
            if (StringUtils.isNotEmpty(gameEntity.getPass())) {
                sysflags = String.valueOf(Integer.parseInt(sysflags) | (1 << 16)); // Add password flag (16th bit)
            }
            games.add(Stream.of(new String[][]{
                    {"IDENT", String.valueOf(gameEntity.getId())},
                    {"NAME", gameEntity.getName()},
                    {"PARAMS", gameEntity.getParams()},
                    {"SYSFLAGS", sysflags},
                    {"COUNT", String.valueOf(gameEntity.getGameConnections().stream().filter(connection -> null == connection.getEndTime()).count())},
                    {"MAXSIZE", String.valueOf(gameEntity.getMaxsize())},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
        }

        for (Map<String, String> game : games) {
            SocketData socketData = new SocketData("+gam", null, game);
            socketWriter.write(socket, socketData);
        }
    }

    /**
     * Join a game
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void gjoi(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        AccountEntity accountEntity = accountRepository.findById(socketWrapper.getAccountEntity().getId()).orElse(null);
        if (blacklistRepository.existsByIp(socket.getInetAddress().getHostAddress())
                || Objects.requireNonNull(accountEntity).isBanned()) {
            socketData.setIdMessage("gjoiblak"); // IP is blacklisted or account is banned (can also use gjoiband)
            socketWriter.write(socket, socketData);
            return;
        }

        String ident = getValueFromSocket(socketData.getInputMessage(), "IDENT");
        Optional<GameEntity> gameEntityOpt;
        if (ident != null) {
            gameEntityOpt = gameRepository.findById(Long.valueOf(ident));
        } else {
            // Some games don't provide an identifier, so we need to find the game by name and version
            String name = getValueFromSocket(socketData.getInputMessage(), "NAME");
            gameEntityOpt = gameRepository.findByNameAndVersInAndEndTimeIsNull(name, List.of(socketWrapper.getPersonaConnectionEntity().getVers()));
        }

        if (gameEntityOpt.isPresent()) {
            joinGame(socket, socketData, socketWrapper, gameEntityOpt.get());
        } else {
            socketWriter.write(socket, new SocketData("gjoiugam", null, null)); // Game unknown
        }
    }

    public void joinGame(Socket socket, SocketData socketData, SocketWrapper socketWrapper, GameEntity gameEntity) {
        String pass = getValueFromSocket(socketData.getInputMessage(), "PASS");
        if (StringUtils.isNotEmpty(pass) && !pass.equals(gameEntity.getPass())) {
            socketWriter.write(socket, new SocketData("gjoipass", null, null)); // Wrong password
            return;
        }
        if (gameEntity.getEndTime() == null) {
            // Check if game allows joining mid-game
            if (gameEntity.isStarted() && MIDGAME_FORBIDDEN.contains(gameEntity.getVers())) {
                socketWriter.write(socket, new SocketData("gjoiasta", null, null)); // Game already started
                return;
            }

            // Check if the game is full
            long currentCount = gameEntity.getGameConnections().stream().filter(connection -> null == connection.getEndTime()).count();
            if (currentCount + 1 > gameEntity.getMaxsize()) {
                socketWriter.write(socket, new SocketData("gjoifull", null, null)); // Game is full
                return;
            }

            startGameConnection(socketWrapper, gameEntity, false);
            socketWriter.write(socket, socketData);

            // Check if game is P2P
            if (gameServerService.isP2P(gameEntity.getVers())) {
                // Reset userflags
                synchronized (this) {
                    socketWrapper.setUserflags("0");
                }

                // Send who
                personaService.who(socket, socketWrapper);

                // Broadcast the game join to all connected clients in the room
                // Inform about all players in the game (+usr for each player, to each player)
                List<GameConnectionEntity> gameConnections = gameConnectionRepository.findByGameIdAndEndTimeIsNull(gameEntity.getId());
                for (SocketWrapper clientWrapper : socketManager.getSocketWrapperByVers(gameEntity.getVers())) {
                    for (GameConnectionEntity gameConnection : gameConnections) { // For each player in the game, send +usr to each player
                        SocketWrapper inGameWrapper = socketManager.getSocketWrapperByPersonaConnectionId(gameConnection.getPersonaConnection().getId());
                        if (inGameWrapper != null && gameConnections.stream().anyMatch(gameConnectionEntity -> gameConnectionEntity.getPersonaConnection().getId().equals(clientWrapper.getPersonaConnectionEntity().getId()))) {
                            personaService.usr(clientWrapper.getSocket(), inGameWrapper); // Update user info for each player
                        }
                    }
                    agm(clientWrapper.getSocket(), gameEntity);
                    mgm(clientWrapper.getSocket(), gameEntity);
                }
            } else {
                updateHostInfo(gameEntity);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ses(socket, gameEntity);
            }
        } else {
            socketWriter.write(socket, new SocketData("gjoiugam", null, null)); // Game closed
        }
    }

    /**
     * Create a game on a persistent game spawn service for a user (dedicated servers)
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void gpsc(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        AccountEntity accountEntity = accountRepository.findById(socketWrapper.getAccountEntity().getId()).orElse(null);
        if (blacklistRepository.existsByIp(socket.getInetAddress().getHostAddress())
                || Objects.requireNonNull(accountEntity).isBanned()) {
            socketData.setIdMessage("gpscblak"); // IP is blacklisted or account is banned (can also use gpscband)
            socketWriter.write(socket, socketData);
            return;
        }

        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        String slus = socketWrapper.getPersonaConnectionEntity().getSlus();

        GameEntity gameEntityToCreate = socketMapper.toGameEntity(socketData.getInputMessage(), vers, slus);

        List<String> relatedVers = gameServerService.getRelatedVers(vers);
        boolean isMohh = relatedVers.equals(MOH07_OR_UHS);
        boolean duplicateName = gameRepository.existsByNameAndVersInAndEndTimeIsNull(gameEntityToCreate.getName(), relatedVers);
        if (duplicateName) {
            socketData.setIdMessage("gpscdupl");
            socketWriter.write(socket, socketData);
        } else if (isMohh) {
            SocketWrapper gpsSocketWrapper = socketManager.getAvailableGps();
            if (gpsSocketWrapper == null) {
                socketData.setIdMessage("gpscnfnd");
                socketWriter.write(socket, socketData);
            } else {
                socketWriter.write(socket, socketData);
                Map<String, String> content = Stream.of(new String[][]{
                        {"NAME", gameEntityToCreate.getName()},
                        {"PARAMS", gameEntityToCreate.getParams()},
                        {"SYSFLAGS", gameEntityToCreate.getSysflags()},
                        {"MINSIZE", String.valueOf(gameEntityToCreate.getMinsize())},
                        {"MAXSIZE", String.valueOf(gameEntityToCreate.getMaxsize())},
                        {"PASS", null != gameEntityToCreate.getPass() ? gameEntityToCreate.getPass() : ""},
                }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
                socketWriter.write(gpsSocketWrapper.getSocket(), new SocketData("$cre", null, content));

                new Thread(() -> {
                    int retries = 0;
                    while (retries < 5) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        Optional<GameEntity> gameEntityOpt = gameRepository.findByNameAndVersInAndEndTimeIsNull(gameEntityToCreate.getName(), relatedVers);
                        if (gameEntityOpt.isPresent()) {
                            GameEntity gameEntity = gameEntityOpt.get();
                            startGameConnection(socketWrapper, gameEntity, false);
                            ses(socket, gameEntity);
                            updateHostInfo(gameEntity);
                            break;
                        }
                        retries++;
                    }
                }).start();
            }
        } else {
            socketWriter.write(socket, socketData);

            // Set a game server port for MoHH2 if it's not already set (the game set it if there are other games...)
            String params = gameEntityToCreate.getParams();
            int serverPortPos = StringUtils.ordinalIndexOf(params, ",", 20);
            if (serverPortPos != -1 && serverPortPos < params.length()) {
                String[] paramArray = params.split(",");
                if (paramArray.length > 19 && paramArray[19].isEmpty()) {
                    paramArray[19] = Integer.toHexString(1); // Set game server port to 1, so it doesn't conflict with other games
                    params = String.join(",", paramArray);
                }
            }
            gameEntityToCreate.setParams(params);
            gameEntityToCreate.setStarted(true);
            gameRepository.save(gameEntityToCreate);

            startGameConnection(socketWrapper, gameEntityToCreate, false);
            ses(socket, gameEntityToCreate);
        }
    }

    /**
     * Send game updates to the host (player list, params)
     *
     * @param gameEntity The updated game entity
     */
    public void updateHostInfo(GameEntity gameEntity) {
        SocketWrapper hostSocketWrapper = socketManager.getHostSocketWrapperOfGame(gameEntity.getId());
        if (hostSocketWrapper != null) {
            Map<String, String> content = gameUtils.getGameInfo(gameEntity);
            socketWriter.write(hostSocketWrapper.getSocket(), new SocketData("+mgm", null, content));
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            socketWriter.write(hostSocketWrapper.getSocket(), new SocketData("+ses", null, content));
        }
    }

    /**
     * Create a new game
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void gcre(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        String slus = socketWrapper.getPersonaConnectionEntity().getSlus();
        GameEntity gameEntity = socketMapper.toGameEntity(socketData.getInputMessage(), vers, slus);

        // Some games don't provide a game name, so we set it to the persona name
        if (gameEntity.getName() == null || gameEntity.getName().isEmpty()) {
            gameEntity.setName(socketWrapper.getPersonaEntity().getPers());
        }

        List<String> relatedVers = gameServerService.getRelatedVers(vers);
        boolean duplicateName = gameRepository.existsByNameAndVersInAndEndTimeIsNull(gameEntity.getName(), relatedVers);

        if (duplicateName) {
            socketData.setIdMessage("gcredupl");
            socketWriter.write(socket, socketData);
        } else {
            gameEntity.setStarted(!gameServerService.isP2P(vers));

            synchronized (this) {
                // Set userflags directly to 1 (ready) for the host
                socketWrapper.setUserflags("1");
            }

            gameRepository.save(gameEntity);
            socketWriter.write(socket, new SocketData("gcre", null, gameUtils.getGameInfo(gameEntity)));

            startGameConnection(socketWrapper, gameEntity, true);
            personaService.who(socket, socketWrapper); // Used to set the game id

            if (gameServerService.isP2P(vers)) {
                personaService.usr(socket, socketWrapper);

                // Add the game to the room
                Room room = roomService.getRoomByVers(vers);
                room.getGameIds().add(gameEntity.getId());

                // Broadcast the game creation to people inside the room
                socketManager.getSocketWrapperByVers(vers).stream()
                        .filter(wrapper -> room.getPersonaIds().contains(wrapper.getPersonaEntity().getId()))
                        .forEach(wrapper -> {
                            socketWriter.write(wrapper.getSocket(), new SocketData("+agm", null, gameUtils.getGameInfo(gameEntity)));
                        });
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            socketWriter.write(socket, new SocketData("+mgm", null, gameUtils.getGameInfo(gameEntity)));
        }
    }

    /**
     * Leave game
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void glea(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        endGameConnection(socketWrapper);
        socketWriter.write(socket, socketData);
    }

    /**
     * Update the status of a persistent game spawn service.
     * If STATUS is "A", then the GPS is available to host a game.
     * If STATUS is "G", then the GPS is hosting a game.
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void gpss(Socket socket, SocketData socketData) {
        socketWriter.write(socket, socketData);

        String status = getValueFromSocket(socketData.getInputMessage(), "STATUS");

        SocketWrapper socketWrapper = socketManager.getSocketWrapper(socket);
        // Add a flag to indicate that the game is hosted
        if (("A").equals(status)) {
            socketWrapper.getIsGps().set(true);
            socketWrapper.getIsHosting().set(false);
        } else if (("G").equals(status)) {
            socketWrapper.getIsHosting().set(true);
        }

    }

    /**
     * Get periodic status from the GPS
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    private void gps(Socket socket, SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][]{
                {"PING", "EA60"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketData.setOutputData(content);
        socketData.setIdMessage("$gps");
        socketWriter.write(socket, socketData);
    }

    /**
     * Delete a game
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void gdel(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        endGame(socketWrapper);
        socketWriter.write(socket, socketData);
    }

    /**
     * Game status update (new game, new players in the game, etc.)
     *
     * @param socket     The socket to write the response to
     * @param gameEntity The game entity to start the session for
     */
    public void agm(Socket socket, GameEntity gameEntity) {
        socketWriter.write(socket, new SocketData("+agm", null, gameUtils.getGameInfo(gameEntity)));
    }

    /**
     * Join game
     *
     * @param socket     The socket to write the response to
     * @param gameEntity The game entity to start the session for
     */
    public void mgm(Socket socket, GameEntity gameEntity) {
        socketWriter.write(socket, new SocketData("+mgm", null, gameUtils.getGameInfo(gameEntity)));
    }

    /**
     * Start game
     *
     * @param socket     The socket to write the response to
     * @param gameEntity The game entity to start the session for
     */
    public void ses(Socket socket, GameEntity gameEntity) {
        socketWriter.write(socket, new SocketData("+ses", null, gameUtils.getGameInfo(gameEntity)));
    }

    /**
     * Game details (current opponents, ...)
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void gget(Socket socket, SocketData socketData) {
        String ident = getValueFromSocket(socketData.getInputMessage(), "IDENT");
        Optional<GameEntity> gameEntityOpt = gameRepository.findById(Long.valueOf(ident));
        if (gameEntityOpt.isPresent()) {
            GameEntity gameEntity = gameEntityOpt.get();
            socketWriter.write(socket, new SocketData("gget", null, gameUtils.getGameInfo(gameEntity)));
        } else {
            socketWriter.write(socket, new SocketData("gget", null, null));
        }
    }

    /**
     * Profanity filter a string
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void filt(Socket socket, SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][]{
                {"TEXT", getValueFromSocket(socketData.getInputMessage(), "TEXT")},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    /**
     * Registers a game entry
     *
     * @param socketWrapper The socket wrapper of current connection
     * @param gameEntity    The game entity to register
     */
    private void startGameConnection(SocketWrapper socketWrapper, GameEntity gameEntity, boolean isHost) {
        // Close any game report that wasn't property ended (e.g. use Dolphin save state to leave)
        endGameConnection(socketWrapper);

        GameConnectionEntity gameConnectionEntity = new GameConnectionEntity();
        gameConnectionEntity.setGame(gameEntity);
        gameConnectionEntity.setPersonaConnection(socketWrapper.getPersonaConnectionEntity());
        gameConnectionEntity.setHost(isHost);
        gameConnectionEntity.setStartTime(LocalDateTime.now());
        gameConnectionRepository.save(gameConnectionEntity);
    }

    /**
     * Ends the game connection because the player has left the game
     */
    public void endGameConnection(SocketWrapper socketWrapper) {
        Optional<GameConnectionEntity> gameConnectionEntityOpt =
                gameConnectionRepository.findByPersonaConnectionIdAndEndTimeIsNull(socketWrapper.getPersonaConnectionEntity().getId());
        if (gameConnectionEntityOpt.isPresent()) {
            GameConnectionEntity gameConnectionEntity = gameConnectionEntityOpt.get();
            GameEntity gameEntity = gameConnectionEntity.getGame();

            if (gameServerService.isP2P(gameEntity.getVers())) {
                // If the player is the host, we need to close the game and notify all players
                if (gameConnectionEntity.isHost()) {
                    endGame(socketWrapper);
                } else {
                    gameConnectionEntity.setEndTime(LocalDateTime.now());
                    gameConnectionRepository.save(gameConnectionEntity);
                    // Broadcast the game leave to all connected clients in the game
                    socketManager.getSocketWrapperByVers(gameEntity.getVers())
                            .forEach(wrapper -> {
                                Socket socket = wrapper.getSocket();
                                agm(socket, gameEntity);
                                mgm(socket, gameEntity);
                            });
                }
            } else {
                if (socketWrapper.getIsDedicatedHost().get()) {
                    endGame(socketWrapper);
                } else {
                    gameConnectionEntity.setEndTime(LocalDateTime.now());
                    gameConnectionRepository.save(gameConnectionEntity);
                    updateHostInfo(gameEntity);
                }
            }
        }
    }

    /**
     * Ends the game and all connections to the game
     * This is used when the game is over or when the host leaves the game
     *
     * @param socketWrapper The socket wrapper of current connection
     */
    public void endGame(SocketWrapper socketWrapper) {
        List<GameEntity> gameEntity = gameRepository.findCurrentGameOfPersona(socketWrapper.getPersonaConnectionEntity().getId());
        if (!gameEntity.isEmpty()) {
            GameEntity game = gameEntity.get(0);
            LocalDateTime now = LocalDateTime.now();
            game.setEndTime(now);
            gameRepository.save(game);
            game.getGameConnections().stream().filter(connection -> null == connection.getEndTime()).forEach(report -> {
                report.setEndTime(now);
                gameConnectionRepository.save(report);
            });

            // For P2P games, remove the game from the room and broadcast the game deletion
            if (gameServerService.isP2P(game.getVers())) {
                roomService.removeGameFromRoom(game);
            }
        }
    }

    /**
     * Set an end time to all active connections and games when the server boots or shuts down
     */
    @PostConstruct
    @PreDestroy
    private void closeActiveConnectionsAndGames() {
        LocalDateTime now = LocalDateTime.now();
        int gameConnectionsCleaned = gameConnectionRepository.setEndTimeForAllUnfinishedGameConnections(now);
        int gameCleaned = gameRepository.setEndTimeForAllUnfinishedGames(now);
        int personaConnectionsCleaned = personaConnectionRepository.setEndTimeForAllUnfinishedPersonaConnections(now);
        log.info("Data cleaned: {} games, {} game connections, {} persona connections", gameCleaned, gameConnectionsCleaned, personaConnectionsCleaned);
    }

    /**
     * Data cleanup :
     * - Manually close expired games (only applies to mohh2 as games aren't hosted)
     * - Close persona connections, game reports and games (if persona was the host) when the socket is closed
     */
    public void dataCleanup() {
        LocalDateTime now = LocalDateTime.now();

        // Manually close expired games
        List<GameEntity> gameEntities = gameRepository.findByEndTimeIsNull();
        gameEntities.forEach(gameEntity -> {
            Set<GameConnectionEntity> gameConnections = gameEntity.getGameConnections();
            if (gameConnections.stream().noneMatch(connection -> connection.isHost() || null == connection.getEndTime())) {
                if (gameConnections.stream().allMatch(connection -> connection.getEndTime().plusSeconds(90).isBefore(now))) {
                    log.info("Closing expired game: {} - {}", gameEntity.getId(), gameEntity.getName());
                    gameEntity.setEndTime(now);
                    gameRepository.save(gameEntity);
                }
            }
        });

        // Get all active socket addresses from socket manager
        Set<String> activeAddresses = socketManager.getActiveSocketIdentifiers();

        // Close personna connections for inactive connections
        List<PersonaConnectionEntity> inactivePersonaConnections = personaConnectionRepository
                .findByEndTimeIsNullAndAddressNotIn(activeAddresses);
        if (!inactivePersonaConnections.isEmpty()) {
            inactivePersonaConnections.forEach(connection -> {
                log.info("Socket closed for persona connection: {}", connection.getId());
                connection.setEndTime(now);
            });
            personaConnectionRepository.saveAll(inactivePersonaConnections);
        }

        // Close game connections for inactive persona connections
        List<GameConnectionEntity> inactiveGameConnections = gameConnectionRepository
                .findByEndTimeIsNullAndPersonaConnectionAddressNotIn(activeAddresses);
        if (!inactiveGameConnections.isEmpty()) {
            inactiveGameConnections.forEach(report -> {
                log.info("Socket closed for game report: {}", report.getId());
                report.setEndTime(now);
            });
            gameConnectionRepository.saveAll(inactiveGameConnections);
        }

        // Close games where host is inactive
        List<GameEntity> gamesWithInactiveHost = gameRepository.findByEndTimeIsNullAndGameConnectionsIsHostIsTrueAndGameConnectionsPersonaConnectionAddressNotIn(activeAddresses);
        if (!gamesWithInactiveHost.isEmpty()) {
            gamesWithInactiveHost.forEach(game -> {
                log.info("Host socket closed for game: {}", game.getId());
                game.setEndTime(now);
                game.getGameConnections().stream()
                        .filter(connection -> connection.getEndTime() == null)
                        .forEach(connection -> {
                            log.info("Closing game connection: {}", connection.getId());
                            connection.setEndTime(now);
                        });
            });
            gameRepository.saveAll(gamesWithInactiveHost);
        }
    }

}
