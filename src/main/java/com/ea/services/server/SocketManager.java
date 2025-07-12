package com.ea.services.server;

import com.ea.dto.BuddySocketWrapper;
import com.ea.dto.SocketWrapper;
import com.ea.repositories.core.GameConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Component
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

    public SocketWrapper getSocketWrapper(Socket socket) {
        return getSocketWrapper(socket.getRemoteSocketAddress().toString());
    }

    public BuddySocketWrapper getBuddySocketWrapper(Socket socket) {
        return buddySockets.get(socket.getRemoteSocketAddress().toString());
    }

    public SocketWrapper getSocketWrapper(String identifier) {
        return sockets.get(identifier);
    }

    public SocketWrapper getAriesSocketWrapperByLkey(String lkey) {
        return sockets.values().stream()
                .filter(wrapper -> lkey.equals(wrapper.getLkey()))
                .findFirst()
                .orElse(null);
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
                        wrapper.getPersonaConnectionEntity().getId().equals(personaConnectionId))
                .findFirst()
                .orElse(null);
    }

    public List<SocketWrapper> getSocketWrapperByVers(String vers) {
        return sockets.values().stream()
                .filter(wrapper -> wrapper.getPersonaConnectionEntity() != null &&
                        wrapper.getPersonaConnectionEntity().getVers().equals(vers)).toList();
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

}