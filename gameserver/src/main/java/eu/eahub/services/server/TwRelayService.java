package eu.eahub.services.server;

import eu.eahub.config.TwConfig;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.services.core.GameSessionListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import static eu.eahub.services.server.GameServerService.ALL_TW;

/**
 * Optional, opaque UDP forwarding for accepted TW04 matches. No arbitrary destinations.
 *
 * <p>Both players send first, so neither needs an inbound port forward. DSOCKF.IRX's CommUDP
 * listener (the match host) sends an 8-byte POKE {5,0} every second to the address it was given
 * while it waits, and the connector (the guest) sends an 8-byte INIT {1,ident} every second. Each
 * player's NAT mapping is therefore learned from its own first packet, whatever port the NAT
 * chose, and the packet type says which side of which reserved match it belongs to. The listener
 * accepts the INIT from any source and the connector follows a POKE's source, so both then talk
 * only to this socket. If a home router later moves a player to a new port, the relay follows it
 * once the old port has gone quiet.
 */
@Slf4j
@Service
public class TwRelayService implements GameSessionListener, AutoCloseable {

    static final int MAX_PACKET = 2048;
    static final int MAX_MATCHES = 64;
    static final long START_TIMEOUT = Duration.ofSeconds(60).toNanos();
    static final long IDLE_TIMEOUT = Duration.ofMinutes(2).toNanos();
    static final long MAX_AGE = Duration.ofHours(6).toNanos();
    static final long REBIND_QUIET = Duration.ofSeconds(1).toNanos(); // A quiet endpoint may be replaced by its player's new NAT port
    static final long SILENCE = Duration.ofSeconds(3).toNanos(); // Logged when one side of a match goes this long without a packet
    private static final int INIT = 1;
    private static final int POKE = 5;
    // The game always sends peer traffic to UDP 3658, so remote players can only reach a relay on that port
    private static final int GAME_PEER_PORT = 3658;

    private final TwConfig.Relay config;
    private final LongSupplier clock;
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<InetSocketAddress, Peer> endpoints = new HashMap<>();
    private volatile DatagramSocket socket;
    private Thread worker;

    private static final class Session {
        String id;
        long created;
        Peer a;
        Peer b;
    }

    private static final class Peer {
        Session session;
        InetAddress address;
        boolean host;
        InetSocketAddress endpoint;
        long lastSeen;
        long rateWindow;
        long forwarded;
        long longestGap;
        int packets;
        int rebinds;
        int strays;
        boolean seen;
        boolean silent;

        String role() {
            return host ? "host" : "guest";
        }

        Peer other() {
            return session.a == this ? session.b : session.a;
        }
    }

    @Autowired
    public TwRelayService(TwConfig config) {
        this(config, System::nanoTime);
    }

    TwRelayService(TwConfig config, LongSupplier clock) {
        this.config = config.getRelay();
        this.clock = clock;
    }

    /**
     * Check if the relay is enabled in the configuration
     *
     * @return true if tw04.relay.enabled is set
     */
    public boolean enabled() {
        return config.isEnabled();
    }

    /**
     * Validate the relay configuration and start listening, if the relay is enabled
     *
     * @throws IOException if the UDP socket cannot be bound
     */
    @PostConstruct
    public void start() throws IOException {
        if (!enabled()) {
            return;
        }
        ipv4(config.getPublicAddress());
        InetAddress bind = ipv4(config.getBindAddress());
        if (config.getPort() != GAME_PEER_PORT) {
            log.warn("TW04 relay on UDP {}: remote players can only reach it on {}, where the game sends its peer traffic", config.getPort(), GAME_PEER_PORT);
        }
        DatagramSocket opened = new DatagramSocket(null);
        try {
            opened.setReuseAddress(false);
            opened.bind(new InetSocketAddress(bind, config.getPort()));
            opened.setSoTimeout(1000);
        } catch (IOException e) {
            opened.close();
            throw e;
        }
        socket = opened;
        worker = new Thread(() -> receive(opened), "tw04-udp-relay");
        worker.setDaemon(true);
        worker.start();
        log.info("TW04 relay listening on {}:{}", config.getBindAddress(), config.getPort());
    }

    /**
     * Reserve a route for an accepted match
     * Both players may share one public IP. Only one not-yet-learned host and one not-yet-learned guest
     * per source IP may wait at a time, so a first packet can never be attributed to the wrong match.
     *
     * @param id The match id
     * @param a  The host (listener) address
     * @param b  The guest (connector) address
     */
    public synchronized void register(String id, InetAddress a, InetAddress b) {
        if (!enabled() || socket == null || socket.isClosed()) {
            throw new IllegalStateException("Relay is unavailable");
        }
        expire();
        if (!(a instanceof Inet4Address) || !(b instanceof Inet4Address) || sessions.containsKey(id) || sessions.size() >= MAX_MATCHES) {
            throw new IllegalStateException("Relay is full or the match is already reserved");
        }
        if (waiting(a, true) || waiting(b, false)) {
            throw new IllegalStateException("Another match from this address is still connecting; try again shortly");
        }
        Session session = new Session();
        session.id = id;
        session.created = clock.getAsLong();
        session.a = peer(session, a, true);
        session.b = peer(session, b, false);
        sessions.put(id, session);
        log.info("TW04 relay reserved for {} and {}", a.getHostAddress(), b.getHostAddress());
    }

    private boolean waiting(InetAddress address, boolean host) {
        for (Session s : sessions.values()) {
            for (Peer p : List.of(s.a, s.b)) {
                if (p.endpoint == null && p.host == host && p.address.equals(address)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Peer peer(Session session, InetAddress address, boolean host) {
        Peer peer = new Peer();
        peer.session = session;
        peer.address = address;
        peer.host = host;
        peer.lastSeen = session.created;
        peer.rateWindow = session.created;
        return peer;
    }

    /**
     * Route a TW04 session through the relay: both players are given the relay's public address in +ses
     *
     * @param game  the game
     * @param host  the host's connection
     * @param guest the guest's connection
     * @return the relay address for both players, or nothing when the relay is off
     */
    @Override
    public Map<Long, String> beforeStart(GameEntity game, SocketWrapper host, SocketWrapper guest) {
        if (!enabled() || !ALL_TW.contains(game.getVers())) {
            return Map.of();
        }
        register(String.valueOf(game.getId()), host.getSocket().getInetAddress(), guest.getSocket().getInetAddress());
        return Map.of(host.getPersonaEntity().getId(), config.getPublicAddress(), guest.getPersonaEntity().getId(), config.getPublicAddress());
    }

    /**
     * Release the route when the session is reported, cancelled or expires; a lobby disconnect does not end it, as
     * the consoles leave the lobby while they play
     *
     * @param game   the game
     * @param reason why it ended
     */
    @Override
    public void onEnd(GameEntity game, EndReason reason) {
        release(String.valueOf(game.getId()));
    }

    private void receive(DatagramSocket opened) {
        byte[] buffer = new byte[MAX_PACKET + 1];
        while (!opened.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                opened.receive(packet);
                forward(opened, packet);
            } catch (SocketTimeoutException ignored) {
                synchronized (this) {
                    expire();
                }
            } catch (IOException e) {
                if (!opened.isClosed()) {
                    log.warn("TW04 relay receive/send failed: {}", e.toString());
                }
            }
        }
    }

    /**
     * Get the CommUDP control type of a packet
     * Control packets are two IOP-native (little-endian) words; accept either order.
     *
     * @param packet The received packet
     * @return POKE, INIT, or 0 for game data
     */
    static int control(DatagramPacket packet) {
        if (packet.getLength() != 8) {
            return 0;
        }
        byte[] d = packet.getData();
        int o = packet.getOffset();
        int little = (d[o] & 0xff) | (d[o + 1] & 0xff) << 8 | (d[o + 2] & 0xff) << 16 | d[o + 3] << 24;
        int big = d[o] << 24 | (d[o + 1] & 0xff) << 16 | (d[o + 2] & 0xff) << 8 | (d[o + 3] & 0xff);
        boolean zero = d[o + 4] == 0 && d[o + 5] == 0 && d[o + 6] == 0 && d[o + 7] == 0;
        for (int type : new int[]{little, big}) {
            if (type == POKE && zero) {
                return POKE;
            }
            if (type == INIT && !zero) {
                return INIT;
            }
        }
        return 0;
    }

    private synchronized void forward(DatagramSocket opened, DatagramPacket packet) throws IOException {
        expire();
        if (packet.getLength() == 0 || packet.getLength() > MAX_PACKET) {
            return;
        }
        InetSocketAddress source = (InetSocketAddress) packet.getSocketAddress();
        int control = control(packet);
        long now = clock.getAsLong();
        Peer sender = endpoints.get(source);
        if (sender == null && control != 0) {
            sender = learn(source, control);
        }
        if (sender == null) {
            sender = rebind(source, now);
        }
        if (sender == null) {
            return;
        }
        if (now - sender.rateWindow >= Duration.ofSeconds(1).toNanos()) {
            sender.rateWindow = now;
            sender.packets = 0;
        }
        if (++sender.packets > 1000) {
            return;
        }
        if (sender.seen) {
            long gap = now - sender.lastSeen;
            sender.longestGap = Math.max(sender.longestGap, gap);
            if (sender.silent) {
                log.info("TW04 relay {} {} resumed after {} ms", sender.role(), sender.endpoint, gap / 1_000_000);
            }
        }
        sender.silent = false;
        sender.seen = true;
        sender.lastSeen = now;
        // A POKE only opens the host's NAT mapping; the guest's INIT now has somewhere to go
        if (sender.host && control == POKE) {
            return;
        }
        Peer target = sender.other();
        if (target.endpoint == null) {
            return; // The guest retries its INIT every second
        }
        opened.send(new DatagramPacket(packet.getData(), packet.getOffset(), packet.getLength(), target.endpoint));
        if (++sender.forwarded == 1) {
            log.info("TW04 relay forwarding {} -> {}", sender.endpoint, target.endpoint);
        }
    }

    /**
     * Learn a NAT mapping only from the packet its role sends first: a host's POKE or a guest's
     * INIT, from the IP the authenticated lobby connection came from.
     */
    private Peer learn(InetSocketAddress source, int control) {
        boolean host = control == POKE;
        Peer found = null;
        for (Session s : sessions.values()) {
            for (Peer p : List.of(s.a, s.b)) {
                if (p.endpoint == null && p.host == host && p.address.equals(source.getAddress())) {
                    found = p;
                }
            }
        }
        if (found == null) {
            return null;
        }
        found.endpoint = source;
        endpoints.put(source, found);
        log.info("TW04 relay learned {} endpoint {}", found.role(), source);
        return found;
    }

    /**
     * Follow a player whose router moved them to a new port mid-match. Only when exactly one
     * learned endpoint has that IP (two players behind one IP cannot be told apart by address) and
     * it has been quiet, so a stray packet cannot take over a working route.
     */
    private Peer rebind(InetSocketAddress source, long now) {
        Peer found = null;
        int matches = 0;
        for (Session s : sessions.values()) {
            for (Peer p : List.of(s.a, s.b)) {
                if (p.endpoint != null && p.address.equals(source.getAddress())) {
                    found = p;
                    matches++;
                }
            }
        }
        if (found == null) {
            return null;
        }
        if (matches > 1 || now - found.lastSeen < REBIND_QUIET) {
            if (found.strays++ < 5) {
                log.info("TW04 relay ignored {} for {} {}: {}", source, found.role(), found.endpoint,
                        matches > 1 ? "players share this IP" : "current port still active");
            }
            return null;
        }
        log.warn("TW04 relay {} endpoint moved {} -> {} after {} ms quiet", found.role(), found.endpoint, source, (now - found.lastSeen) / 1_000_000);
        endpoints.remove(found.endpoint);
        found.endpoint = source;
        endpoints.put(source, found);
        found.rebinds++;
        return found;
    }

    synchronized void expire() {
        long now = clock.getAsLong();
        for (Session s : sessions.values()) {
            for (Peer p : List.of(s.a, s.b)) {
                if (p.seen && !p.silent && now - p.lastSeen >= SILENCE) {
                    p.silent = true;
                    Peer o = p.other();
                    log.warn("TW04 relay {} {} silent for {} ms; {} last heard {} ms ago", p.role(), p.endpoint, (now - p.lastSeen) / 1_000_000,
                            o.role(), o.seen ? (now - o.lastSeen) / 1_000_000 : -1);
                }
            }
        }
        for (Session s : List.copyOf(sessions.values())) {
            boolean pending = !s.a.seen || !s.b.seen;
            if ((pending && now - s.created >= START_TIMEOUT) || now - Math.min(s.a.lastSeen, s.b.lastSeen) >= IDLE_TIMEOUT
                    || now - s.created >= MAX_AGE) {
                release(s.id);
            }
        }
    }

    /**
     * Release a match's route and log its traffic summary
     *
     * @param id The match id
     */
    public synchronized void release(String id) {
        Session s = sessions.remove(id);
        if (s == null) {
            return;
        }
        for (Peer p : List.of(s.a, s.b)) {
            if (p.endpoint != null) {
                endpoints.remove(p.endpoint);
            }
        }
        long now = clock.getAsLong();
        log.info("TW04 relay released {} / {}; forwarded {} / {} packets; longest gap {} / {} ms; last heard {} / {} ms ago; port changes {} / {}",
                s.a.address.getHostAddress(), s.b.address.getHostAddress(), s.a.forwarded, s.b.forwarded,
                s.a.longestGap / 1_000_000, s.b.longestGap / 1_000_000,
                s.a.seen ? (now - s.a.lastSeen) / 1_000_000 : -1, s.b.seen ? (now - s.b.lastSeen) / 1_000_000 : -1,
                s.a.rebinds, s.b.rebinds);
    }

    static InetAddress ipv4(String text) {
        if (!text.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
            throw new IllegalArgumentException("Relay requires numeric IPv4 addresses");
        }
        byte[] bytes = new byte[4];
        String[] pieces = text.split("\\.");
        for (int i = 0; i < 4; i++) {
            int value = Integer.parseInt(pieces[i]);
            if (value > 255) {
                throw new IllegalArgumentException("Invalid relay IPv4 address");
            }
            bytes[i] = (byte) value;
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException impossible) {
            throw new IllegalArgumentException(impossible);
        }
    }

    /**
     * Stop the relay and release every reserved match
     */
    @Override
    @PreDestroy
    public void close() {
        DatagramSocket opened = socket;
        if (opened != null) {
            opened.close();
        }
        if (worker != null) {
            try {
                worker.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (this) {
            for (String id : List.copyOf(sessions.keySet())) {
                release(id);
            }
        }
    }
}
