package eu.eahub.services.server;

import eu.eahub.dto.BuddySocketWrapper;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.repositories.core.GameConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Component
@Slf4j
public class SocketManager {

    private final GameConnectionRepository gameConnectionRepository;
    private final ConcurrentHashMap<String, SocketWrapper> sockets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BuddySocketWrapper> buddySockets = new ConcurrentHashMap<>();

    public void addSocket(String identifier, Socket socket) {
        SocketWrapper wrapper = new SocketWrapper();
        wrapper.setSocket(socket);
        wrapper.setIdentifier(identifier);
        sockets.put(identifier, wrapper);
    }

    public void addBuddySocket(String identifier, Socket socket) {
        BuddySocketWrapper wrapper = new BuddySocketWrapper();
        wrapper.setSocket(socket);
        wrapper.setIdentifier(identifier);
        buddySockets.put(identifier, wrapper);
    }

    public void removeSocket(String identifier) {
        sockets.remove(identifier);
    }

    public void removeBuddySocket(String identifier) {
        buddySockets.remove(identifier);
    }

    public SocketWrapper getSocketWrapper(String identifier) {
        return sockets.get(identifier);
    }

    /**
     * Finds a SocketWrapper by exact Socket object match.
     *
     * @param socket The exact Socket object to find
     * @return The SocketWrapper containing this exact socket, or null if not found
     */
    public SocketWrapper getSocketWrapperBySocket(Socket socket) {
        return sockets.values().stream()
                .filter(wrapper -> wrapper.getSocket() == socket)
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds a BuddySocketWrapper by exact Socket object match.
     *
     * @param socket The exact Socket object to find
     * @return The BuddySocketWrapper containing this exact socket, or null if not found
     */
    public BuddySocketWrapper getBuddySocketWrapperBySocket(Socket socket) {
        return buddySockets.values().stream()
                .filter(wrapper -> wrapper.getSocket() == socket)
                .findFirst()
                .orElse(null);
    }

    public SocketWrapper getAriesSocketWrapperByLkey(String lkey) {
        SocketWrapper result = sockets.values().stream()
                .filter(wrapper -> lkey.equals(wrapper.getLkey()))
                .findFirst()
                .orElse(null);

        // If no exact match, try partial match by removing last character (bug from FIFA on PS2)
        if (result == null && lkey.length() > 1) {
            result = sockets.values().stream()
                    .filter(wrapper -> {
                        String wrapperLkey = wrapper.getLkey();
                        return wrapperLkey != null &&
                                wrapperLkey.length() > 1 &&
                                lkey.equals(wrapperLkey.substring(0, wrapperLkey.length() - 1));
                    })
                    .findFirst()
                    .orElse(null);
        }
        return result;
    }

    public Set<String> getActiveSocketIdentifiers() {
        return sockets.keySet();
    }

    public SocketWrapper getHostSocketWrapperOfGame(Long gameId) {
        return gameConnectionRepository.findHostAddressByGameId(gameId)
                .stream()
                .findFirst()
                .map(this::getSocketWrapper)
                .orElse(null);
    }

    public SocketWrapper getSocketWrapperByPersonaConnectionId(Long personaConnectionId) {
        return sockets.values().stream()
                .filter(wrapper -> wrapper.getPersonaConnectionEntity() != null &&
                        wrapper.getPersonaConnectionEntity().getId() != null &&
                        wrapper.getPersonaConnectionEntity().getId().equals(personaConnectionId))
                .findFirst()
                .orElse(null);
    }

    public List<SocketWrapper> getSocketWrapperByVers(String vers) {
        cleanupClosedSockets();

        return sockets.values().stream()
                .filter(wrapper -> wrapper.getPersonaConnectionEntity() != null &&
                        wrapper.getPersonaConnectionEntity().getVers().equals(vers)).toList();
    }

    /**
     * Cleans up closed sockets from the manager.
     * This is to ensure that we do not keep references to sockets that are no longer valid.
     */
    private void cleanupClosedSockets() {
        sockets.entrySet().removeIf(entry -> {
            Socket socket = entry.getValue().getSocket();
            if (socket == null || socket.isClosed() || !socket.isConnected() || socket.isOutputShutdown()) {
                log.info("Removing closed socket: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    public SocketWrapper getAvailableGps() {
        return sockets.values().stream()
                .filter(wrapper -> wrapper.getIsGps().get() && !wrapper.getIsHosting().get())
                .findFirst()
                .orElse(null);
    }

    public List<BuddySocketWrapper> getAllBuddySocketWrappers() {
        return List.copyOf(buddySockets.values());
    }

    public Optional<BuddySocketWrapper> getBuddySocketWrapperByPersona(String personaName) {
        return buddySockets.values().stream()
                .filter(wrapper -> wrapper.getPersonaEntity() != null &&
                        personaName.equals(wrapper.getPersonaEntity().getPers()))
                .findFirst();
    }

    public List<Socket> getSockets() {
        return sockets.values().stream()
                .map(SocketWrapper::getSocket)
                .toList();
    }

    public List<Socket> getBuddySockets() {
        return buddySockets.values().stream()
                .map(BuddySocketWrapper::getSocket)
                .toList();
    }

    /**
     * Find a SocketWrapper by persona ID and game version.
     *
     * @param personaId The persona ID to search for
     * @param vers      The game version to filter by
     * @return The matching SocketWrapper, or null if not found
     */
    public SocketWrapper getSocketWrapperByPersonaIdAndVers(Long personaId, String vers) {
        return sockets.values().stream()
                .filter(wrapper -> wrapper.getPersonaEntity() != null &&
                        wrapper.getPersonaEntity().getId() != null &&
                        wrapper.getPersonaEntity().getId().equals(personaId) &&
                        wrapper.getPersonaConnectionEntity() != null &&
                        vers.equals(wrapper.getPersonaConnectionEntity().getVers()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Find a SocketWrapper by persona name and game version.
     *
     * @param personaName The persona name to search for
     * @param vers        The game version to filter by
     * @return The matching SocketWrapper, or null if not found
     */
    public SocketWrapper getSocketWrapperByPersonaNameAndVers(String personaName, String vers) {
        return sockets.values().stream()
                .filter(wrapper -> wrapper.getPersonaEntity() != null &&
                        personaName.equals(wrapper.getPersonaEntity().getPers()) &&
                        wrapper.getPersonaConnectionEntity() != null &&
                        vers.equals(wrapper.getPersonaConnectionEntity().getVers()))
                .findFirst()
                .orElse(null);
    }

}