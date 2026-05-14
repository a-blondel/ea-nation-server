package eu.eahub.services.core;

import eu.eahub.dto.Room;
import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.*;
import eu.eahub.mappers.SocketMapper;
import eu.eahub.repositories.core.*;
import eu.eahub.services.server.GameServerService;
import eu.eahub.services.server.SocketManager;
import eu.eahub.services.stats.MohhStatsService;
import eu.eahub.steps.SocketWriter;
import eu.eahub.utils.GameUtils;
import eu.eahub.utils.SocketUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eu.eahub.services.server.GameServerService.*;
import static eu.eahub.utils.SocketUtils.getValueFromSocket;

@Slf4j
@RequiredArgsConstructor
@Service
public class GameService {

    public static final String NFS_MW_CONVERT_TO_RANKED_PASSWORD = "ranked";
    private final GameRepository gameRepository;
    private final GameConnectionRepository gameConnectionRepository;
    private final PersonaConnectionRepository personaConnectionRepository;
    private final AccountRepository accountRepository;
    private final BlacklistRepository blacklistRepository;
    private final SocketMapper socketMapper;
    private final PersonaService personaService;
    private final GameServerService gameServerService;
    private final RoomService roomService;
    private final UserSetService userSetService;
    private final MohhStatsService mohhStatsService;
    private final SocketWriter socketWriter;
    private final SocketManager socketManager;
    private final UserSetRepository userSetRepository;
    private final UserSetMemberRepository userSetMemberRepository;
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

        // Temporarily disable the game search feature so it doesn't trigger instant start of a game with +ses
        if (true) {
            return;
        }

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
                String name = socketWrapper.getPersonaConnectionEntity().getVers();

                while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
                    log.debug("Searching for game for socket: {}", socket.getRemoteSocketAddress());
                    List<GameEntity> gameEntities = gameRepository.findByVersAndEndTimeIsNull(name);

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
                            GameEntity gameEntity = gameEntities.getFirst(); // Get the first game found
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
                        // Do we really want to update start time here?
                        // It is more accurate for the stats, but it will make the discord bot to send twice the game join event
                        //connection.setStartTime(LocalDateTime.now());
                        //gameConnectionRepository.save(connection);
                    }
                    // Don't update game start time here, the WHEN attribute of the 'rank' packet uses the first declared start time (and it's used as an identifier for the game)
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
        PersonaConnectionEntity personaConnectionEntity = socketWrapper.getPersonaConnectionEntity();
        if (gameServerService.isP2P(personaConnectionEntity.getVers())) {
            // On NHL, the value is 0 or 1 to know if the client is ready or not
            String userflags = getValueFromSocket(socketData.getInputMessage(), "USERFLAGS");
            String userparams = getValueFromSocket(socketData.getInputMessage(), "USERPARAMS");
            String sysflags = getValueFromSocket(socketData.getInputMessage(), "SYSFLAGS");
            String params = getValueFromSocket(socketData.getInputMessage(), "PARAMS");
            String kick = getValueFromSocket(socketData.getInputMessage(), "KICK");

            if (userflags != null) {
                synchronized (this) {
                    socketWrapper.setUserflags(userflags);
                }
                // Broadcast the userflags to all players in the game
                broadcastGameStateToPlayers(personaConnectionEntity);
            }
            if (userparams != null) {
                // Normalize USERPARAMS  (HOST Ready button fix)
                String normalizedUserparams = normalizeUserparams(userparams, personaConnectionEntity.getVers());

                synchronized (this) {
                    socketWrapper.setUserparams(normalizedUserparams);
                }
                // Broadcast the userparams to all players in the game
                broadcastGameStateToPlayers(personaConnectionEntity);
            }
            if (sysflags != null) {
                // Update sysflags in the game entity
                GameEntity gameEntity = gameConnectionRepository.findByPersonaConnectionIdAndEndTimeIsNull(personaConnectionEntity.getId())
                        .map(GameConnectionEntity::getGame).orElse(null);
                if (gameEntity != null) {
                    gameEntity.setSysflags(sysflags);
                    gameRepository.save(gameEntity);

                    if (!sysflags.isEmpty()) {
                        Map<String, String> content = gameUtils.getGameInfo(gameEntity);
                        socketWriter.write(socketWrapper.getSocket(), new SocketData("gset", null, content));
                        return;
                    }
                }
            }
            if (params != null) {
                // Update params in the game entity
                GameEntity gameEntity = gameConnectionRepository.findByPersonaConnectionIdAndEndTimeIsNull(personaConnectionEntity.getId())
                        .map(GameConnectionEntity::getGame).orElse(null);
                if (gameEntity != null) {
                    gameEntity.setParams(params);
                    gameRepository.save(gameEntity);
                    // Should we broadcast the game update to all players in the room but not in a game ?
                }
            }
            if (kick != null) {
                GameEntity gameEntity = gameConnectionRepository.findByPersonaConnectionIdAndEndTimeIsNull(personaConnectionEntity.getId())
                        .map(GameConnectionEntity::getGame).orElse(null);
                if (gameEntity != null) {
                    for (SocketWrapper clientWrapper : socketManager.getSocketWrapperByVers(personaConnectionEntity.getVers())) {
                        if (kick.equals(clientWrapper.getPersonaEntity().getPers())) {
                            Map<String, String> content = Collections.singletonMap("GAME", gameEntity.getId().toString());
                            socketWriter.write(clientWrapper.getSocket(), new SocketData("+kik", null, content));
                            endGameConnection(clientWrapper);
                            break;
                        }
                    }
                }
            }
        }
        socketWriter.write(socket, socketData);

        // For MoHH dedicated server, gset means a map rotation, but instead of just updating the game parameters,
        // we end the current game and create a new one with the new parameters.
        if (PSP_MOH07.equals(personaConnectionEntity.getVers())) {
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
        String name = socketWrapper.getPersonaConnectionEntity().getVers();
        List<GameEntity> gameEntities = gameRepository.findByVersAndEndTimeIsNull(name);

        Map<String, String> paramsMap = SocketUtils.getMapFromSocket(socketData.getInputMessage());
        List<GameEntity> filteredGameEntities = filterGameEntities(gameEntities, paramsMap, name);

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
        if (ALL_MOH.contains(vers)) {
            if (gameEntity.getVers().equals("WII_MOH08")) { // TODO : delete this when the server is fixed
                //gameEntity.setParams("8,12d,,1,1,-1,,,a,3,1,1,1,1,1,1,1,1,20,e4a,e68,15f90,122d0022"); // PSP
                gameEntity.setParams("8,12d,,1,-1,,,a,3,-1,1,1,1,1,1,1,1,1,20,e4a,e68,15f90,122d0022"); // Wii
            }
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

        String userparams = getValueFromSocket(socketData.getInputMessage(), "USERPARAMS");
        if (userparams != null) {
            synchronized (this) {
                socketWrapper.setUserparams(userparams);
            }
        }

        String ident = getValueFromSocket(socketData.getInputMessage(), "IDENT");
        Optional<GameEntity> gameEntityOpt;
        if (ident != null) {
            gameEntityOpt = gameRepository.findById(Long.valueOf(ident));
        } else {
            // Some games don't provide an identifier, so we need to find the game by name and version
            String name = getValueFromSocket(socketData.getInputMessage(), "NAME");
            gameEntityOpt = gameRepository.findByNameAndVersAndEndTimeIsNull(name, socketWrapper.getPersonaConnectionEntity().getVers());
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
            if (gameEntity.isStarted() && !ALL_MOH.contains(gameEntity.getVers())) {
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

            socketData.setOutputData(gameUtils.getGameInfo(gameEntity)); // Required for NFS:MW on PC/PS2
            socketWriter.write(socket, socketData);

            // Check if game is P2P
            if (gameServerService.isP2P(gameEntity.getVers())) {
                // Reset userflags
                synchronized (this) {
                    socketWrapper.setUserflags("0");
                }

                // Send who to the joining client
                personaService.who(socket, socketWrapper);

                if (USERSETS_GAMES.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
                    // Send +ust to the client who joined
                    userSetService.sendUserSetInfo(socket, socketWrapper);

                    // Broadcast +usm for all game members to update G= attribute
                    userSetService.broadcastUserSetMembersForGame(gameEntity);
                }

                // Broadcast the game join to all connected clients in the room
                // Inform about all players in the game (+usr for each player, to each player), except for UserSet games
                List<GameConnectionEntity> gameConnections = gameConnectionRepository.findByGameIdAndEndTimeIsNull(gameEntity.getId());
                for (SocketWrapper clientWrapper : socketManager.getSocketWrapperByVers(gameEntity.getVers())) {
                    if (!USERSETS_GAMES.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
                        for (GameConnectionEntity gameConnection : gameConnections) { // For each player in the game, send +usr to each player
                            SocketWrapper inGameWrapper = socketManager.getSocketWrapperByPersonaConnectionId(gameConnection.getPersonaConnection().getId());
                            if (inGameWrapper != null && gameConnections.stream().anyMatch(gameConnectionEntity -> gameConnectionEntity.getPersonaConnection().getId().equals(clientWrapper.getPersonaConnectionEntity().getId()))) {
                                personaService.usr(clientWrapper.getSocket(), inGameWrapper); // Update user info for each player
                            }
                        }
                        agm(clientWrapper.getSocket(), gameEntity);
                    }
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

        String name = socketWrapper.getPersonaConnectionEntity().getVers();

        GameEntity gameEntityToCreate = socketMapper.toGameEntity(socketData.getInputMessage(), name);

        boolean duplicateName = gameRepository.existsByNameAndVersAndEndTimeIsNull(gameEntityToCreate.getName(), name);
        if (duplicateName) {
            socketData.setIdMessage("gpscdupl");
            socketWriter.write(socket, socketData);
        } else {
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
                        Optional<GameEntity> gameEntityOpt = gameRepository.findByNameAndVersAndEndTimeIsNull(gameEntityToCreate.getName(), name);
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
        String name = socketWrapper.getPersonaConnectionEntity().getVers();
        GameEntity gameEntity = socketMapper.toGameEntity(socketData.getInputMessage(), name);

        String userparams = getValueFromSocket(socketData.getInputMessage(), "USERPARAMS");
        if (userparams != null) {
            synchronized (this) {
                socketWrapper.setUserparams(userparams);
            }
        }

        // Some games don't provide a game name, so we set it to the persona name
        if (gameEntity.getName() == null || gameEntity.getName().isEmpty()) {
            gameEntity.setName(socketWrapper.getPersonaEntity().getPers());
        }

        boolean duplicateName = gameRepository.existsByNameAndVersAndEndTimeIsNull(gameEntity.getName(), name);

        // Custom logic for NFS Most Wanted clients allowing to create custom ranked games based on unranked game parameters
        // To do so, a specific password ("ranked") must be provided, then we set the sysflags to 262144 (ranked) and remove the password
        if (PSP_NFS06.equals(name) && gameEntity.getSysflags().equals("0")
                && gameEntity.getPass() != null && gameEntity.getPass().equals(NFS_MW_CONVERT_TO_RANKED_PASSWORD)) {
            gameEntity.setSysflags("262144");
            gameEntity.setPass(null);
        }

        if (duplicateName) {
            socketData.setIdMessage("gcredupl");
            socketWriter.write(socket, socketData);
        } else {
            gameEntity.setStarted(!gameServerService.isP2P(name));

            synchronized (this) {
                // Set userflags directly to 1 (ready) for the host
                socketWrapper.setUserflags("1");
            }

            gameRepository.save(gameEntity);
            startGameConnection(socketWrapper, gameEntity, true);
            socketWriter.write(socket, new SocketData("gcre", null, gameUtils.getGameInfo(gameEntity)));

            if (gameServerService.isP2P(name)) {
                if (USERSETS_GAMES.contains(socketWrapper.getPersonaConnectionEntity().getVers())) {
                    // Broadcast +who and +usm with G= attribute to all UserSet members
                    userSetService.broadcastUserSetStateAfterGameCreation(socketWrapper);
                } else {
                    personaService.usr(socket, socketWrapper);

                    // Add the game to the room
                    Room room = roomService.getRoomByVers(name);
                    room.getGameIds().add(gameEntity.getId());

                    // Broadcast the game creation to people inside the room
                    socketManager.getSocketWrapperByVers(name).stream()
                            .filter(wrapper -> null != wrapper.getPersonaEntity() && room.getPersonaIds().contains(wrapper.getPersonaEntity().getId()))
                            .forEach(wrapper -> socketWriter.write(wrapper.getSocket(), new SocketData("+agm", null, gameUtils.getGameInfo(gameEntity))));
                }
            }
            personaService.who(socket, socketWrapper); // Used to set the game id with G= and US= attributes

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
        socketWriter.write(socket, socketData);
        if (socketWrapper != null) {
            endGameConnection(socketWrapper);
        } else {
            log.warn("Socket wrapper is null for socket: {}", socket.getRemoteSocketAddress());
        }
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

        SocketWrapper socketWrapper = socketManager.getSocketWrapperBySocket(socket);
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
        socketWriter.write(socket, socketData);
        endGame(socketWrapper);
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
        if (gameEntity.getVers().equals("WII_MOH08")) { // TODO : delete this when the server is fixed
            //gameEntity.setParams("8,12d,,1,1,-1,,,a,3,1,1,1,1,1,1,1,1,20,e4a,e68,15f90,122d0022"); // PSP
            gameEntity.setParams("8,12d,,1,-1,,,a,3,-1,1,1,1,1,1,1,1,1,20,e4a,e68,15f90,122d0022"); // Wii
        }
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
            if (gameEntity.getVers().equals("WII_MOH08")) { // TODO : delete this when the server is fixed
                //gameEntity.setParams("8,12d,,1,1,-1,,,a,3,1,1,1,1,1,1,1,1,20,e4a,e68,15f90,122d0022"); // PSP
                gameEntity.setParams("8,12d,,1,-1,,,a,3,-1,1,1,1,1,1,1,1,1,20,e4a,e68,15f90,122d0022"); // Wii
            }
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
                                if (!wrapper.getPersonaConnectionEntity().getId().equals(socketWrapper.getPersonaConnectionEntity().getId())) {
                                    mgm(socket, gameEntity);
                                }
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
            GameEntity game = gameEntity.getFirst();
            String vers = game.getVers();
            LocalDateTime now = LocalDateTime.now();
            game.setEndTime(now);
            gameRepository.save(game);
            game.getGameConnections().stream().filter(connection -> null == connection.getEndTime()).forEach(report -> {
                report.setEndTime(now);
                gameConnectionRepository.save(report);
            });

            // For P2P games, remove the game from the room and broadcast the game deletion
            // But for USERSETS_GAMES, do not broadcast game removal - players should stay in the UserSet lobby
            if (gameServerService.isP2P(game.getVers()) && !USERSETS_GAMES.contains(game.getVers())) {
                roomService.removeGameFromRoom(game, socketWrapper);

                // For PS2 games, broadcast room users to all remaining online players
                if (vers.startsWith("PS2_") && socketWrapper.getPersonaEntity() != null) {
                    Room room = roomService.getRoomByPersonaId(socketWrapper.getPersonaEntity().getId());
                    if (room != null) {
                        roomService.broadcastRoomUsers(vers, room.getId());
                    }
                }
            } else if (USERSETS_GAMES.contains(game.getVers())) {
                Map<String, String> mgmContent = Collections.singletonMap("IDENT", String.valueOf(game.getId()));
                socketWriter.write(socketWrapper.getSocket(), new SocketData("+mgm", null, mgmContent));
            }
        }
    }

    /**
     * Normalize USERPARAMS (HOST Ready button fix)
     * By e.g. FIFA 07 PS2 the HOST sends byte6 with bit 4 set when Ready, but the game UI expects byte6=0x8b
     *
     * @param userparams Original USERPARAMS string
     * @param vers       Game version
     * @return Normalized USERPARAMS or original if not applicable
     */
    private String normalizeUserparams(String userparams, String vers) {
        if (!vers.startsWith("PS2_") || userparams == null || userparams.length() < 7) {
            return userparams;
        }

        byte byte6 = (byte) userparams.charAt(6);

        // Check if HOST is Ready: byte6 bit 4 set (0x9d, 0x9b, etc) and not CLIENT (0x8b)
        boolean isHostReady = (byte6 != (byte) 0x8b) && ((byte6 & 0x10) != 0);

        if (isHostReady) {
            // Normalize: set byte4 bit 3 + convert byte6 to CLIENT format (0x8b)
            byte[] userparamsBytes = userparams.getBytes(StandardCharsets.ISO_8859_1);
            userparamsBytes[4] |= (byte) 0x08;  // Set bit 3 for checkmark
            userparamsBytes[6] = (byte) 0x8b;   // Convert to CLIENT format
            return new String(userparamsBytes, StandardCharsets.ISO_8859_1);
        }

        return userparams;
    }

    /**
     * Broadcast game state to all players in the game
     * This is used when a player changes their state (ready, team selection, etc.)
     *
     * @param personaConnectionEntity The persona connection entity of the player who changed their state
     */
    private void broadcastGameStateToPlayers(PersonaConnectionEntity personaConnectionEntity) {
        Optional<GameConnectionEntity> gameConnectionOpt = gameConnectionRepository
                .findByPersonaConnectionIdAndEndTimeIsNull(personaConnectionEntity.getId());

        if (gameConnectionOpt.isPresent()) {
            GameConnectionEntity gameConnectionEntity = gameConnectionOpt.get();
            GameEntity gameEntity = gameConnectionEntity.getGame();
            List<GameConnectionEntity> gameConnections = gameConnectionRepository
                    .findByGameIdAndEndTimeIsNull(gameEntity.getId());
            for (GameConnectionEntity gameConnection : gameConnections) {
                SocketWrapper gameConnectionSocketWrapper = socketManager
                        .getSocketWrapperByPersonaConnectionId(gameConnection.getPersonaConnection().getId());
                if (gameConnectionSocketWrapper != null) {
                    agm(gameConnectionSocketWrapper.getSocket(), gameEntity);
                    mgm(gameConnectionSocketWrapper.getSocket(), gameEntity);
                }
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

        // Close UserSets where owner is not connected
        List<UserSetEntity> userSetsWithInactiveOwner = userSetRepository.findByEndTimeIsNullAndOwnerNotConnected(activeAddresses);
        if (!userSetsWithInactiveOwner.isEmpty()) {
            userSetsWithInactiveOwner.forEach(userSet -> {
                log.info("Owner socket closed for UserSet: {} - {}", userSet.getId(), userSet.getName());
                userSet.setEndTime(now);
                // Close all members of the userset
                List<UserSetMemberEntity> members = userSetMemberRepository.findByUserSetIdAndEndTimeIsNull(userSet.getId());
                members.forEach(member -> {
                    log.info("Closing UserSet member: {} for userset {}", member.getPersona().getPers(), userSet.getName());
                    member.setEndTime(now);
                });
                userSetMemberRepository.saveAll(members);
            });
            userSetRepository.saveAll(userSetsWithInactiveOwner);
        }

        // Close UserSet memberships for inactive personas
        List<UserSetMemberEntity> inactiveMembers = userSetMemberRepository.findByEndTimeIsNullAndPersonaNotConnected(activeAddresses);
        if (!inactiveMembers.isEmpty()) {
            inactiveMembers.forEach(member -> {
                log.info("Socket closed for UserSet member: {} in userset {}",
                        member.getPersona().getPers(), member.getUserSet().getName());
                member.setEndTime(now);
            });
            userSetMemberRepository.saveAll(inactiveMembers);
        }
    }

}
