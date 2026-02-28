package eu.eahub.services.core;

import eu.eahub.dto.Room;
import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.*;
import eu.eahub.repositories.core.GameConnectionRepository;
import eu.eahub.repositories.core.UserSetMemberRepository;
import eu.eahub.repositories.core.UserSetRepository;
import eu.eahub.services.server.SocketManager;
import eu.eahub.steps.SocketWriter;
import eu.eahub.utils.PersonaUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eu.eahub.utils.SocketUtils.TAB_CHAR;
import static eu.eahub.utils.SocketUtils.getValueFromSocket;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserSetService {

    private final UserSetRepository userSetRepository;
    private final UserSetMemberRepository userSetMemberRepository;
    private final GameConnectionRepository gameConnectionRepository;
    private final SocketWriter socketWriter;
    private final SocketManager socketManager;
    private final PersonaUtils personaUtils;
    private final RoomService roomService;

    /**
     * Create a new UserSet
     * Request: ucre NAME=xxx SIZE=4 TYPE=0 UPDATES=0 SYSFLAGS=KV CUSTFLAGS=JKM2 PARAMS=...
     * Response: ucre I=id T=type SF=sysflags CF=custflags O=owner S=size N=name D=desc P=params C=count IDENT=id NAME=name
     */
    @Transactional
    public void ucre(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        String name = getValueFromSocket(socketData.getInputMessage(), "NAME");
        String sizeStr = getValueFromSocket(socketData.getInputMessage(), "SIZE");
        String typeStr = getValueFromSocket(socketData.getInputMessage(), "TYPE");
        String updatesStr = getValueFromSocket(socketData.getInputMessage(), "UPDATES");
        String sysflags = getValueFromSocket(socketData.getInputMessage(), "SYSFLAGS");
        String custflags = getValueFromSocket(socketData.getInputMessage(), "CUSTFLAGS");
        String params = getValueFromSocket(socketData.getInputMessage(), "PARAMS");

        // Burnout Revenge sends ucre with null NAME on login, so we fall back to using the persona name
        if (name == null) {
            name = socketWrapper.getPersonaEntity().getPers();
        }

        // Check for duplicate name
        if (userSetRepository.existsByNameAndVersAndEndTimeIsNull(name, vers)) {
            socketData.setIdMessage("ucredupl");
            socketWriter.write(socket, socketData);
            return;
        }

        // Leave any existing UserSet
        leaveCurrentUserSet(socketWrapper);

        // Create new UserSet
        UserSetEntity userSet = new UserSetEntity();
        userSet.setVers(vers);
        userSet.setName(name);
        userSet.setSize(sizeStr != null ? Integer.parseInt(sizeStr) : 4);
        userSet.setType(typeStr != null ? Integer.parseInt(typeStr) : 0);
        userSet.setUpdates(updatesStr != null ? Integer.parseInt(updatesStr) : 0);
        userSet.setSysflags(sysflags != null ? sysflags : "");
        userSet.setCustflags(custflags != null ? custflags : "");
        userSet.setParams(params != null ? params : "");
        userSet.setOwner(socketWrapper.getPersonaEntity());
        userSet.setStartTime(LocalDateTime.now());
        userSetRepository.save(userSet);

        // Add creator as first member
        UserSetMemberEntity member = new UserSetMemberEntity();
        member.setUserSet(userSet);
        member.setPersona(socketWrapper.getPersonaEntity());
        member.setSlot(1);
        member.setStartTime(LocalDateTime.now());
        userSetMemberRepository.save(member);

        // Update socket wrapper
        socketWrapper.setUserSetId(userSet.getId());

        // Send response
        Map<String, String> content = getUserSetInfo(userSet);
        content.put("IDENT", String.valueOf(userSet.getId()));
        content.put("NAME", name);
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData, TAB_CHAR);

        // Send +who update to show US (UserSet name)
        who(socket, socketWrapper);

        // Send +ust to self
        ust(socket, userSet);

        // Send +usm to self
        usm(socket, socketWrapper);
    }

    /**
     * Search for UserSets
     * Request: usea START=0 COUNT=1000 CUSTFLAGS=0 CUSTMASK=66060801
     * Response: usea COUNT=n followed by +uss for each UserSet
     */
    public void usea(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        String countStr = getValueFromSocket(socketData.getInputMessage(), "COUNT");
        int maxCount = countStr != null ? Integer.parseInt(countStr) : 100;

        // Check for NAME parameter (specific UserSet search)
        String name = getValueFromSocket(socketData.getInputMessage(), "NAME");
        List<UserSetEntity> userSets;

        if (name != null) {
            // Search for specific UserSet by name
            Optional<UserSetEntity> userSetOpt = userSetRepository.findByNameAndVersAndEndTimeIsNull(name, vers);
            userSets = userSetOpt.map(List::of).orElse(Collections.emptyList());
        } else {
            // Search all UserSets
            userSets = userSetRepository.findByVersAndEndTimeIsNull(vers);

            // Filter to only return usersets with at least one active member
            userSets = userSets.stream()
                    .filter(us -> userSetMemberRepository.countByUserSetIdAndEndTimeIsNull(us.getId()) > 0)
                    .limit(maxCount)
                    .toList();
        }

        Map<String, String> content = Collections.singletonMap("COUNT", String.valueOf(userSets.size()));
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        // Send +uss for each UserSet
        for (UserSetEntity userSet : userSets) {
            uss(socket, userSet);
        }
    }

    /**
     * Join a UserSet
     * Request: ujoi NAME=xxx
     * Response: ujoi with UserSet info
     */
    @Transactional
    public void ujoi(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        String name = getValueFromSocket(socketData.getInputMessage(), "NAME");

        Optional<UserSetEntity> userSetOpt = userSetRepository.findByNameAndVersAndEndTimeIsNull(name, vers);
        if (userSetOpt.isEmpty()) {
            socketData.setIdMessage("ujoinfnd"); // Unknown userset
            socketWriter.write(socket, socketData);
            return;
        }

        UserSetEntity userSet = userSetOpt.get();

        // Check if already a member
        Optional<UserSetMemberEntity> existingMember = userSetMemberRepository
                .findByUserSetIdAndPersonaIdAndEndTimeIsNull(userSet.getId(), socketWrapper.getPersonaEntity().getId());
        if (existingMember.isPresent()) {
            // Already in this userset, just return info
            Map<String, String> content = getUserSetInfo(userSet);
            content.put("IDENT", String.valueOf(userSet.getId()));
            socketData.setOutputData(content);
            socketWriter.write(socket, socketData, TAB_CHAR);
            return;
        }

        // Leave any current UserSet
        leaveCurrentUserSet(socketWrapper);

        // Check if userset is full
        int currentCount = userSetMemberRepository.countByUserSetIdAndEndTimeIsNull(userSet.getId());
        if (currentCount >= userSet.getSize()) {
            socketData.setIdMessage("ujoifull"); // UserSet is full
            socketWriter.write(socket, socketData);
            return;
        }

        // Add new member
        int nextSlot = userSetMemberRepository.findMaxSlotByUserSetId(userSet.getId()) + 1;
        UserSetMemberEntity member = new UserSetMemberEntity();
        member.setUserSet(userSet);
        member.setPersona(socketWrapper.getPersonaEntity());
        member.setSlot(nextSlot);
        member.setStartTime(LocalDateTime.now());
        userSetMemberRepository.save(member);

        // Update socket wrapper
        socketWrapper.setUserSetId(userSet.getId());

        // Send response
        Map<String, String> content = getUserSetInfo(userSet);
        content.put("IDENT", String.valueOf(userSet.getId()));
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData, TAB_CHAR);

        // Send +who to joining client
        who(socket, socketWrapper);

        // Broadcast +ust and +usm to all members including the new one
        broadcastUserSetUpdate(userSet);
    }

    /**
     * Leave a UserSet
     * Request: ulea NAME=xxx
     * Response: ulea (empty)
     */
    public void ulea(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        Long userSetId = socketWrapper.getUserSetId();
        if (userSetId == null) {
            socketWriter.write(socket, socketData);
            return;
        }

        // Get UserSet info before leaving (to broadcast updates to remaining members)
        Optional<UserSetEntity> userSetOpt = userSetRepository.findById(userSetId);
        if (userSetOpt.isEmpty()) {
            socketWriter.write(socket, socketData);
            return;
        }

        // Remove member from UserSet
        Optional<UserSetMemberEntity> memberOpt = userSetMemberRepository
                .findByPersonaIdAndEndTimeIsNull(socketWrapper.getPersonaEntity().getId());

        if (memberOpt.isPresent()) {
            UserSetMemberEntity member = memberOpt.get();
            member.setEndTime(LocalDateTime.now());
            userSetMemberRepository.save(member);
        }

        // Clear UserSet from wrapper
        socketWrapper.setUserSetId(null);

        // Send response
        socketWriter.write(socket, socketData);

        // Send +who update to clear US
        who(socket, socketWrapper);

        UserSetEntity userSet = userSetOpt.get();
        // Check if userset still has members
        int remainingCount = userSetMemberRepository.countByUserSetIdAndEndTimeIsNull(userSet.getId());
        if (remainingCount > 0) {
            broadcastUserSetUpdate(userSet);
        }
    }

    /**
     * Delete a UserSet (sent by the owner)
     * Request: udel NAME=xxx
     * Response: udel (empty)
     */
    @Transactional
    public void udel(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        Long userSetId = socketWrapper.getUserSetId();

        // Get UserSet
        Optional<UserSetEntity> userSetOpt = userSetRepository.findById(userSetId);
        if (userSetOpt.isEmpty()) {
            socketWriter.write(socket, socketData);
            return;
        }

        UserSetEntity userSet = userSetOpt.get();

        String vers = userSet.getVers();

        // Get all members before deleting
        List<UserSetMemberEntity> members = userSetMemberRepository.findByUserSetIdAndEndTimeIsNull(userSet.getId());

        // Send minimal updates to all members
        for (UserSetMemberEntity member : members) {
            SocketWrapper memberWrapper = socketManager.getSocketWrapperByPersonaIdAndVers(member.getPersona().getId(), vers);
            if (memberWrapper != null) {
                // Clear UserSet from wrapper
                memberWrapper.setUserSetId(null);

                // Send +who update to clear US field (now G=0 since game is already closed)
                who(memberWrapper.getSocket(), memberWrapper);

                // Send minimal +ust with just I field
                Map<String, String> content = Collections.singletonMap("I", String.valueOf(member.getUserSet().getId()));
                socketWriter.write(memberWrapper.getSocket(), new SocketData("+ust", null, content));
            }

            // Mark member as ended
            member.setEndTime(LocalDateTime.now());
            userSetMemberRepository.save(member);
        }


        // Mark UserSet as ended
        userSet.setEndTime(LocalDateTime.now());
        userSetRepository.save(userSet);

        // Send response to requester
        socketWriter.write(socket, socketData);
    }

    /**
     * Update UserSet parameters
     * Request: uadm NAME=xxx DESC=xxx PARAMS=xxx CUSTFLAGS=xxx
     * Response: uadm with updated UserSet info
     */
    public void uadm(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String desc = getValueFromSocket(socketData.getInputMessage(), "DESC");
        String params = getValueFromSocket(socketData.getInputMessage(), "PARAMS");
        String custflags = getValueFromSocket(socketData.getInputMessage(), "CUSTFLAGS");

        if (socketWrapper.getUserSetId() == null) {
            socketWriter.write(socket, socketData);
            return;
        }

        Optional<UserSetEntity> userSetOpt = userSetRepository.findById(socketWrapper.getUserSetId());
        if (userSetOpt.isEmpty()) {
            socketWriter.write(socket, socketData);
            return;
        }

        UserSetEntity userSet = userSetOpt.get();

        // Update fields if provided
        if (desc != null) {
            userSet.setDescription(desc);
        }
        if (params != null) {
            userSet.setParams(params);
        }
        if (custflags != null) {
            userSet.setCustflags(custflags);
        }
        userSetRepository.save(userSet);

        // Send response
        Map<String, String> content = getUserSetInfo(userSet);
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData, TAB_CHAR);

        // Broadcast +ust to all members
        broadcastUserSetUpdate(userSet);
    }

    /**
     * Set/get auxiliary user information
     * Request: auxi TEXT=xxx
     * Response: auxi TEXT=xxx
     */
    public void auxi(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String text = getValueFromSocket(socketData.getInputMessage(), "TEXT");

        if (text != null) {
            // Set auxiliary text
            socketWrapper.setAuxText(text);
        }

        // Send response with current aux text
        String currentText = socketWrapper.getAuxText() != null ? socketWrapper.getAuxText() : "";
        Map<String, String> content = Collections.singletonMap("TEXT", currentText);
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        // Broadcast +usm updates to UserSet members
        if (socketWrapper.getUserSetId() != null) {
            Optional<UserSetEntity> userSetOpt = userSetRepository.findById(socketWrapper.getUserSetId());
            if (userSetOpt.isPresent()) {
                UserSetEntity userSet = userSetOpt.get();
                String vers = socketWrapper.getPersonaConnectionEntity().getVers();
                // Broadcast +usm to all members
                List<UserSetMemberEntity> members = userSetMemberRepository.findByUserSetIdAndEndTimeIsNull(userSet.getId());
                List<SocketWrapper> memberWrappers = getMemberWrappers(members, vers);

                for (SocketWrapper memberWrapper : memberWrappers) {
                    usm(memberWrapper.getSocket(), socketWrapper);
                }
            }
        }
    }

    /**
     * Check if a persona is online
     * Request: onln PERS=xxx
     * Response: onln with persona info
     */
    public void onln(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String pers = getValueFromSocket(socketData.getInputMessage(), "PERS");
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();

        SocketWrapper targetWrapper = socketManager.getSocketWrapperByPersonaNameAndVers(pers, vers);
        if (targetWrapper != null && targetWrapper.getPersonaEntity() != null) {
            Room room = roomService.getRoomByPersonaId(socketWrapper.getPersonaEntity().getId());
            Map<String, String> personaInfo = personaUtils.getPersonaInfo(socket, targetWrapper, room);
            socketData.setOutputData(personaInfo);
        }
        socketWriter.write(socket, socketData);
    }

    /**
     * Leave the current UserSet
     */
    private void leaveCurrentUserSet(SocketWrapper socketWrapper) {
        if (socketWrapper.getUserSetId() == null) {
            return;
        }

        Optional<UserSetMemberEntity> memberOpt = userSetMemberRepository
                .findByPersonaIdAndEndTimeIsNull(socketWrapper.getPersonaEntity().getId());

        if (memberOpt.isPresent()) {
            UserSetMemberEntity member = memberOpt.get();
            UserSetEntity userSet = member.getUserSet();

            member.setEndTime(LocalDateTime.now());
            userSetMemberRepository.save(member);

            // Check if this was the owner or last member
            int remainingCount = userSetMemberRepository.countByUserSetIdAndEndTimeIsNull(userSet.getId());
            if (remainingCount == 0) {
                // Close the UserSet
                userSet.setEndTime(LocalDateTime.now());
                userSetRepository.save(userSet);
            } else {
                // Broadcast update to remaining members
                broadcastUserSetUpdate(userSet);
            }
        }

        socketWrapper.setUserSetId(null);
    }

    /**
     * Send rank post-game updates: +ust, and +usm for all members
     * This is specifically called after rank to properly restore lobby state
     *
     * @param socketWrapper the socket wrapper of the player who triggered the rank
     */
    public void sendRankPostGameUpdates(SocketWrapper socketWrapper) {
        if (socketWrapper.getUserSetId() == null) {
            return;
        }

        Optional<UserSetEntity> userSetOpt = userSetRepository.findById(socketWrapper.getUserSetId());
        if (userSetOpt.isEmpty()) {
            return;
        }

        UserSetEntity userSet = userSetOpt.get();
        List<UserSetMemberEntity> members = userSetMemberRepository.findByUserSetIdAndEndTimeIsNull(userSet.getId());

        // Use the common broadcast method
        broadcastToMembers(userSet, members);
    }

    /**
     * Broadcast UserSet updates to all members
     */
    private void broadcastUserSetUpdate(UserSetEntity userSet) {
        // Filter active members (those without endTime)
        List<UserSetMemberEntity> members = userSet.getMembers().stream()
                .filter(member -> member.getEndTime() == null)
                .toList();

        broadcastToMembers(userSet, members);
    }

    /**
     * Generic broadcast method to send +ust and +usm to all specified members
     *
     * @param userSet the UserSet to broadcast
     * @param members the list of members to broadcast to
     */
    private void broadcastToMembers(UserSetEntity userSet, List<UserSetMemberEntity> members) {
        String vers = userSet.getVers();
        List<SocketWrapper> memberWrappers = getMemberWrappers(members, vers);

        for (SocketWrapper memberWrapper : memberWrappers) {
            // Send +ust
            ust(memberWrapper.getSocket(), userSet);

            // Send +usm for each member
            for (SocketWrapper otherWrapper : memberWrappers) {
                usm(memberWrapper.getSocket(), otherWrapper);
            }
        }
    }

    /**
     * Get socket wrappers for a list of members
     *
     * @param members the list of members
     * @param vers    the version
     * @return list of socket wrappers (excluding null values)
     */
    private List<SocketWrapper> getMemberWrappers(List<UserSetMemberEntity> members, String vers) {
        return members.stream()
                .map(member -> socketManager.getSocketWrapperByPersonaIdAndVers(member.getPersona().getId(), vers))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Send +uss (UserSet summary) packet
     */
    private void uss(Socket socket, UserSetEntity userSet) {
        Map<String, String> content = getUserSetInfo(userSet);
        socketWriter.write(socket, new SocketData("+uss", null, content), TAB_CHAR);
    }

    /**
     * Send +who packet for a user
     *
     * @param socket        the socket to write into
     * @param socketWrapper the wrapper containing user data
     */
    public void who(Socket socket, SocketWrapper socketWrapper) {
        Room room = roomService.getRoomByPersonaId(socketWrapper.getPersonaEntity().getId());
        Map<String, String> personaInfo = personaUtils.getPersonaInfo(socket, socketWrapper, room);

        socketWriter.write(socket, new SocketData("+who", null, personaInfo), TAB_CHAR);
    }

    /**
     * Send +ust (UserSet update) packet
     */
    private void ust(Socket socket, UserSetEntity userSet) {
        Map<String, String> content = getUserSetInfo(userSet);
        socketWriter.write(socket, new SocketData("+ust", null, content), TAB_CHAR);
    }

    /**
     * Send UserSet info (+ust) to a specific socket based on the socket wrapper's UserSet
     * This is called when a player joins a game and needs to receive the UserSet information
     *
     * @param socket        the socket to send the update to
     * @param socketWrapper the socket wrapper containing UserSet information
     */
    public void sendUserSetInfo(Socket socket, SocketWrapper socketWrapper) {
        Long userSetId = socketWrapper.getUserSetId();
        if (userSetId != null) {
            Optional<UserSetEntity> userSetOpt = userSetRepository.findById(userSetId);
            userSetOpt.ifPresent(userSetEntity -> ust(socket, userSetEntity));
        }
    }

    /**
     * Send +usm (UserSet member) packet
     */
    private void usm(Socket socket, SocketWrapper memberWrapper) {
        PersonaEntity personaEntity = memberWrapper.getPersonaEntity();

        // Get game ID if player is in a game
        Long gameId = getGameIdForUser(memberWrapper);

        Map<String, String> content = Stream.of(new String[][]{
                {"I", String.valueOf(personaEntity.getId())},
                {"N", personaEntity.getPers()},
                {"F", ""},
                {"G", String.valueOf(Optional.ofNullable(gameId).orElse(0L))},
                {"X", memberWrapper.getAuxText() != null ? memberWrapper.getAuxText() : ""},
                {"S", "0"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketWriter.write(socket, new SocketData("+usm", null, content), TAB_CHAR);
    }

    /**
     * Get the game ID for a user from their active game connection
     */
    private Long getGameIdForUser(SocketWrapper socketWrapper) {
        Optional<GameConnectionEntity> gameConnection = gameConnectionRepository
                .findByPersonaConnectionIdAndEndTimeIsNull(socketWrapper.getPersonaConnectionEntity().getId());
        return gameConnection.map(conn -> conn.getGame().getId()).orElse(null);
    }

    /**
     * Send UserSet updates (+ust and +usm) to all members of the UserSet
     * This is called when the client needs UserSet information (e.g., after sele with USERSETS=1)
     *
     * @param targetSocket the socket to send the updates to
     * @param userSetId    the ID of the UserSet
     */
    public void sendUserSetUpdatesToSocket(Socket targetSocket, Long userSetId) {
        if (userSetId == null) {
            return;
        }

        Optional<UserSetEntity> userSetOpt = userSetRepository.findById(userSetId);
        if (userSetOpt.isEmpty()) {
            return;
        }

        UserSetEntity userSet = userSetOpt.get();
        String vers = userSet.getVers();

        // Send +ust packet with UserSet info
        ust(targetSocket, userSet);

        // Send +usm for each member
        List<UserSetMemberEntity> members = userSetMemberRepository.findByUserSetIdAndEndTimeIsNull(userSet.getId());
        List<SocketWrapper> memberWrappers = getMemberWrappers(members, vers);

        for (SocketWrapper memberWrapper : memberWrappers) {
            usm(targetSocket, memberWrapper);
        }
    }

    /**
     * Get UserSet info as a map
     */
    private Map<String, String> getUserSetInfo(UserSetEntity userSet) {
        int memberCount = userSetMemberRepository.countByUserSetIdAndEndTimeIsNull(userSet.getId());

        // Add quotes around names if they contain spaces (Aries protocol requirement)
        String ownerName = "";
        if (userSet.getOwner() != null) {
            ownerName = userSet.getOwner().getPers();
            if (ownerName.contains(" ") && !ownerName.startsWith("\"")) {
                ownerName = "\"" + ownerName + "\"";
            }
        }

        String userSetName = userSet.getName();
        if (userSetName != null && userSetName.contains(" ") && !userSetName.startsWith("\"")) {
            userSetName = "\"" + userSetName + "\"";
        }

        return Stream.of(new String[][]{
                {"I", String.valueOf(userSet.getId())},
                {"T", String.valueOf(userSet.getType())},
                {"SF", userSet.getSysflags() != null ? userSet.getSysflags() : ""},
                {"CF", userSet.getCustflags() != null ? userSet.getCustflags() : ""},
                {"O", ownerName},
                {"S", String.valueOf(userSet.getSize())},
                {"N", userSetName},
                {"D", userSet.getDescription() != null ? userSet.getDescription() : ""},
                {"P", userSet.getParams() != null ? userSet.getParams() : ""},
                {"C", String.valueOf(memberCount)},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    }

    /**
     * Broadcast complete userset state after game creation
     * This sends +who with G= and +usm updates to all userset members
     */
    public void broadcastUserSetStateAfterGameCreation(SocketWrapper hostWrapper) {
        if (hostWrapper.getUserSetId() == null) {
            return;
        }

        Optional<UserSetEntity> userSetOpt = userSetRepository.findById(hostWrapper.getUserSetId());
        if (userSetOpt.isEmpty()) {
            return;
        }

        UserSetEntity userSet = userSetOpt.get();
        List<UserSetMemberEntity> members = userSetMemberRepository.findByUserSetIdAndEndTimeIsNull(userSet.getId());
        String vers = userSet.getVers();
        List<SocketWrapper> memberWrappers = getMemberWrappers(members, vers);

        // Broadcast +usm to all members
        for (SocketWrapper memberWrapper : memberWrappers) {
            // Send +usm for this member to all members
            for (SocketWrapper otherWrapper : memberWrappers) {
                usm(otherWrapper.getSocket(), memberWrapper);
            }
        }
    }

    /**
     * Broadcast +usm for all game members to update G= attribute
     * Used after gjoi to inform all members about the game ID
     */
    public void broadcastUserSetMembersForGame(GameEntity gameEntity) {
        // Get all active game connections
        List<GameConnectionEntity> gameConnections = gameConnectionRepository.findByGameIdAndEndTimeIsNull(gameEntity.getId());

        // Get socket wrappers for all game members
        List<SocketWrapper> memberWrappers = gameConnections.stream()
                .map(conn -> socketManager.getSocketWrapperByPersonaConnectionId(conn.getPersonaConnection().getId()))
                .filter(java.util.Objects::nonNull)
                .toList();

        // Send +usm for each game member to all game members
        for (SocketWrapper memberWrapper : memberWrappers) {
            for (SocketWrapper otherWrapper : memberWrappers) {
                usm(otherWrapper.getSocket(), memberWrapper);
            }
        }
    }

    /**
     * Close all active UserSets and members on startup/shutdown
     */
    @PostConstruct
    @PreDestroy
    private void closeActiveUserSets() {
        LocalDateTime now = LocalDateTime.now();
        int membersCleaned = userSetMemberRepository.setEndTimeForAllUnfinishedMembers(now);
        int userSetsCleaned = userSetRepository.setEndTimeForAllUnfinishedUserSets(now);
        log.info("UserSet data cleaned: {} usersets, {} members", userSetsCleaned, membersCleaned);
    }

    /**
     * End UserSet membership when a user disconnects.
     * If the user was just a member, their membership is ended and remaining members are notified.
     * If the user was the owner, the entire UserSet is closed and all members are notified.
     *
     * @param socketWrapper the socket wrapper of the disconnecting user
     */
    @Transactional
    public void endUserSetMembership(SocketWrapper socketWrapper) {
        if (socketWrapper == null || socketWrapper.getPersonaEntity() == null) {
            return;
        }

        Long personaId = socketWrapper.getPersonaEntity().getId();

        // Find active membership
        Optional<UserSetMemberEntity> memberOpt = userSetMemberRepository.findByPersonaIdAndEndTimeIsNull(personaId);
        if (memberOpt.isEmpty()) {
            return;
        }

        UserSetMemberEntity member = memberOpt.get();

        // Reload the UserSet with eager fetch to avoid LazyInitializationException
        Long userSetId = member.getUserSet().getId();
        Optional<UserSetEntity> userSetOpt = userSetRepository.findById(userSetId);
        if (userSetOpt.isEmpty()) {
            return;
        }

        UserSetEntity userSet = userSetOpt.get();
        String vers = userSet.getVers();
        boolean isOwner = userSet.getOwner() != null && userSet.getOwner().getId().equals(personaId);

        if (isOwner) {
            // Owner is disconnecting - close the entire UserSet
            LocalDateTime now = LocalDateTime.now();

            // Get all members before closing
            List<UserSetMemberEntity> members = userSetMemberRepository.findByUserSetIdAndEndTimeIsNull(userSet.getId());

            // Notify all other members and close their memberships
            for (UserSetMemberEntity otherMember : members) {
                if (!otherMember.getPersona().getId().equals(personaId)) {
                    SocketWrapper memberWrapper = socketManager.getSocketWrapperByPersonaIdAndVers(otherMember.getPersona().getId(), vers);
                    if (memberWrapper != null) {
                        // Clear UserSet from wrapper
                        memberWrapper.setUserSetId(null);
                        // Send minimal +ust with just I field to notify UserSet is gone
                        Map<String, String> ustContent = Collections.singletonMap("I", String.valueOf(userSet.getId()));
                        socketWriter.write(memberWrapper.getSocket(), new SocketData("+ust", null, ustContent));
                    }
                }

                // Close membership
                otherMember.setEndTime(now);
                userSetMemberRepository.save(otherMember);
            }

            // Close the member's own membership (if not already closed)
            member.setEndTime(now);
            userSetMemberRepository.save(member);

            // Close the UserSet
            userSet.setEndTime(now);
            userSetRepository.save(userSet);

        } else {
            // Regular member is disconnecting - just end their membership
            member.setEndTime(LocalDateTime.now());
            userSetMemberRepository.save(member);

            // Check if userset still has members
            int remainingCount = userSetMemberRepository.countByUserSetIdAndEndTimeIsNull(userSet.getId());
            if (remainingCount > 0) {
                // Broadcast update to remaining members
                broadcastUserSetUpdate(userSet);
            } else {
                // No more members, close the userset
                userSet.setEndTime(LocalDateTime.now());
                userSetRepository.save(userSet);
            }
        }

        // Clear UserSet from wrapper
        socketWrapper.setUserSetId(null);
    }

}
