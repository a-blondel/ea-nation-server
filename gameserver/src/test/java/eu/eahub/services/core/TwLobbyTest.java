package eu.eahub.services.core;

import eu.eahub.config.GameServerConfig;
import eu.eahub.config.TwConfig;
import eu.eahub.dirtysdk.LobbyTagField;
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
import eu.eahub.services.TwTestConfig;
import eu.eahub.services.server.GameServerService;
import eu.eahub.services.server.SocketManager;
import eu.eahub.services.social.ChatHistoryService;
import eu.eahub.services.stats.FifaStatsService;
import eu.eahub.services.stats.MohhStatsService;
import eu.eahub.services.stats.NfsRankService;
import eu.eahub.services.stats.NfsStatsService;
import eu.eahub.services.stats.NhlStatsService;
import eu.eahub.services.stats.StatsService;
import eu.eahub.services.stats.TwNewsService;
import eu.eahub.services.stats.TwStatsService;
import eu.eahub.services.stats.TwTournamentService;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static eu.eahub.services.server.GameServerService.PS2_TW04;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The TW04 lobby through the shared services: rooms, chat, challenges, sessions and their results
 */
@DataJpaTest
@Import({RoomService.class, GameService.class, AuthService.class, AccountService.class, UserSetService.class, PersonaService.class,
        StatsService.class, SocketManager.class, GameServerService.class, GameUtils.class, PersonaUtils.class, TwStatsService.class,
        TwTournamentService.class, TwChallengeService.class, ChatHistoryService.class, GameServerConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TwLobbyTest {

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
    @MockitoBean
    private TwNewsService twNewsService;
    // Stands in for the relay
    @MockitoBean
    private GameSessionListener relay;

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
    @Autowired
    private List<ConnectionListener> connectionListeners;
    private final List<Frame> frames = new ArrayList<>();

    @TestConfiguration
    static class Beans {
        @Bean
        TwConfig twConfig() {
            return new TwConfig();
        }
    }

    private record Frame(Socket socket, String verb, Map<String, String> fields, String text) {
    }

    @BeforeEach
    void setup() {
        TwTestConfig.reset(jdbc, "Alice", "Bob", "Charlie");
        // The TW04 server as configured in application.yml, enabled and alone on its port
        GameServerConfig.GameServer tw04 = gameServerConfig.getServers().stream().filter(s -> s.getName().equals(PS2_TW04)).findFirst().orElseThrow();
        tw04.setEnabled(true);
        gameServerConfig.setServers(List.of(tw04));
        roomService.generateRooms();
        when(props.getTcpHost()).thenReturn("127.0.0.1");
        when(props.getTcpBuddyPort()).thenReturn(13504);
        when(twNewsService.news(PS2_TW04)).thenReturn("Welcome\n");
        when(relay.beforeStart(any(), any(), any())).thenReturn(Map.of());
        doAnswer(c -> frames.add(new Frame(c.getArgument(0), c.getArgument(1), Map.copyOf(c.<Map<String, String>>getArgument(2)), null)))
                .when(socketWriter).writeTagFields(any(), anyString(), anyMap());
        doAnswer(c -> frames.add(new Frame(c.getArgument(0), c.getArgument(1), LobbyTagField.decode(c.getArgument(2)), c.getArgument(2))))
                .when(socketWriter).writeText(any(), anyString(), anyString());
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
        PersonaEntity persona = personaRepository.findById(personaId).orElseThrow();
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
    private void send(SocketWrapper p, String verb, String body) {
        Socket s = p.getSocket();
        SocketData d = new SocketData(verb, body + "\0", null);
        switch (verb) {
            case "sele" -> authService.sele(s, d, p);
            case "addr" -> authService.addr(s, d, p);
            case "news" -> authService.news(s, d);
            case "room" -> roomService.room(s, d, p);
            case "move" -> roomService.move(s, d, p);
            case "peek" -> roomService.peek(s, d, p);
            case "mesg" -> roomService.mesg(s, d, p);
            case "chal" -> gameService.chal(s, d, p);
            case "rank" -> statsService.rank(s, d, p);
            case "snap" -> statsService.snap(s, d, p);
            case "cusr" -> statsService.cusr(s, d, p);
            case "user" -> accountService.user(s, d, p);
            case "onln" -> userSetService.onln(s, d, p);
            default -> throw new IllegalArgumentException(verb);
        }
    }

    /**
     * Close a lobby connection as TcpSocketThread does
     */
    private void disconnect(SocketWrapper p) {
        when(p.getSocket().isClosed()).thenReturn(true);
        socketManager.removeSocket(p.getIdentifier());
        connectionListeners.forEach(listener -> listener.onDisconnect(p));
        gameService.endGameConnection(p);
        personaService.endPersonaConnection(p);
    }

    private List<Frame> received(SocketWrapper p, String verb) {
        return frames.stream().filter(f -> f.socket() == p.getSocket() && f.verb().equals(verb)).toList();
    }

    private void subscribe(SocketWrapper p) {
        send(p, "sele", "ROOMS=1 GAMES=1 USERS=1 RANKS=1 MESGS=1");
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
     */
    private String result(Map<String, String> session) {
        long epoch = Long.parseLong(session.get("WHEN").substring(1), 16);
        Map<String, String> f = new HashMap<>();
        for (int side = 0; side < 2; side++) {
            f.put("DONE" + side, "1");
            f.put("QUIT" + side, "0");
            f.put("HOLES" + side, "18");
            f.put("STROKES" + side, side == 0 ? "70" : "72");
            f.put("SCORE" + side, side == 0 ? "2" : "-2");
        }
        f.put("WHEN", LocalDateTime.ofEpochSecond(epoch, 0, ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy.M.d H:mm:ss")));
        f.put("AUTH", session.get("AUTH"));
        return LobbyTagField.encode(f).replace('\n', ' ');
    }

    private GameEntity game(Map<String, String> session) {
        long epoch = Long.parseLong(session.get("WHEN").substring(1), 16);
        LocalDateTime start = LocalDateTime.ofEpochSecond(epoch, 0, ZoneOffset.UTC).atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        return gameRepository.findAll().stream().filter(g -> g.getStartTime().equals(start)).findFirst().orElseThrow();
    }

    @Test
    void subscriptionsPushTheConfiguredRoomsAndRespectUnsubscribe() throws Exception {
        SocketWrapper p = player(1, "198.51.100.10");
        subscribe(p);
        List<Frame> rooms = received(p, "+rom");
        assertEquals(ROOMS, rooms.stream().map(f -> f.fields().get("N")).toList());
        assertEquals(10, rooms.stream().map(f -> f.fields().get("I")).distinct().count());
        send(p, "sele", "ROOMS=0");
        frames.clear();
        // The real client can send a leftover PASS even for a public room
        send(p, "move", "NAME=Stroke.T.East PASS=leftover");
        assertEquals("1", received(p, "move").getLast().fields().get("COUNT"));
        assertTrue(received(p, "+rom").isEmpty());
        send(p, "news", "NAME=0");
        assertEquals("127.0.0.1:13504\n", received(p, "news").getLast().text());
        send(p, "news", "NAME=1");
        assertEquals("Welcome\n", received(p, "news").getLast().text());
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
        assertTrue(received(a, "+usr").stream().anyMatch(f -> "Bob".equals(f.fields().get("N")) && "1".equals(f.fields().get("I"))));
        frames.clear();
        disconnect(b);
        assertEquals(List.of(Map.of("I", "0", "N", "Alice", "P", "good", "A", "198.51.100.10", "R", "0")),
                received(a, "+usr").stream().filter(f -> f.fields().containsKey("N")).map(f -> {
                    Map<String, String> m = new HashMap<>(f.fields());
                    m.remove("S");
                    return m;
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
        assertTrue(received(b, "+rom").stream().anyMatch(f -> "Stroke.C.Our Room".equals(f.fields().get("N"))));
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
        List<String> listed = received(a, "+rom").stream().map(f -> f.fields().get("N")).toList();
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
        assertTrue(received(c, "+rom").stream().anyMatch(f -> "Match.C.Duel".equals(f.fields().get("N"))));
        SocketWrapper back = player(2, "198.51.100.90");
        subscribe(back);
        send(back, "rank", result(session));
        assertEquals("SUCCESS", received(back, "rank").getLast().fields().get("TITLE"));
        frames.clear();
        send(c, "sele", "ROOMS=1");
        assertEquals(ROOMS, received(c, "+rom").stream().map(f -> f.fields().get("N")).toList());
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
        SocketWrapper p = player(1, "198.51.100.10");
        p.setPersonaEntity(null);
        send(p, "room", "NAME=Match.C.Test");
        assertEquals(1, received(p, "roomnusr").size());
        send(p, "move", "NAME=Match.T.East");
        assertEquals(1, received(p, "movenusr").size());
        p = player(2, "198.51.100.90");
        send(p, "move", "NAME=unlisted");
        assertEquals(1, received(p, "movename").size());
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
        when(relay.beforeStart(any(), any(), any())).thenReturn(Map.of(1L, "203.0.113.9:3659:3658", 2L, "203.0.113.9:3660:3658"));
        Map<String, String> session = start(a, b);
        assertEquals("203.0.113.9:3659:3658", session.get("ADDR"));
        assertEquals("203.0.113.9:3660:3658", received(b, "+ses").getFirst().fields().get("ADDR"));
        disconnect(a);
        disconnect(b);
        assertNull(gameRepository.findById(game(session).getId()).orElseThrow().getEndTime());
        verify(relay, never()).onEnd(any(), any());

        SocketWrapper back = player(2, "198.51.100.90");
        send(back, "move", "NAME=Match.T.East");
        send(back, "rank", result(session));
        assertEquals("SUCCESS", received(back, "rank").getLast().fields().get("TITLE"));
        GameEntity game = game(session);
        assertNotNull(game.getEndTime());
        verify(relay).onEnd(any(), eq(GameSessionListener.EndReason.REPORTED));
        // The second report is accepted without counting the match again
        SocketWrapper alice = player(1, "198.51.100.10");
        send(alice, "rank", result(session));
        assertEquals("SUCCESS", received(alice, "rank").getLast().fields().get("TITLE"));
        frames.clear();
        send(alice, "snap", "INDEX=13 CHAN=4 START=0 RANGE=25");
        assertEquals("snap", frames.getFirst().verb());
        assertEquals("2", frames.getFirst().fields().get("COUNT"));
        assertEquals(List.of("Alice", "Bob"), received(alice, "+snp").stream().map(f -> f.fields().get("N")).toList());
        assertEquals("4", received(alice, "+snp").getFirst().fields().get("CHAN"));
        send(alice, "snap", "INDEX=13 CHAN=5 FIND=Bob RANGE=1");
        assertEquals("Bob", frames.getLast().fields().get("N"));
        assertEquals("2", frames.getLast().fields().get("R"));
        send(alice, "snap", "INDEX=13 CHAN=99");
        assertEquals("snapdata", frames.getLast().verb());
    }

    @Test
    void relayRefusalCancelsTheSessionWithoutFallingBackToADirectMatch() throws Exception {
        SocketWrapper[] pair = pair();
        when(relay.beforeStart(any(), any(), any())).thenThrow(new IllegalStateException("busy"));
        invite(pair[0], pair[1]);
        send(pair[0], "chal", "PERS=Bob HOST=1");
        send(pair[1], "chal", "PERS=Alice HOST=0");
        assertTrue(received(pair[0], "+ses").isEmpty());
        assertTrue(received(pair[1], "+ses").isEmpty());
        assertEquals(1, received(pair[0], "chalunav").size());
        assertEquals(1, received(pair[1], "chalunav").size());
        assertTrue(gameRepository.findAll().stream().allMatch(g -> g.getEndTime() != null));
        verify(relay).onEnd(any(), eq(GameSessionListener.EndReason.CANCELLED));
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
    void returningToTheLobbyAllowsAnotherMatchAndAReportNeedsItsOwnToken() throws Exception {
        SocketWrapper[] pair = pair();
        SocketWrapper a = pair[0];
        SocketWrapper b = pair[1];
        Map<String, String> first = start(a, b);
        send(a, "chal", "PERS=*");
        send(b, "chal", "PERS=*");
        verify(relay).onEnd(any(), eq(GameSessionListener.EndReason.CANCELLED));
        // Back-to-back sessions would share a start second
        Thread.sleep(1100);
        Map<String, String> second = start(b, a);
        assertEquals(2, received(a, "+ses").size());
        assertNotEquals(first.get("AUTH"), second.get("AUTH"));
        assertEquals("Bob", second.get("HOST"));
        // The first match reported with the live session's token is refused, with its own (expired) token it counts
        send(a, "rank", result(first).replace(first.get("AUTH"), second.get("AUTH")));
        assertEquals(1, received(a, "rankmaut").size());
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
    void sharedRouterUsesTheReportedLanAddressButNeverThePcsx2VirtualAddress() throws Exception {
        SocketWrapper a = player(1, "203.0.113.1");
        SocketWrapper b = player(2, "203.0.113.1");
        send(a, "addr", "ADDR=192.168.1.10 PORT=3658");
        send(b, "addr", "ADDR=192.168.1.90 PORT=3658");
        send(a, "move", "NAME=Match.T.East");
        send(b, "move", "NAME=Match.T.East");
        Map<String, String> session = start(a, b);
        assertEquals("192.168.1.90", session.get("ADDR"));
        assertEquals("192.168.1.10", received(b, "+ses").getLast().fields().get("ADDR"));
        send(a, "chal", "PERS=*");
        send(b, "addr", "ADDR=192.0.2.100 PORT=3658");
        Thread.sleep(1100);
        assertEquals("203.0.113.1", start(a, b).get("ADDR"));
    }

    @Test
    void golferUploadBelongsToTheAuthenticatedPersonaAndIsReturnedAsBinary() throws Exception {
        SocketWrapper[] pair = pair();
        SocketWrapper a = pair[0];
        SocketWrapper b = pair[1];
        send(a, "cusr", "CMD=whomi PERS=Bob CRPIN=\u0080À\u0080!");
        send(b, "user", "PERS=Alice");
        assertEquals("$80c08021", received(b, "user").getLast().fields().get("CRPIN"));
        send(a, "user", "PERS=Bob");
        assertFalse(received(a, "user").getLast().fields().containsKey("CRPIN"));
        // A profile shows the same standing as the room roster
        send(a, "peek", "NAME=Match.T.East");
        send(a, "onln", "PERS=Bob");
        Map<String, String> profile = received(a, "onln").getLast().fields();
        assertEquals("0", profile.get("R"));
        assertEquals(received(a, "+usr").stream().filter(f -> "Bob".equals(f.fields().get("N"))).toList().getLast().fields().get("S"), profile.get("S"));
    }
}
