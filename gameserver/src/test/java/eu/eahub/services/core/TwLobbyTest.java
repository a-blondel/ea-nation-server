package eu.eahub.services.core;

import eu.eahub.config.GameServerConfig;
import eu.eahub.config.TwConfig;
import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.GameConnectionEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaConnectionEntity;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.mappers.SocketMapper;
import eu.eahub.repositories.core.GameConnectionRepository;
import eu.eahub.repositories.core.GameRepository;
import eu.eahub.repositories.core.PersonaConnectionRepository;
import eu.eahub.repositories.core.PersonaRepository;
import eu.eahub.services.TwFrames;
import eu.eahub.services.TwTestConfig;
import eu.eahub.services.server.GameServerService;
import eu.eahub.services.server.SocketManager;
import eu.eahub.services.social.TwBuddyService;
import eu.eahub.services.social.TwChatHistoryService;
import eu.eahub.services.stats.FifaStatsService;
import eu.eahub.services.stats.MohhStatsService;
import eu.eahub.services.stats.NfsRankService;
import eu.eahub.services.stats.NfsStatsService;
import eu.eahub.services.stats.NhlStatsService;
import eu.eahub.services.stats.StatsService;
import eu.eahub.services.stats.TwStatsService;
import eu.eahub.steps.SocketWriter;
import eu.eahub.utils.EmailUtils;
import eu.eahub.utils.GameUtils;
import eu.eahub.utils.PasswordUtils;
import eu.eahub.utils.PersonaUtils;
import eu.eahub.utils.Props;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static eu.eahub.services.server.GameServerService.PS2_TW04;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The TW04 lobby through the shared services: rooms, chat, challenges, sessions and their results
 */
@DataJpaTest
@ContextConfiguration(classes = TwTestConfig.class)
@Import({TwConfig.class, RoomService.class, GameService.class, AuthService.class, AccountService.class, UserSetService.class, PersonaService.class,
        StatsService.class, SocketManager.class, GameServerService.class, GameUtils.class, PersonaUtils.class, TwStatsService.class,
        TwChallengeService.class, TwRoomService.class, TwChatHistoryService.class, TwBuddyService.class,
        GameServerConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TwLobbyTest {

    private static final String[] PERSONAS = {"Alice", "Bob", "Charlie"};

    private static final List<String> ROOMS = List.of("Match.T.East", "Match.I.West", "Match.G.Beginner", "Match.E.Advanced", "Match.R.Open",
            "Stroke.T.East", "Stroke.I.West", "Stroke.G.Beginner", "Stroke.E.Advanced", "Stroke.R.Open");

    @MockitoBean
    private SocketWriter socketWriter;
    @MockitoBean
    private Props props;
    @MockitoBean
    private SocketMapper socketMapper;
    @MockitoBean
    private PasswordUtils passwordUtils;
    @MockitoBean
    private EmailUtils emailUtils;
    @MockitoBean
    private MohhStatsService mohhStatsService;
    @MockitoBean
    private NfsStatsService nfsStatsService;
    @MockitoBean
    private NhlStatsService nhlStatsService;
    @MockitoBean
    private FifaStatsService fifaStatsService;
    @MockitoBean
    private NfsRankService nfsRankService;
    @Autowired
    private TwConfig twConfig;

    @Autowired
    private GameServerConfig gameServerConfig;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PersonaRepository personaRepository;
    @Autowired
    private PersonaConnectionRepository personaConnectionRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private GameConnectionRepository gameConnectionRepository;
    @Autowired
    private SocketManager socketManager;
    @Autowired
    private RoomService roomService;
    @Autowired
    private GameService gameService;
    @Autowired
    private AuthService authService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private UserSetService userSetService;
    @Autowired
    private PersonaService personaService;
    @Autowired
    private StatsService statsService;
    private final TwFrames frames = new TwFrames();


    @BeforeEach
    void setup() {
        TwTestConfig.reset(jdbc, PERSONAS);
        // The TW04 server as configured in application.yml, enabled and alone on its port
        GameServerConfig.GameServer tw04 = gameServerConfig.getServers().stream().filter(s -> s.getName().equals(PS2_TW04)).findFirst().orElseThrow();
        tw04.setEnabled(true);
        gameServerConfig.setServers(List.of(tw04));
        roomService.generateRooms();
        when(props.getTcpHost()).thenReturn("127.0.0.1");
        when(props.getTcpBuddyPort()).thenReturn(13504);
        frames.capture(socketWriter);
    }

    /**
     * A logged-in lobby connection from this address
     */
    private SocketWrapper player(long personaId, String address) throws Exception {
        Socket socket = mock(Socket.class);
        when(socket.getLocalPort()).thenReturn(10200);
        when(socket.isConnected()).thenReturn(true);
        when(socket.getInetAddress()).thenReturn(InetAddress.getByName(address));
        String identifier = "/" + address + ":" + (1000 + personaId);
        socketManager.addSocket(identifier, socket);
        SocketWrapper wrapper = socketManager.getSocketWrapper(identifier);
        // Personas are numbered in the order TwTestConfig.reset created them
        PersonaEntity persona = personaRepository.findByPers(PERSONAS[(int) personaId - 1]).orElseThrow();
        PersonaConnectionEntity connection = new PersonaConnectionEntity();
        connection.setPersona(persona);
        connection.setVers(PS2_TW04);
        connection.setAddress(identifier);
        connection.setStartTime(LocalDateTime.now());
        personaConnectionRepository.save(connection);
        wrapper.setPersonaEntity(persona);
        wrapper.setPersonaConnectionEntity(connection);
        return wrapper;
    }

    /**
     * Send a message as SocketProcessor dispatches it
     */
    private void send(SocketWrapper player, String verb, String body) {
        Socket socket = player.getSocket();
        SocketData data = new SocketData(verb, body + "\0", null);
        switch (verb) {
            case "sele" -> authService.sele(socket, data, player);
            case "addr" -> authService.addr(socket, data, player);
            case "news" -> authService.news(socket, data);
            case "room" -> roomService.room(socket, data, player);
            case "move" -> roomService.move(socket, data, player);
            case "peek" -> roomService.peek(socket, data, player);
            case "mesg" -> roomService.mesg(socket, data, player);
            case "chal" -> gameService.chal(socket, data, player);
            case "rank" -> statsService.rank(socket, data, player);
            case "snap" -> statsService.snap(socket, data, player);
            case "user" -> accountService.user(socket, data, player);
            case "onln" -> userSetService.onln(socket, data, player);
            default -> throw new IllegalArgumentException(verb);
        }
    }

    /**
     * Close a lobby connection as TcpSocketThread does
     */
    private void disconnect(SocketWrapper player) {
        when(player.getSocket().isClosed()).thenReturn(true);
        socketManager.removeSocket(player.getIdentifier());
        gameService.endGameConnection(player);
        personaService.endPersonaConnection(player);
    }

    private List<TwFrames.Frame> received(SocketWrapper player, String verb) {
        return frames.received(player.getSocket(), verb);
    }

    private void subscribe(SocketWrapper player) {
        send(player, "sele", "ROOMS=1 GAMES=1 USERS=1 RANKS=1 MESGS=1");
    }

    /**
     * Alice and Bob behind different routers, both in Match.T.East
     */
    private SocketWrapper[] pair() throws Exception {
        SocketWrapper a = player(1, "198.51.100.10");
        SocketWrapper b = player(2, "198.51.100.90");
        subscribe(a);
        subscribe(b);
        send(a, "addr", "ADDR=192.0.2.100 PORT=3658");
        send(b, "addr", "ADDR=192.0.2.100 PORT=3658");
        send(a, "move", "NAME=Match.T.East");
        send(b, "move", "NAME=Match.T.East");
        frames.clear();
        return new SocketWrapper[]{a, b};
    }

    private void invite(SocketWrapper a, SocketWrapper b) {
        send(a, "mesg", "PRIV=" + b.getPersonaEntity().getPers() + " ATTR=3 TEXT=challenge%0aCOUR%3d4%0aGLFR%3d1%0aSHOT%3d30");
        send(b, "mesg", "PRIV=" + a.getPersonaEntity().getPers() + " ATTR=3 TEXT=accept");
    }

    private Map<String, String> start(SocketWrapper host, SocketWrapper guest) {
        invite(host, guest);
        send(host, "chal", "PERS=" + guest.getPersonaEntity().getPers() + " HOST=1");
        send(guest, "chal", "PERS=" + host.getPersonaEntity().getPers() + " HOST=0");
        return received(host, "+ses").getLast().fields();
    }

    /**
     * A finished 18-hole result for the session that +ses started: the host wins 2 up, on 70 to the guest's 72
     * Sent like the game sends it: tab-separated, with the space in WHEN unquoted
     */
    private String result(Map<String, String> session) {
        long epoch = Long.parseLong(session.get("WHEN").substring(1), 16);
        Map<String, String> fields = new HashMap<>();
        for (int side = 0; side < 2; side++) {
            fields.put("DONE" + side, "1");
            fields.put("QUIT" + side, "0");
            fields.put("HOLES" + side, "18");
            fields.put("STROKES" + side, side == 0 ? "70" : "72");
            fields.put("SCORE" + side, side == 0 ? "2" : "-2");
        }
        fields.put("WHEN", LocalDateTime.ofEpochSecond(epoch, 0, ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy.M.d H:mm:ss")));
        fields.put("AUTH", session.get("AUTH"));
        return fields.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("\t"));
    }

    private GameEntity game(Map<String, String> session) {
        long epoch = Long.parseLong(session.get("WHEN").substring(1), 16);
        LocalDateTime start = LocalDateTime.ofEpochSecond(epoch, 0, ZoneOffset.UTC).atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        return gameRepository.findAll().stream().filter(game -> game.getStartTime().equals(start)).findFirst().orElseThrow();
    }

    /**
     * Move a session's start back by a minute, as if it had started earlier: a session started right after it then
     * has its own start second, which identifies a match when it is reported
     *
     * @return the session fields with the earlier WHEN
     */
    private Map<String, String> rewind(Map<String, String> session) {
        GameEntity game = game(session);
        game.setStartTime(game.getStartTime().minusMinutes(1));
        gameRepository.save(game);
        long epoch = game.getStartTime().atZone(ZoneId.systemDefault()).toEpochSecond();
        Map<String, String> earlier = new HashMap<>(session);
        earlier.put("WHEN", "$" + String.format("%016x", epoch));
        return earlier;
    }

    @Test
    void subscriptionsPushTheConfiguredRoomsAndRespectUnsubscribe() throws Exception {
        SocketWrapper player = player(1, "198.51.100.10");
        subscribe(player);
        List<TwFrames.Frame> rooms = received(player, "+rom");
        assertEquals(ROOMS, rooms.stream().map(frame -> frame.fields().get("N")).toList());
        assertEquals(10, rooms.stream().map(frame -> frame.fields().get("I")).distinct().count());
        send(player, "sele", "ROOMS=0");
        frames.clear();
        // The real client can send a leftover PASS even for a public room
        send(player, "move", "NAME=Stroke.T.East PASS=leftover");
        assertEquals("1", received(player, "move").getLast().fields().get("COUNT"));
        assertTrue(received(player, "+rom").isEmpty());
        send(player, "news", "NAME=0");
        assertEquals("127.0.0.1:13504\n", received(player, "news").getLast().text());
        send(player, "news", "NAME=1");
        assertEquals(twConfig.getNews() + "\n", received(player, "news").getLast().text());
    }

    @Test
    void twoPlayersSeeMembershipAndDisconnectRemovesTheStaleRosterSlot() throws Exception {
        SocketWrapper a = player(1, "198.51.100.10");
        SocketWrapper b = player(2, "198.51.100.90");
        subscribe(a);
        subscribe(b);
        send(a, "move", "NAME=Match.T.East");
        send(b, "move", "NAME=Match.T.East");
        assertEquals("2", received(b, "move").getLast().fields().get("COUNT"));
        assertTrue(received(a, "+usr").stream().anyMatch(frame -> "Bob".equals(frame.fields().get("N")) && "1".equals(frame.fields().get("I"))));
        frames.clear();
        disconnect(b);
        assertEquals(List.of(Map.of("I", "0", "N", "Alice", "P", "good", "A", "198.51.100.10", "R", "0")),
                received(a, "+usr").stream().filter(frame -> frame.fields().containsKey("N")).map(frame -> {
                    Map<String, String> fields = new HashMap<>(frame.fields());
                    fields.remove("S");
                    return fields;
                }).toList());
        assertEquals(Map.of("I", "1"), received(a, "+usr").getLast().fields());
        send(a, "peek", "NAME=Match.T.East");
        assertEquals("1", received(a, "peek").getLast().fields().get("COUNT"));
    }

    @Test
    void createdRoomsEnforcePasswordCapacityAndNamesAndCloseWhenEmpty() throws Exception {
        SocketWrapper a = player(1, "198.51.100.10");
        SocketWrapper b = player(2, "198.51.100.90");
        SocketWrapper c = player(3, "198.51.100.30");
        subscribe(a);
        subscribe(b);
        send(a, "room", "NAME=\"Stroke.C.Our Room\" PASS=secret MAX=2");
        assertEquals("1", received(a, "room").getLast().fields().get("COUNT"));
        assertTrue(received(b, "+rom").stream().anyMatch(frame -> "Stroke.C.Our Room".equals(frame.fields().get("N"))));
        send(b, "move", "NAME=\"Stroke.C.Our Room\" PASS=wrong");
        assertEquals(1, received(b, "movepass").size());
        send(b, "move", "NAME=\"Stroke.C.Our Room\" PASS=secret");
        assertEquals("2", received(b, "move").getLast().fields().get("COUNT"));
        send(c, "move", "NAME=\"Stroke.C.Our Room\" PASS=secret");
        assertEquals(1, received(c, "movefull").size());
        send(c, "room", "NAME=\"Stroke.C.Our Room\"");
        assertEquals(1, received(c, "roomdupl").size());
        send(c, "room", "NAME=Stroke.T.Mine");
        assertEquals(1, received(c, "roomname").size());
        send(a, "move", "NAME=Stroke.T.East");
        disconnect(b);
        frames.clear();
        send(a, "sele", "ROOMS=1");
        List<String> listed = received(a, "+rom").stream().map(frame -> frame.fields().get("N")).toList();
        assertEquals(ROOMS, listed);
        send(c, "move", "NAME=\"Stroke.C.Our Room\" PASS=secret");
        assertEquals(1, received(c, "movename").size());
    }

    @Test
    void createdRoomStaysUntilItsSessionIsReported() throws Exception {
        SocketWrapper a = player(1, "198.51.100.10");
        SocketWrapper b = player(2, "198.51.100.90");
        SocketWrapper c = player(3, "198.51.100.30");
        subscribe(c);
        send(a, "room", "NAME=Match.C.Duel");
        send(b, "move", "NAME=Match.C.Duel");
        Map<String, String> session = start(a, b);
        // Both consoles leave the lobby to play
        disconnect(a);
        disconnect(b);
        frames.clear();
        send(c, "sele", "ROOMS=1");
        assertTrue(received(c, "+rom").stream().anyMatch(frame -> "Match.C.Duel".equals(frame.fields().get("N"))));
        SocketWrapper back = player(2, "198.51.100.90");
        subscribe(back);
        send(back, "rank", result(session));
        assertEquals("SUCCESS", received(back, "rank").getLast().fields().get("TITLE"));
        frames.clear();
        send(c, "sele", "ROOMS=1");
        assertEquals(ROOMS, received(c, "+rom").stream().map(frame -> frame.fields().get("N")).toList());
    }

    @Test
    void leaveAndRoomSwitchUpdateBothRoomsAndChatStaysInTheRoom() throws Exception {
        SocketWrapper a = player(1, "198.51.100.10");
        SocketWrapper b = player(2, "198.51.100.90");
        subscribe(a);
        subscribe(b);
        send(a, "move", "NAME=Match.T.East");
        send(b, "move", "NAME=Match.T.East");
        send(b, "move", "NAME=Stroke.T.East");
        frames.clear();
        send(a, "mesg", "TEXT=\"hello %22golfers%22\"");
        assertEquals("hello \"golfers\"", received(a, "+msg").getFirst().fields().get("T"));
        assertTrue(received(b, "+msg").isEmpty());
        send(a, "move", "");
        assertEquals("0", received(a, "move").getLast().fields().get("COUNT"));
        send(b, "peek", "NAME=Match.T.East");
        assertEquals("0", received(b, "peek").getLast().fields().get("COUNT"));
    }

    @Test
    void rejectsUnauthenticatedRoomChangesAndUnknownRooms() throws Exception {
        SocketWrapper player = player(1, "198.51.100.10");
        player.setPersonaEntity(null);
        send(player, "room", "NAME=Match.C.Test");
        assertEquals(1, received(player, "roomnusr").size());
        send(player, "move", "NAME=Match.T.East");
        assertEquals(1, received(player, "movenusr").size());
        player = player(2, "198.51.100.90");
        send(player, "move", "NAME=unlisted");
        assertEquals(1, received(player, "movename").size());
    }

    @Test
    void agreedChallengeRelaysControlAndStartsOneGameWithBothConnections() throws Exception {
        SocketWrapper[] pair = pair();
        SocketWrapper a = pair[0];
        SocketWrapper b = pair[1];
        invite(a, b);
        assertEquals("P3", received(b, "+msg").getFirst().fields().get("F"));
        assertEquals("challenge\nCOUR=4\nGLFR=1\nSHOT=30", received(b, "+msg").getFirst().fields().get("T"));
        assertEquals("accept", received(a, "+msg").getFirst().fields().get("T"));
        // Control messages are not echoed to the sender
        assertEquals(1, received(a, "+msg").size());
        send(b, "chal", "PERS=Alice HOST=0");
        assertTrue(received(a, "+ses").isEmpty());
        assertEquals("play", received(b, "chal").getLast().fields().get("MODE"));
        send(a, "chal", "PERS=Bob HOST=1");
        Map<String, String> host = received(a, "+ses").getFirst().fields();
        Map<String, String> guest = received(b, "+ses").getFirst().fields();
        for (String field : List.of("NAME", "HOST", "P1", "P2", "AUTH", "SEED", "WHEN")) {
            assertEquals(host.get(field), guest.get(field));
        }
        assertEquals("Alice", host.get("HOST"));
        assertEquals("Bob", host.get("FROM"));
        assertEquals("Alice", guest.get("FROM"));
        assertEquals("198.51.100.90", host.get("ADDR"));
        assertEquals("198.51.100.10", guest.get("ADDR"));
        assertTrue(host.get("WHEN").matches("\\$[0-9a-f]{16}"));
        assertTrue(Integer.parseInt(host.get("SEED")) > 0);
        send(a, "chal", "PERS=Bob HOST=1");
        send(b, "chal", "PERS=Alice HOST=0");
        assertEquals(1, received(a, "+ses").size());
        assertEquals(1, received(b, "+ses").size());

        GameEntity game = game(host);
        assertEquals(1, gameRepository.count());
        assertEquals(PS2_TW04, game.getVers());
        assertEquals("Match.T.East", game.getName());
        // The course only travels in the challenge setup; it is kept with the game
        assertEquals("4," + host.get("SEED"), game.getParams());
        assertTrue(game.isStarted());
        List<GameConnectionEntity> connections = gameConnectionRepository.findByGameIdAndEndTimeIsNull(game.getId());
        assertEquals(2, connections.size());
        assertTrue(connections.stream().anyMatch(gc -> gc.isHost() && gc.getPersonaConnection().getId().equals(a.getPersonaConnectionEntity().getId())));
        assertTrue(connections.stream().anyMatch(gc -> !gc.isHost() && gc.getPersonaConnection().getId().equals(b.getPersonaConnectionEntity().getId())));
    }

    @Test
    void sessionSurvivesLobbyDisconnectAndEndsWhenReported() throws Exception {
        SocketWrapper[] pair = pair();
        SocketWrapper a = pair[0];
        SocketWrapper b = pair[1];
        Map<String, String> session = start(a, b);
        assertEquals("198.51.100.90", session.get("ADDR"));
        assertEquals("198.51.100.10", received(b, "+ses").getFirst().fields().get("ADDR"));
        disconnect(a);
        disconnect(b);
        assertNull(gameRepository.findById(game(session).getId()).orElseThrow().getEndTime());

        SocketWrapper back = player(2, "198.51.100.90");
        send(back, "move", "NAME=Match.T.East");
        send(back, "rank", result(session));
        assertEquals("SUCCESS", received(back, "rank").getLast().fields().get("TITLE"));
        GameEntity game = game(session);
        assertNotNull(game.getEndTime());
        // The second report is accepted without counting the match again
        SocketWrapper alice = player(1, "198.51.100.10");
        send(alice, "rank", result(session));
        assertEquals("SUCCESS", received(alice, "rank").getLast().fields().get("TITLE"));
        frames.clear();
        send(alice, "snap", "INDEX=13 CHAN=4 START=0 RANGE=25");
        assertEquals("snap", frames.first().verb());
        assertEquals("2", frames.first().fields().get("COUNT"));
        assertEquals(List.of("Alice", "Bob"), received(alice, "+snp").stream().map(frame -> frame.fields().get("N")).toList());
        assertEquals("4", received(alice, "+snp").getFirst().fields().get("CHAN"));
        send(alice, "snap", "INDEX=13 CHAN=5 FIND=Bob RANGE=1");
        assertEquals("Bob", frames.last().fields().get("N"));
        assertEquals("2", frames.last().fields().get("R"));
        send(alice, "snap", "INDEX=13 CHAN=99");
        assertEquals("snapdata", frames.last().verb());
        // List 0 is the overall board, which the STATISTICS screen opens for rank points
        frames.clear();
        send(alice, "snap", "INDEX=0 CHAN=4 START=0 RANGE=25");
        assertEquals(List.of("Alice", "Bob"), received(alice, "+snp").stream().map(frame -> frame.fields().get("N")).toList());
        // List 18 ranks match wins alone
        frames.clear();
        send(alice, "snap", "INDEX=18 CHAN=4 START=0 RANGE=25");
        assertEquals("1", frames.first().fields().get("COUNT"));
        assertEquals(Map.of("N", "Alice", "R", "1", "P", "1"), Map.of("N", frames.last().fields().get("N"),
                "R", frames.last().fields().get("R"), "P", frames.last().fields().get("P")));
    }


    @Test
    void cannotStartWithoutInvitationAcceptanceOrWithTheWrongHostRole() throws Exception {
        SocketWrapper[] pair = pair();
        SocketWrapper a = pair[0];
        SocketWrapper b = pair[1];
        send(a, "chal", "PERS=Bob HOST=1");
        assertEquals(1, received(a, "chalnrom").size());
        send(a, "mesg", "PRIV=Bob ATTR=3 TEXT=challenge");
        send(a, "chal", "PERS=Bob HOST=1");
        assertEquals(1, received(a, "chalmaut").size());
        send(a, "mesg", "PRIV=Bob ATTR=3 TEXT=accept");
        assertEquals(1, received(a, "mesgmaut").size());
        send(b, "mesg", "PRIV=Alice ATTR=3 TEXT=accept");
        send(b, "chal", "PERS=Alice HOST=1");
        assertEquals(1, received(b, "chalmaut").size());
        assertTrue(received(a, "+ses").isEmpty());
        assertEquals(0, gameRepository.count());
    }

    @Test
    void cancellationRoomLeaveAndDisconnectInvalidateAPendingAgreement() throws Exception {
        SocketWrapper[] pair = pair();
        SocketWrapper a = pair[0];
        SocketWrapper b = pair[1];
        invite(a, b);
        send(b, "mesg", "PRIV=Alice ATTR=3 TEXT=decline");
        send(a, "chal", "PERS=Bob HOST=1");
        assertTrue(received(a, "+ses").isEmpty());
        invite(a, b);
        send(b, "move", "NAME=Stroke.T.East");
        assertEquals("revoke", received(a, "+msg").getLast().fields().get("T"));
        send(b, "move", "NAME=Match.T.East");
        invite(a, b);
        disconnect(b);
        assertEquals("revoke", received(a, "+msg").getLast().fields().get("T"));
        send(a, "chal", "PERS=Bob HOST=1");
        assertTrue(received(a, "+ses").isEmpty());
        assertEquals(0, gameRepository.count());
    }

    @Test
    void reportedMatchAllowsAnotherAndAReportNeedsItsOwnToken() throws Exception {
        SocketWrapper[] pair = pair();
        SocketWrapper a = pair[0];
        SocketWrapper b = pair[1];
        Map<String, String> first = rewind(start(a, b));
        // While the first match is on, neither player can be challenged
        send(b, "mesg", "PRIV=Alice ATTR=3 TEXT=challenge");
        assertEquals(1, received(b, "mesgingm").size());
        send(a, "rank", result(first));
        assertEquals("SUCCESS", received(a, "rank").getLast().fields().get("TITLE"));
        // The reported match ended the challenge, so a new one can start
        Map<String, String> second = start(b, a);
        assertEquals(2, received(a, "+ses").size());
        assertNotEquals(first.get("AUTH"), second.get("AUTH"));
        assertEquals("Bob", second.get("HOST"));
        // The first match reported again with the live session's token is refused, with its own (expired) token it is accepted
        send(b, "rank", result(first).replace(first.get("AUTH"), second.get("AUTH")));
        assertEquals(1, received(b, "rankmaut").size());
        send(b, "rank", result(first));
        assertEquals("SUCCESS", received(b, "rank").getLast().fields().get("TITLE"));
        assertNull(game(second).getEndTime());
    }

    @Test
    void cancelledMatchAllowsAnother() throws Exception {
        SocketWrapper[] pair = pair();
        SocketWrapper a = pair[0];
        SocketWrapper b = pair[1];
        Map<String, String> first = rewind(start(a, b));
        send(a, "chal", "PERS=*");
        assertNotNull(game(first).getEndTime());
        Map<String, String> second = start(b, a);
        assertNotEquals(first.get("AUTH"), second.get("AUTH"));
        // The cancelled match still counts once it is reported with its own token
        send(a, "rank", result(first));
        assertEquals("SUCCESS", received(a, "rank").getLast().fields().get("TITLE"));
        assertNull(game(second).getEndTime());
    }

    @Test
    void challengesCannotTargetSelfAnotherRoomOrSomeoneInAMatch() throws Exception {
        SocketWrapper[] pair = pair();
        SocketWrapper a = pair[0];
        SocketWrapper b = pair[1];
        send(a, "mesg", "PRIV=Alice ATTR=3 TEXT=challenge");
        assertEquals(1, received(a, "mesgnrom").size());
        send(b, "move", "NAME=Stroke.T.East");
        send(a, "mesg", "PRIV=Bob ATTR=3 TEXT=challenge");
        assertEquals(2, received(a, "mesgnrom").size());
        send(b, "move", "NAME=Match.T.East");
        start(a, b);
        SocketWrapper c = player(3, "198.51.100.30");
        subscribe(c);
        send(c, "move", "NAME=Match.T.East");
        send(c, "mesg", "PRIV=Bob ATTR=3 TEXT=challenge");
        assertEquals(1, received(c, "mesgingm").size());
    }

    @Test
    void messengerChallengeOutsideARoomTakesItsModeFromTheSetup() throws Exception {
        SocketWrapper[] pair = pair();
        SocketWrapper a = pair[0];
        SocketWrapper b = pair[1];
        send(a, "move", "");
        send(b, "move", "NAME=Match.T.East");
        // Outside a shared room only MFLG tells Match from Stroke Play
        send(a, "mesg", "PRIV=Bob ATTR=3 TEXT=challenge%0aCOUR%3d5");
        assertEquals(1, received(a, "mesgnrom").size());
        send(a, "mesg", "PRIV=Bob ATTR=3 TEXT=challenge%0aCOUR%3d5%0aMFLG%3d34");
        send(b, "mesg", "PRIV=Alice ATTR=3 TEXT=accept");
        send(a, "chal", "PERS=Bob HOST=1");
        send(b, "chal", "PERS=Alice HOST=0");
        Map<String, String> session = received(a, "+ses").getFirst().fields();
        assertEquals("Stroke.B.Buddy", session.get("NAME"));
        assertEquals(session.get("AUTH"), received(b, "+ses").getFirst().fields().get("AUTH"));
        GameEntity game = game(session);
        assertEquals("Stroke.B.Buddy", game.getName());
        assertNull(game.getRoomId());
        assertEquals("5," + session.get("SEED") + ",34", game.getParams());
        send(a, "rank", result(session));
        assertEquals("SUCCESS", received(a, "rank").getLast().fields().get("TITLE"));
        // A ranked Stroke Play win
        frames.clear();
        send(a, "snap", "INDEX=15 CHAN=4 START=0 RANGE=25");
        assertEquals("1", frames.first().fields().get("COUNT"));
        assertEquals("Alice", frames.last().fields().get("N"));
    }

    @Test
    void unrankedMatchIsRecordedButNotCounted() throws Exception {
        SocketWrapper[] pair = pair();
        SocketWrapper a = pair[0];
        SocketWrapper b = pair[1];
        send(a, "mesg", "PRIV=Bob ATTR=3 TEXT=challenge%0aCOUR%3d4%0aMFLG%3d1");
        send(b, "mesg", "PRIV=Alice ATTR=3 TEXT=accept");
        send(a, "chal", "PERS=Bob HOST=1");
        send(b, "chal", "PERS=Alice HOST=0");
        Map<String, String> session = received(a, "+ses").getFirst().fields();
        send(a, "rank", result(session));
        assertEquals("SUCCESS", received(a, "rank").getLast().fields().get("TITLE"));
        assertNotNull(gameRepository.findById(game(session).getId()).orElseThrow().getEndTime());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM stats.TW_GAME_REPORT", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM stats.TW_PERSONA_STATS", Integer.class));
        frames.clear();
        send(a, "snap", "INDEX=0 CHAN=4 START=0 RANGE=25");
        assertTrue(received(a, "+snp").isEmpty());
    }

    @Test
    void sharedRouterUsesTheReportedLanAddressButNeverThePcsx2VirtualAddress() throws Exception {
        SocketWrapper a = player(1, "203.0.113.1");
        SocketWrapper b = player(2, "203.0.113.1");
        send(a, "addr", "ADDR=192.168.1.10 PORT=3658");
        send(b, "addr", "ADDR=192.168.1.90 PORT=3658");
        send(a, "move", "NAME=Match.T.East");
        send(b, "move", "NAME=Match.T.East");
        Map<String, String> session = rewind(start(a, b));
        assertEquals("192.168.1.90", session.get("ADDR"));
        assertEquals("192.168.1.10", received(b, "+ses").getLast().fields().get("ADDR"));
        send(a, "chal", "PERS=*");
        send(b, "addr", "ADDR=192.0.2.100 PORT=3658");
        assertEquals("203.0.113.1", start(a, b).get("ADDR"));
    }
}
