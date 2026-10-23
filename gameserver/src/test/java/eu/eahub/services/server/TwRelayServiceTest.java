package eu.eahub.services.server;

import eu.eahub.config.TwConfig;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.services.core.GameSessionListener;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static eu.eahub.services.server.GameServerService.PS2_TW04;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TwRelayServiceTest {

    // CommUDP control packets as DSOCKF.IRX writes them: two little-endian words
    private static final byte[] POKE = {5, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] INIT = {1, 0, 0, 0, 0x2a, 0x17, 0, 0};
    private static final byte[] ACK = {2, 0, 0, 0, 0x2a, 0x17, 0, 0};

    private DatagramSocket client(String address) throws Exception {
        DatagramSocket s = new DatagramSocket(new InetSocketAddress(address, 0));
        s.setSoTimeout(300);
        return s;
    }

    private TwConfig config() throws Exception {
        TwConfig c = new TwConfig();
        c.getRelay().setEnabled(true);
        c.getRelay().setBindAddress("127.0.0.1");
        c.getRelay().setPublicAddress("127.0.0.1");
        try (DatagramSocket free = client("127.0.0.1")) {
            c.getRelay().setPort(free.getLocalPort());
        }
        return c;
    }

    private void send(DatagramSocket s, TwConfig c, byte[] data) throws Exception {
        s.send(new DatagramPacket(data, data.length, new InetSocketAddress("127.0.0.1", c.getRelay().getPort())));
    }

    private void send(DatagramSocket s, TwConfig c, String data) throws Exception {
        send(s, c, data.getBytes(StandardCharsets.ISO_8859_1));
    }

    private byte[] receive(DatagramSocket s, int relayPort) throws Exception {
        DatagramPacket p = new DatagramPacket(new byte[4096], 4096);
        s.receive(p);
        assertEquals(relayPort, p.getPort());
        return Arrays.copyOf(p.getData(), p.getLength());
    }

    private void empty(DatagramSocket s) {
        assertThrows(SocketTimeoutException.class, () -> s.receive(new DatagramPacket(new byte[4096], 4096)));
    }

    @Test
    void learnsBothNatMappingsFromOutboundPacketsWithoutAnyPortForward() throws Exception {
        TwConfig c = config();
        try (TwRelayService relay = new TwRelayService(c);
             DatagramSocket host = client("127.0.0.2");
             DatagramSocket guest = client("127.0.0.3")) {
            relay.start();
            relay.register("match", host.getLocalAddress(), guest.getLocalAddress());
            send(guest, c, INIT);
            empty(host); // Host not learned yet; the guest retries every second
            send(host, c, POKE); // From whatever port the host's NAT chose
            empty(guest); // POKE only opens the host's mapping
            send(guest, c, INIT);
            assertArrayEquals(INIT, receive(host, c.getRelay().getPort()));
            send(host, c, ACK);
            assertArrayEquals(ACK, receive(guest, c.getRelay().getPort()));
            byte[] binary = {0, -1, 4, 10, 0, 127};
            send(guest, c, binary);
            assertArrayEquals(binary, receive(host, c.getRelay().getPort()));
            send(host, c, binary);
            assertArrayEquals(binary, receive(guest, c.getRelay().getPort()));
            relay.release("match");
            send(host, c, "after release");
            empty(guest);
            relay.register("rematch", host.getLocalAddress(), guest.getLocalAddress());
        }
    }

    @Test
    void playersSharingOnePublicIpAreToldApartByRoleAndPort() throws Exception {
        TwConfig c = config();
        try (TwRelayService relay = new TwRelayService(c);
             DatagramSocket host = client("127.0.0.2");
             DatagramSocket guest = client("127.0.0.2");
             DatagramSocket host2 = client("127.0.0.2");
             DatagramSocket guest2 = client("127.0.0.4")) {
            relay.start();
            InetAddress shared = host.getLocalAddress();
            relay.register("match", shared, shared);
            // A second host from the same IP must wait until the first host is learned
            assertThrows(IllegalStateException.class, () -> relay.register("early", shared, guest2.getLocalAddress()));
            send(host, c, POKE);
            send(guest, c, INIT);
            assertArrayEquals(INIT, receive(host, c.getRelay().getPort()));
            relay.register("second", shared, guest2.getLocalAddress());
            send(host2, c, POKE);
            send(guest2, c, INIT);
            assertArrayEquals(INIT, receive(host2, c.getRelay().getPort()));
            empty(host);
            send(guest, c, "first match");
            assertArrayEquals("first match".getBytes(), receive(host, c.getRelay().getPort()));
            send(host2, c, "second match");
            assertArrayEquals("second match".getBytes(), receive(guest2, c.getRelay().getPort()));
            empty(guest);
        }
    }

    @Test
    void ignoresUnknownSourcesChangedPortsOversizeAndNonControlFirstPackets() throws Exception {
        TwConfig c = config();
        try (TwRelayService relay = new TwRelayService(c);
             DatagramSocket host = client("127.0.0.2");
             DatagramSocket guest = client("127.0.0.3");
             DatagramSocket impostor = client("127.0.0.4");
             DatagramSocket rebound = client("127.0.0.2")) {
            relay.start();
            relay.register("match", host.getLocalAddress(), guest.getLocalAddress());
            send(host, c, "not a poke");
            send(guest, c, INIT);
            empty(host);
            send(host, c, POKE);
            send(guest, c, INIT);
            receive(host, c.getRelay().getPort());
            send(impostor, c, INIT);
            send(impostor, c, POKE);
            send(rebound, c, POKE); // Host's port is still active: a new port is not adopted
            send(rebound, c, "new source port");
            send(host, c, new byte[TwRelayService.MAX_PACKET + 1]);
            empty(guest);
            send(host, c, "valid");
            assertArrayEquals("valid".getBytes(), receive(guest, c.getRelay().getPort()));
            empty(host);
        }
    }

    @Test
    void followsAPlayerWhoseRouterMovesThemToANewPortOnceTheOldOneIsQuiet() throws Exception {
        TwConfig c = config();
        AtomicLong clock = new AtomicLong();
        try (TwRelayService relay = new TwRelayService(c, clock::get);
             DatagramSocket host = client("127.0.0.2");
             DatagramSocket guest = client("127.0.0.3");
             DatagramSocket moved = client("127.0.0.3")) {
            relay.start();
            relay.register("match", host.getLocalAddress(), guest.getLocalAddress());
            send(host, c, POKE);
            send(guest, c, INIT);
            assertArrayEquals(INIT, receive(host, c.getRelay().getPort()));
            send(host, c, "shot");
            assertArrayEquals("shot".getBytes(), receive(guest, c.getRelay().getPort()));
            send(moved, c, "too early"); // The guest's current port was just heard from
            empty(host);
            clock.addAndGet(TwRelayService.SILENCE);
            relay.expire(); // Logs the guest's silence; the session stays reserved
            send(moved, c, "from new port");
            assertArrayEquals("from new port".getBytes(), receive(host, c.getRelay().getPort()));
            send(host, c, "reply");
            assertArrayEquals("reply".getBytes(), receive(moved, c.getRelay().getPort()));
            empty(guest);
            send(guest, c, "old port"); // The abandoned mapping no longer belongs to the match
            empty(host);
        }
    }

    @Test
    void doesNotMoveEitherPlayerWhenBothShareOneIp() throws Exception {
        TwConfig c = config();
        AtomicLong clock = new AtomicLong();
        try (TwRelayService relay = new TwRelayService(c, clock::get);
             DatagramSocket host = client("127.0.0.2");
             DatagramSocket guest = client("127.0.0.2");
             DatagramSocket other = client("127.0.0.2")) {
            relay.start();
            InetAddress shared = host.getLocalAddress();
            relay.register("match", shared, shared);
            send(host, c, POKE);
            send(guest, c, INIT);
            assertArrayEquals(INIT, receive(host, c.getRelay().getPort()));
            clock.addAndGet(TwRelayService.SILENCE);
            send(other, c, "ambiguous");
            empty(host);
            empty(guest);
            send(guest, c, "still routed");
            assertArrayEquals("still routed".getBytes(), receive(host, c.getRelay().getPort()));
        }
    }

    @Test
    void expiresPendingAndEstablishedSessionsWithoutLobbyConnection() throws Exception {
        TwConfig c = config();
        AtomicLong clock = new AtomicLong();
        try (TwRelayService relay = new TwRelayService(c, clock::get);
             DatagramSocket a = client("127.0.0.2");
             DatagramSocket b = client("127.0.0.3")) {
            relay.start();
            relay.register("pending", a.getLocalAddress(), b.getLocalAddress());
            clock.addAndGet(TwRelayService.START_TIMEOUT);
            relay.expire();
            relay.register("active", a.getLocalAddress(), b.getLocalAddress());
            send(a, c, POKE);
            send(b, c, INIT);
            receive(a, c.getRelay().getPort());
            clock.addAndGet(TwRelayService.IDLE_TIMEOUT);
            relay.expire();
            send(a, c, "late");
            empty(b);
            relay.register("new", a.getLocalAddress(), b.getLocalAddress());
        }
    }

    @Test
    void disabledByDefaultAndRefusesConflictingBind() throws Exception {
        try (TwRelayService disabled = new TwRelayService(new TwConfig())) {
            disabled.start();
            assertFalse(disabled.enabled());
            assertThrows(IllegalStateException.class,
                    () -> disabled.register("x", InetAddress.getLoopbackAddress(), InetAddress.getLoopbackAddress()));
        }
        TwConfig c = config();
        try (TwRelayService first = new TwRelayService(c);
             TwRelayService second = new TwRelayService(c)) {
            first.start();
            assertThrows(SocketException.class, second::start);
        }
    }

    @Test
    void routesTw04SessionsThroughThePublicAddressUntilTheyEnd() throws Exception {
        TwConfig c = config();
        c.getRelay().setPublicAddress("203.0.113.9");
        try (TwRelayService relay = new TwRelayService(c)) {
            relay.start();
            GameEntity game = new GameEntity();
            game.setId(7L);
            game.setVers(PS2_TW04);
            SocketWrapper host = player(1L, "198.51.100.2");
            SocketWrapper guest = player(2L, "198.51.100.3");
            assertEquals(Map.of(1L, "203.0.113.9", 2L, "203.0.113.9"), relay.beforeStart(game, host, guest));
            // A second reservation for the same game is refused until the first is released
            assertThrows(IllegalStateException.class, () -> relay.beforeStart(game, host, guest));
            relay.onEnd(game, GameSessionListener.EndReason.REPORTED);
            assertEquals(2, relay.beforeStart(game, host, guest).size());
            game.setVers("PS2_NHL08");
            assertEquals(Map.of(), relay.beforeStart(game, host, guest));
        }
    }

    private static SocketWrapper player(Long personaId, String address) throws Exception {
        Socket socket = mock(Socket.class);
        when(socket.getInetAddress()).thenReturn(InetAddress.getByName(address));
        PersonaEntity persona = new PersonaEntity();
        persona.setId(personaId);
        SocketWrapper wrapper = new SocketWrapper();
        wrapper.setSocket(socket);
        wrapper.setPersonaEntity(persona);
        return wrapper;
    }
}
