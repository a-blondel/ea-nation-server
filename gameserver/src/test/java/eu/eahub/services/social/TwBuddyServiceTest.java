package eu.eahub.services.social;

import eu.eahub.config.GameServerConfig;
import eu.eahub.dirtysdk.LobbyTagField;
import eu.eahub.dto.BuddySocketWrapper;
import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.PersonaConnectionEntity;
import eu.eahub.repositories.buddy.BuddyRepository;
import eu.eahub.repositories.core.GameConnectionRepository;
import eu.eahub.repositories.core.PersonaRepository;
import eu.eahub.services.TwTestConfig;
import eu.eahub.services.server.SocketManager;
import eu.eahub.steps.SocketWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static eu.eahub.services.server.GameServerService.PS2_TW04;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TwBuddyServiceTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PersonaRepository personaRepository;
    @Autowired
    private BuddyRepository buddyRepository;
    @Autowired
    private GameConnectionRepository gameConnectionRepository;
    private final List<Frame> frames = new ArrayList<>();
    private SocketManager socketManager;
    private TwBuddyService messenger;

    private record Frame(Socket socket, String verb, Map<String, String> tags) {
    }

    @BeforeEach
    void setup() {
        TwTestConfig.reset(jdbc, "Alice", "Bob");
        SocketWriter writer = mock(SocketWriter.class);
        doAnswer(c -> frames.add(new Frame(c.getArgument(0), c.getArgument(1), Map.copyOf(c.<Map<String, String>>getArgument(2)))))
                .when(writer).writeTagFields(any(), anyString(), anyMap());
        doAnswer(c -> frames.add(new Frame(c.getArgument(0), c.getArgument(1), LobbyTagField.decode(c.getArgument(2)))))
                .when(writer).writeText(any(), anyString(), anyString());
        GameServerConfig.GameServer server = new GameServerConfig.GameServer();
        server.setName(PS2_TW04);
        server.setBuddyProduct("tiger-ps2-2004");
        GameServerConfig config = new GameServerConfig();
        config.setServers(List.of(server));
        socketManager = new SocketManager(gameConnectionRepository);
        messenger = new TwBuddyService(config, writer, socketManager, buddyRepository, personaRepository, new ChatHistoryService());
    }

    /**
     * A lobby connection with a persona and its lobby key
     */
    private SocketWrapper lobby(long personaId, String lkey) {
        Socket socket = mock(Socket.class);
        socketManager.addSocket("/192.0.2." + personaId + ":1000", socket);
        SocketWrapper wrapper = socketManager.getSocketWrapper("/192.0.2." + personaId + ":1000");
        wrapper.setPersonaEntity(personaRepository.findById(personaId).orElseThrow());
        wrapper.setLkey(lkey);
        PersonaConnectionEntity connection = new PersonaConnectionEntity();
        connection.setVers(PS2_TW04);
        wrapper.setPersonaConnectionEntity(connection);
        return wrapper;
    }

    private BuddySocketWrapper buddy(String identifier) {
        socketManager.addBuddySocket(identifier, mock(Socket.class));
        return socketManager.getAllBuddySocketWrappers().stream().filter(w -> w.getIdentifier().equals(identifier)).findFirst().orElseThrow();
    }

    private void auth(BuddySocketWrapper wrapper, String lkey) {
        SocketData login = new SocketData("AUTH", "PROD=tiger-ps2-2004 LKEY=" + lkey, null);
        assertEquals(PS2_TW04, messenger.authVers(login).orElseThrow());
        messenger.auth(wrapper.getSocket(), login, wrapper, PS2_TW04);
    }

    @Test
    void authenticatesListsPresenceMessagesBlockingAndDisconnect() {
        lobby(1, "a".repeat(64));
        lobby(2, "b".repeat(64));
        assertTrue(messenger.authVers(new SocketData("AUTH", "PROD=nhl-ps2-2008", null)).isEmpty());
        BuddySocketWrapper bad = buddy("/192.0.2.9:5000");
        auth(bad, "guessed");
        assertEquals("AUTHauth", frames.getLast().verb());
        BuddySocketWrapper a = buddy("/192.0.2.1:5000");
        BuddySocketWrapper b = buddy("/192.0.2.2:5000");
        auth(a, "a".repeat(64));
        auth(b, "b".repeat(64));
        messenger.pset(b.getSocket(), new SocketData("PSET", "SHOW=CHAT STAT=hello", null), b);
        messenger.radd(a.getSocket(), new SocketData("RADD", "ID=101 LIST=B USER=bob GROUP=Friends", null), a);
        assertTrue(frames.stream().anyMatch(f -> f.verb().equals("RADD") && f.tags().get("ID").equals("101")
                && f.tags().get("FUSR").equals("Bob")));
        assertEquals("CHAT", frames.getLast().tags().get("SHOW"));
        frames.clear();
        messenger.rget(a.getSocket(), new SocketData("RGET", "ID=1 LIST=B PRES=Y", null), a);
        assertEquals(List.of("RGET", "ROST", "PGET"), frames.stream().map(Frame::verb).toList());
        assertEquals("Friends", frames.get(1).tags().get("GROUP"));
        messenger.send(a.getSocket(), new SocketData("SEND", "ID=9 TYPE=C USER=Bob BODY=hello", null), a);
        assertTrue(frames.stream().anyMatch(f -> f.socket() == b.getSocket() && f.verb().equals("RECV") && f.tags().get("USER").equals("Alice")));
        messenger.radd(b.getSocket(), new SocketData("RADD", "ID=102 LIST=I USER=Alice", null), b);
        assertEquals("DISC", frames.getLast().tags().get("SHOW"));
        messenger.send(a.getSocket(), new SocketData("SEND", "ID=10 USER=Bob BODY=blocked", null), a);
        assertEquals("SENDuser", frames.getLast().verb());
        messenger.rdel(b.getSocket(), new SocketData("RDEL", "ID=103 LIST=I USER=Alice", null), b);
        assertEquals("CHAT", frames.getLast().tags().get("SHOW"));
        messenger.onBuddyDisconnect(b);
        assertEquals("DISC", frames.getLast().tags().get("SHOW"));
        // Lists are kept on the shared buddy table
        assertEquals(1, buddyRepository.count());
    }

    @Test
    void closingTheLobbyConnectionInvalidatesItsKey() {
        SocketWrapper aliceLobby = lobby(1, "a".repeat(64));
        BuddySocketWrapper a = buddy("/192.0.2.1:5000");
        auth(a, "a".repeat(64));
        assertEquals(Map.of(), frames.getLast().tags());
        socketManager.removeSocket(aliceLobby.getIdentifier());
        messenger.onDisconnect(aliceLobby);
        messenger.rget(a.getSocket(), new SocketData("RGET", "ID=1 LIST=B", null), a);
        assertEquals("RGETauth", frames.getLast().verb());
    }
}
