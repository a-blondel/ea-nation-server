package eu.eahub.services.social;

import eu.eahub.config.GameServerConfig;
import eu.eahub.dirtysdk.LobbyTagField;
import eu.eahub.dto.BuddySocketWrapper;
import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.entities.social.BuddyEntity;
import eu.eahub.repositories.buddy.BuddyRepository;
import eu.eahub.repositories.core.PersonaRepository;
import eu.eahub.services.core.ConnectionListener;
import eu.eahub.services.server.SocketManager;
import eu.eahub.steps.SocketWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.Socket;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static eu.eahub.services.server.GameServerService.ALL_TW;

/**
 * TW04 Messenger, an earlier dialect of the buddy server
 * One-way buddy (B) and ignore (I) lists with groups, presence with a status text, and messages to online buddies only.
 * The lobby key (LKEY) must belong to a live lobby connection, and is checked again on every command
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TwBuddyService implements ConnectionListener {

    private static final int MAX_LIST_SIZE = 100;
    private static final int MAX_BODY_LENGTH = 2048;
    private static final Set<String> LISTS = Set.of("B", "I");

    private final GameServerConfig gameServerConfig;
    private final SocketWriter socketWriter;
    private final SocketManager socketManager;
    private final BuddyRepository buddyRepository;
    private final PersonaRepository personaRepository;
    private final ChatHistoryService chatHistoryService;

    /**
     * Whether a buddy connection uses the TW04 Messenger dialect
     *
     * @param buddySocketWrapper the buddy connection
     * @return true once it has authenticated as TW04
     */
    public boolean isTw(BuddySocketWrapper buddySocketWrapper) {
        return buddySocketWrapper != null && buddySocketWrapper.getVers() != null && ALL_TW.contains(buddySocketWrapper.getVers());
    }

    /**
     * Get the game whose Messenger product an AUTH names (PROD=tiger-ps2-2004 or USER=/cso/tiger-ps2-2004)
     *
     * @param socketData the AUTH message
     * @return the game version, if it is a TW04 Messenger login
     */
    public Optional<String> authVers(SocketData socketData) {
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        return gameServerConfig.getServers().stream()
                .filter(server -> ALL_TW.contains(server.getName()) && !server.getBuddyProduct().isEmpty())
                .filter(server -> server.getBuddyProduct().equals(fields.get("PROD"))
                        || ("/cso/" + server.getBuddyProduct()).equals(fields.getOrDefault("USER", "")))
                .map(GameServerConfig.GameServer::getName)
                .findFirst();
    }

    public synchronized void auth(Socket socket, SocketData socketData, BuddySocketWrapper wrapper, String vers) {
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String id = fields.getOrDefault("ID", "0");
        String lkey = fields.getOrDefault("LKEY", "");
        wrapper.setVers(vers);
        wrapper.setKeepAlive("PING");
        PersonaEntity persona = lobbyPersona(lkey, vers);
        if (persona == null) {
            fail(socket, "AUTH", lkey.isBlank() ? "miss" : "auth", id);
            return;
        }
        // One Messenger connection per player: close the older one
        for (BuddySocketWrapper old : socketManager.getAllBuddySocketWrappers()) {
            if (old != wrapper && isTw(old) && old.getPersonaEntity() != null && old.getPersonaEntity().getId().equals(persona.getId())) {
                close(old);
            }
        }
        wrapper.setLkey(lkey);
        wrapper.setPersonaEntity(persona);
        wrapper.setPresenceFields(Map.of("SHOW", "DISC"));
        socketWriter.writeTagFields(socket, "AUTH", Map.of());
    }

    /**
     * Get the persona of the live lobby connection a key was issued to
     */
    private PersonaEntity lobbyPersona(String lkey, String vers) {
        if (lkey == null || lkey.isBlank()) {
            return null;
        }
        SocketWrapper lobby = socketManager.getAriesSocketWrapperByLkey(lkey);
        if (lobby == null || lobby.getPersonaEntity() == null || lobby.getPersonaConnectionEntity() == null
                || !vers.equals(lobby.getPersonaConnectionEntity().getVers())) {
            return null;
        }
        return lobby.getPersonaEntity();
    }

    /**
     * Check that a connection is still authenticated, otherwise reply with the command's auth error
     */
    private boolean authenticated(Socket socket, String verb, String id, BuddySocketWrapper wrapper) {
        PersonaEntity live = lobbyPersona(wrapper.getLkey(), wrapper.getVers());
        if (wrapper.getPersonaEntity() == null || live == null || !live.getId().equals(wrapper.getPersonaEntity().getId())) {
            fail(socket, verb, "auth", id);
            return false;
        }
        return true;
    }

    public synchronized void pset(Socket socket, SocketData socketData, BuddySocketWrapper wrapper) {
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        if (!authenticated(socket, "PSET", fields.getOrDefault("ID", "0"), wrapper)) {
            return;
        }
        Map<String, String> presence = new LinkedHashMap<>();
        for (String key : List.of("SHOW", "STAT", "PROD")) {
            if (fields.containsKey(key)) {
                presence.put(key, limited(fields.get(key), 1024));
            }
        }
        presence.putIfAbsent("SHOW", "CHAT");
        wrapper.setPresenceFields(presence);
        socketWriter.writeTagFields(socket, "PSET", Map.of());
        announce(wrapper.getPersonaEntity());
    }

    public synchronized void rget(Socket socket, SocketData socketData, BuddySocketWrapper wrapper) {
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String id = fields.getOrDefault("ID", "0");
        String list = fields.getOrDefault("LIST", "B");
        if (!authenticated(socket, "RGET", id, wrapper)) {
            return;
        }
        if (!LISTS.contains(list)) {
            fail(socket, "RGET", "user", id);
            return;
        }
        List<BuddyEntity> buddies = list(wrapper.getPersonaEntity(), list);
        socketWriter.writeTagFields(socket, "RGET", Map.of("ID", id, "SIZE", String.valueOf(buddies.size())));
        for (BuddyEntity buddy : buddies) {
            socketWriter.writeTagFields(socket, "ROST", Map.of("ID", id, "USER", buddy.getToPersona().getPers(),
                    "GROUP", buddy.getGroupName() == null ? "" : buddy.getGroupName()));
        }
        if (list.equals("B") && "Y".equals(fields.get("PRES"))) {
            for (BuddyEntity buddy : buddies) {
                tell(socket, wrapper, buddy.getToPersona());
            }
        }
    }

    private List<BuddyEntity> list(PersonaEntity owner, String list) {
        return buddyRepository.findByFromPersonaAndList(owner, list).stream()
                .filter(buddy -> buddy.getToPersona().getDeletedOn() == null)
                .sorted(Comparator.comparing(buddy -> buddy.getToPersona().getPers().toLowerCase()))
                .toList();
    }

    public synchronized void radd(Socket socket, SocketData socketData, BuddySocketWrapper wrapper) {
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String id = fields.getOrDefault("ID", "0");
        String list = fields.getOrDefault("LIST", "B");
        String group = fields.getOrDefault("GROUP", "");
        if (!authenticated(socket, "RADD", id, wrapper)) {
            return;
        }
        PersonaEntity owner = wrapper.getPersonaEntity();
        Optional<PersonaEntity> target = person(fields.getOrDefault("USER", ""));
        if (!LISTS.contains(list) || group.length() > 64 || target.isEmpty() || target.get().getId().equals(owner.getId())) {
            fail(socket, "RADD", "user", id);
            return;
        }
        BuddyEntity buddy = buddyRepository.findByFromPersonaAndList(owner, list).stream()
                .filter(existing -> existing.getToPersona().getId().equals(target.get().getId()))
                .findFirst()
                .orElse(null);
        if (buddy == null) {
            if (list(owner, list).size() >= MAX_LIST_SIZE) {
                fail(socket, "RADD", "user", id);
                return;
            }
            buddy = new BuddyEntity();
            buddy.setFromPersona(owner);
            buddy.setToPersona(target.get());
            buddy.setList(list);
            buddy.setStatus("A");
        }
        buddy.setGroupName(group);
        buddyRepository.save(buddy);
        socketWriter.writeTagFields(socket, "RADD", Map.of("ID", id, "FUSR", target.get().getPers()));
        if (list.equals("B")) {
            tell(socket, wrapper, target.get());
        } else {
            announce(owner);
        }
    }

    public synchronized void rdel(Socket socket, SocketData socketData, BuddySocketWrapper wrapper) {
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String id = fields.getOrDefault("ID", "0");
        String list = fields.getOrDefault("LIST", "B");
        if (!authenticated(socket, "RDEL", id, wrapper)) {
            return;
        }
        if (!LISTS.contains(list)) {
            fail(socket, "RDEL", "user", id);
            return;
        }
        PersonaEntity owner = wrapper.getPersonaEntity();
        person(fields.getOrDefault("USER", "")).ifPresent(target -> buddyRepository.findByFromPersonaAndList(owner, list).stream()
                .filter(existing -> existing.getToPersona().getId().equals(target.getId()))
                .forEach(buddyRepository::delete));
        socketWriter.writeTagFields(socket, "RDEL", Map.of("ID", id));
        if (list.equals("I")) {
            announce(owner);
        }
    }

    public synchronized void send(Socket socket, SocketData socketData, BuddySocketWrapper wrapper) {
        Map<String, String> fields = LobbyTagField.decode(socketData.getInputMessage());
        String id = fields.getOrDefault("ID", "0");
        if (!authenticated(socket, "SEND", id, wrapper)) {
            return;
        }
        PersonaEntity owner = wrapper.getPersonaEntity();
        String body = fields.getOrDefault("BODY", "");
        BuddySocketWrapper target = person(fields.getOrDefault("USER", "")).flatMap(this::connection).orElse(null);
        if (target == null || target.getSocket().isClosed() || blocks(target.getPersonaEntity(), owner) || body.length() > MAX_BODY_LENGTH) {
            fail(socket, "SEND", "user", id);
            return;
        }
        socketWriter.writeTagFields(target.getSocket(), "RECV", Map.of("USER", owner.getPers(), "TYPE", limited(fields.getOrDefault("TYPE", "C"), 8),
                "BODY", body));
        chatHistoryService.add(owner.getId(), owner.getPers(), target.getPersonaEntity().getId(), "", body);
        socketWriter.writeTagFields(socket, "SEND", Map.of("ID", id));
    }

    public synchronized void disc(Socket socket, SocketData socketData, BuddySocketWrapper wrapper) {
        socketWriter.writeTagFields(socket, "DISC", Map.of("ID", LobbyTagField.decode(socketData.getInputMessage()).getOrDefault("ID", "0")));
        close(wrapper);
    }

    /**
     * Any other command is acknowledged with its ID
     */
    public void other(Socket socket, SocketData socketData) {
        socketWriter.writeTagFields(socket, socketData.getIdMessage(), Map.of("ID", LobbyTagField.decode(socketData.getInputMessage()).getOrDefault("ID", "0")));
    }

    private Optional<PersonaEntity> person(String name) {
        return name.isEmpty() ? Optional.empty() : personaRepository.findByPers(name).filter(persona -> persona.getDeletedOn() == null);
    }

    private Optional<BuddySocketWrapper> connection(PersonaEntity persona) {
        return socketManager.getAllBuddySocketWrappers().stream()
                .filter(wrapper -> isTw(wrapper) && wrapper.getPersonaEntity() != null && wrapper.getPersonaEntity().getId().equals(persona.getId()))
                .findFirst();
    }

    private boolean blocks(PersonaEntity owner, PersonaEntity target) {
        return buddyRepository.existsByFromPersonaIdAndToPersonaIdAndList(owner.getId(), target.getId(), "I");
    }

    /**
     * Send a player's presence to everyone who has them as a buddy
     */
    private void announce(PersonaEntity persona) {
        for (BuddySocketWrapper watcher : socketManager.getAllBuddySocketWrappers()) {
            if (isTw(watcher) && watcher.getPersonaEntity() != null && !watcher.getPersonaEntity().getId().equals(persona.getId())
                    && list(watcher.getPersonaEntity(), "B").stream().anyMatch(buddy -> buddy.getToPersona().getId().equals(persona.getId()))) {
                tell(watcher.getSocket(), watcher, persona);
            }
        }
    }

    /**
     * Send a viewer a player's presence: offline (DISC) if they are not connected or ignore the viewer
     */
    private void tell(Socket socket, BuddySocketWrapper viewer, PersonaEntity persona) {
        Optional<BuddySocketWrapper> online = connection(persona);
        Map<String, String> fields = new LinkedHashMap<>();
        if (online.isEmpty() || blocks(persona, viewer.getPersonaEntity())) {
            fields.put("SHOW", "DISC");
        } else {
            fields.putAll(online.get().getPresenceFields());
        }
        fields.put("USER", persona.getPers());
        socketWriter.writeTagFields(socket, "PGET", fields);
    }

    /**
     * Close a Messenger connection; its buddies see the player go offline
     */
    private void close(BuddySocketWrapper wrapper) {
        PersonaEntity persona = wrapper.getPersonaEntity();
        wrapper.setPersonaEntity(null);
        wrapper.setLkey(null);
        try {
            wrapper.getSocket().close();
        } catch (IOException ignored) {
            // Already closed
        }
        if (persona != null) {
            announce(persona);
        }
    }

    /**
     * The lobby connection closed: its key is no longer valid, so close the Messenger connections that used it
     */
    @Override
    public synchronized void onDisconnect(SocketWrapper socketWrapper) {
        if (socketWrapper.getLkey() == null) {
            return;
        }
        for (BuddySocketWrapper wrapper : socketManager.getAllBuddySocketWrappers()) {
            if (isTw(wrapper) && Objects.equals(socketWrapper.getLkey(), wrapper.getLkey())) {
                close(wrapper);
            }
        }
    }

    @Override
    public synchronized void onBuddyDisconnect(BuddySocketWrapper buddySocketWrapper) {
        if (isTw(buddySocketWrapper) && buddySocketWrapper.getPersonaEntity() != null) {
            PersonaEntity persona = buddySocketWrapper.getPersonaEntity();
            buddySocketWrapper.setPersonaEntity(null);
            announce(persona);
        }
    }

    private static String limited(String value, int max) {
        return value.substring(0, Math.min(max, value.length()));
    }

    private void fail(Socket socket, String verb, String error, String id) {
        socketWriter.writeTagFields(socket, verb + error, Map.of("ID", id));
    }
}
