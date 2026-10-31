package eu.eahub.services.social;

import eu.eahub.dto.BuddySocketWrapper;
import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.PersonaConnectionEntity;
import eu.eahub.repositories.buddy.BuddyRepository;
import eu.eahub.repositories.core.GameConnectionRepository;
import eu.eahub.repositories.core.PersonaRepository;
import eu.eahub.services.TwFrames;
import eu.eahub.services.TwTestConfig;
import eu.eahub.services.server.SocketManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.net.Socket;
import java.util.List;
import java.util.Map;

import static eu.eahub.services.server.GameServerService.PS2_TW04;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DataJpaTest
@ContextConfiguration(classes = TwTestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TwBuddyServiceTest {

    private static final String[] PERSONAS = {"Alice", "Bob"};

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PersonaRepository personaRepository;
    @Autowired
    private BuddyRepository buddyRepository;
    @Autowired
    private GameConnectionRepository gameConnectionRepository;
    private final TwFrames frames = new TwFrames();
    private SocketManager socketManager;
    private TwBuddyService messenger;

    @BeforeEach
    void setup() {
        TwTestConfig.reset(jdbc, PERSONAS);
        socketManager = new SocketManager(gameConnectionRepository);
        messenger = new TwBuddyService(frames.writer(), socketManager, buddyRepository, personaRepository, new TwChatHistoryService());
    }

    /**
     * A lobby connection with a persona and its lobby key
     */
    private SocketWrapper lobby(long personaId, String lkey) {
        Socket socket = mock(Socket.class);
        socketManager.addSocket("/192.0.2." + personaId + ":1000", socket);
        SocketWrapper wrapper = socketManager.getSocketWrapper("/192.0.2." + personaId + ":1000");
        // Personas are numbered in the order TwTestConfig.reset created them
        wrapper.setPersonaEntity(personaRepository.findByPers(PERSONAS[(int) personaId - 1]).orElseThrow());
        wrapper.setLkey(lkey);
        PersonaConnectionEntity connection = new PersonaConnectionEntity();
        connection.setVers(PS2_TW04);
        wrapper.setPersonaConnectionEntity(connection);
        return wrapper;
    }

    private BuddySocketWrapper buddy(String identifier) {
        socketManager.addBuddySocket(identifier, mock(Socket.class));
        return socketManager.getAllBuddySocketWrappers().stream().filter(wrapper -> wrapper.getIdentifier().equals(identifier)).findFirst().orElseThrow();
    }

    private void auth(BuddySocketWrapper wrapper, String lkey) {
        SocketData login = new SocketData("AUTH", "PROD=tiger-ps2-2004 LKEY=" + lkey, null);
        assertEquals(PS2_TW04, messenger.authVers(login).orElseThrow());
        messenger.auth(wrapper.getSocket(), login, wrapper, PS2_TW04);
    }

    @Test
    void messengerSession() {
        lobby(1, "a".repeat(64));
        lobby(2, "b".repeat(64));
        assertTrue(messenger.authVers(new SocketData("AUTH", "PROD=nhl-ps2-2008", null)).isEmpty());
        BuddySocketWrapper bad = buddy("/192.0.2.9:5000");
        auth(bad, "guessed");
        assertEquals("AUTHauth", frames.last().verb());
        BuddySocketWrapper alice = buddy("/192.0.2.1:5000");
        BuddySocketWrapper bob = buddy("/192.0.2.2:5000");
        auth(alice, "a".repeat(64));
        auth(bob, "b".repeat(64));
        messenger.pset(bob.getSocket(), new SocketData("PSET", "SHOW=CHAT STAT=hello", null), bob);
        messenger.radd(alice.getSocket(), new SocketData("RADD", "ID=101 LIST=B USER=bob GROUP=Friends", null), alice);
        assertTrue(frames.all().stream().anyMatch(frame -> frame.verb().equals("RADD") && frame.fields().get("ID").equals("101")
                && frame.fields().get("FUSR").equals("Bob")));
        assertEquals("CHAT", frames.last().fields().get("SHOW"));
        frames.clear();
        messenger.rget(alice.getSocket(), new SocketData("RGET", "ID=1 LIST=B PRES=Y", null), alice);
        assertEquals(List.of("RGET", "ROST", "PGET"), frames.all().stream().map(TwFrames.Frame::verb).toList());
        assertEquals("Friends", frames.all().get(1).fields().get("GROUP"));
        messenger.send(alice.getSocket(), new SocketData("SEND", "ID=9 TYPE=C USER=Bob BODY=hello", null), alice);
        assertTrue(frames.all().stream().anyMatch(frame -> frame.socket() == bob.getSocket() && frame.verb().equals("RECV")
                && frame.fields().get("USER").equals("Alice")));
        messenger.radd(bob.getSocket(), new SocketData("RADD", "ID=102 LIST=I USER=Alice", null), bob);
        assertEquals("DISC", frames.last().fields().get("SHOW"));
        messenger.send(alice.getSocket(), new SocketData("SEND", "ID=10 USER=Bob BODY=blocked", null), alice);
        assertEquals("SENDuser", frames.last().verb());
        messenger.rdel(bob.getSocket(), new SocketData("RDEL", "ID=103 LIST=I USER=Alice", null), bob);
        assertEquals("CHAT", frames.last().fields().get("SHOW"));
        messenger.endConnection(bob);
        assertEquals("DISC", frames.last().fields().get("SHOW"));
        // Lists are kept on the shared buddy table
        assertEquals(1, buddyRepository.count());
    }

    @Test
    void lobbyDisconnect() {
        SocketWrapper aliceLobby = lobby(1, "a".repeat(64));
        BuddySocketWrapper alice = buddy("/192.0.2.1:5000");
        auth(alice, "a".repeat(64));
        assertEquals(Map.of(), frames.last().fields());
        socketManager.removeSocket(aliceLobby.getIdentifier());
        messenger.endLobbyConnection(aliceLobby);
        messenger.rget(alice.getSocket(), new SocketData("RGET", "ID=1 LIST=B", null), alice);
        assertEquals("RGETauth", frames.last().verb());
    }
}
