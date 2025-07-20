package com.ea.services.social;

import com.ea.dto.BuddySocketWrapper;
import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.entities.core.PersonaEntity;
import com.ea.entities.social.BuddyEntity;
import com.ea.entities.social.MessageEntity;
import com.ea.repositories.buddy.BuddyRepository;
import com.ea.repositories.buddy.MessageRepository;
import com.ea.repositories.core.PersonaRepository;
import com.ea.services.server.SocketManager;
import com.ea.steps.SocketWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.Socket;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.utils.SocketUtils.getValueFromSocket;

@Slf4j
@RequiredArgsConstructor
@Service
public class BuddyService {

    private final SocketWriter socketWriter;
    private final PersonaRepository personaRepository;
    private final BuddyRepository buddyRepository;
    private final MessageRepository messageRepository;
    private final SocketManager socketManager;

    /**
     * AUTH - Authenticate to Buddy Service
     *
     * @param socket             the socket to write into
     * @param socketData         the object to use to write the message
     * @param buddySocketWrapper the wrapper containing user data
     */
    public void auth(Socket socket, SocketData socketData, BuddySocketWrapper buddySocketWrapper) {
        String prod = getValueFromSocket(socketData.getInputMessage(), "PROD");
        String vers = getValueFromSocket(socketData.getInputMessage(), "VERS");
        String lkey = getValueFromSocket(socketData.getInputMessage(), "LKEY");

        synchronized (this) {
            buddySocketWrapper.setLkey(lkey);
            buddySocketWrapper.setVers(vers);
            buddySocketWrapper.setPersonaEntity(getPersonaFromAriesSocket(buddySocketWrapper));
        }

        Map<String, String> content = Stream.of(new String[][]{
                {"TITL", prod},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        // Send pending messages for the user
        sendPendingMessages(socket, buddySocketWrapper);
    }

    /**
     * EPGT - Get User settings
     *
     * @param socket     the socket to write into
     * @param socketData the object to use to write the message
     */
    public void epgt(Socket socket, SocketData socketData) {
        String id = getValueFromSocket(socketData.getInputMessage(), "ID");

        // Can be completed with ADDR (mail address), and ENABL (T or F), likely to send messages as mail
        Map<String, String> content = Collections.singletonMap("ID", id);
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    /**
     * RGET - Retrieve Buddy List
     *
     * @param socket             the socket to write into
     * @param socketData         the object to use to write the message
     * @param buddySocketWrapper the wrapper containing user data
     */
    public void rget(Socket socket, SocketData socketData, BuddySocketWrapper buddySocketWrapper) {
        String id = getValueFromSocket(socketData.getInputMessage(), "ID");
        String list = getValueFromSocket(socketData.getInputMessage(), "LIST"); // B = buddies, I = Ignored
        // There is also PRES (presence) and PEND (pending) with Y or N

        PersonaEntity persona = buddySocketWrapper.getPersonaEntity();
        List<BuddyEntity> buddyEntities = List.of();

        // Different logic based on list type
        if ("I".equals(list)) {
            // For Ignored list: only get entries where user is fromPersona
            buddyEntities = buddyRepository.findByFromPersonaAndList(persona, list);

            // Update buddyList with ignored users
            synchronized (this) {
                Set<String> ignoredSet = buddyEntities.stream()
                        .map(buddy -> buddy.getToPersona().getPers())
                        .collect(Collectors.toSet());
                buddySocketWrapper.getBuddyList().addAll(ignoredSet);
            }
        } else if ("B".equals(list)) {
            // For Buddy list: get entries in both directions
            buddyEntities = buddyRepository.findByPersonaInEitherDirectionAndList(persona, list);

            // Update buddyList with buddy users
            synchronized (this) {
                Set<String> buddySet = buddyEntities.stream()
                        .map(buddy -> {
                            if (buddy.getFromPersona().getPers().equals(persona.getPers())) {
                                return buddy.getToPersona().getPers();
                            } else {
                                return buddy.getFromPersona().getPers();
                            }
                        })
                        .collect(Collectors.toSet());
                buddySocketWrapper.getBuddyList().addAll(buddySet);
            }
        }

        int size = buddyEntities.size();
        Map<String, String> content = Stream.of(new String[][]{
                {"ID", id},
                {"SIZE", String.valueOf(size)},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        // Send ROST packets for each buddy
        sendRostResults(socket, buddyEntities, persona, id);

        // Send presence updates for the retrieved buddies/ignored users
        sendPresenceUpdatesForBuddies(socket, buddyEntities, persona);
    }

    /**
     * ROST - Send buddy list results
     *
     * @param socket         the socket to write into
     * @param buddyEntities  the list of buddy entities
     * @param currentPersona the current user's persona
     * @param id             the ID from the request
     */
    private void sendRostResults(Socket socket, List<BuddyEntity> buddyEntities, PersonaEntity currentPersona, String id) {
        for (BuddyEntity buddy : buddyEntities) {
            String user;
            String attr;

            // Determine the other user's name and attribute based on direction
            if (buddy.getFromPersona().getPers().equals(currentPersona.getPers())) {
                // Current user is the sender - show the recipient
                user = buddy.getToPersona().getPers();
                if ("P".equals(buddy.getStatus())) {
                    attr = "S"; // Sent invitation (pending)
                } else {
                    attr = "T"; // Friend or ignored user
                }
            } else {
                // Current user is the receiver - show the sender
                user = buddy.getFromPersona().getPers();
                if ("P".equals(buddy.getStatus())) {
                    attr = "R"; // Received invitation (pending)
                } else {
                    attr = "T"; // Friend
                }
            }

            Map<String, String> rostContent = Stream.of(new String[][]{
                    {"ID", id},
                    {"USER", user},
                    {"ATTR", attr},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

            SocketData rostSocketData = new SocketData("ROST", null, rostContent);
            socketWriter.write(socket, rostSocketData);
        }
    }

    /**
     * ROST - Send a notification for received and accepted friend requests to update the screen
     *
     * @param socket the socket to send to
     * @param user   the username
     * @param attr   the attribute (R=received request, T=friend, S=sent request)
     * @param id     the ID for the packet
     */
    private void sendRostNotification(Socket socket, String user, String attr, String id) {
        Map<String, String> rostContent = Stream.of(new String[][]{
                {"ID", id},
                {"USER", user},
                {"ATTR", attr},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        SocketData rostSocketData = new SocketData("ROST", null, rostContent);
        socketWriter.write(socket, rostSocketData);
    }

    /**
     * PSET - Set presence status
     *
     * @param socket             the socket to write into
     * @param socketData         the object to use to write the message
     * @param buddySocketWrapper the wrapper containing user data
     */
    public void pset(Socket socket, SocketData socketData, BuddySocketWrapper buddySocketWrapper) {
        String show = getValueFromSocket(socketData.getInputMessage(), "SHOW"); // CHAT, PASS (in-game), AWAY

        // Update presence in the socket wrapper
        synchronized (this) {
            buddySocketWrapper.setPresence(show);
        }

        // Broadcast presence update to all buddies
        if (buddySocketWrapper.getPersonaEntity() != null) {
            broadcastPresenceUpdate(buddySocketWrapper.getPersonaEntity().getPers(), show);
        }

        socketWriter.write(socket, socketData);
    }

    /**
     * USCH - User Search
     *
     * @param socket     the socket to write into
     * @param socketData the object to use to write the message
     */
    public void usch(Socket socket, SocketData socketData) {
        String id = getValueFromSocket(socketData.getInputMessage(), "ID");
        String user = getValueFromSocket(socketData.getInputMessage(), "USER"); // Username to search for
        String maxr = Optional.ofNullable(getValueFromSocket(socketData.getInputMessage(), "MAXR"))
                .filter(s -> !s.isEmpty())
                .orElse("20"); // Max results, default to 20 if not provided

        List<PersonaEntity> foundPersonas;
        int maxResults = Integer.parseInt(maxr);
        foundPersonas = personaRepository.findByPersLike(user, maxResults);

        int size = foundPersonas.size();

        Map<String, String> content = Stream.of(new String[][]{
                {"ID", id},
                {"SIZE", String.valueOf(size)},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        // Send USER packets for each found persona
        sendUserResults(socket, foundPersonas, id);
    }

    /**
     * USER - User search results
     *
     * @param socket        the socket to write into
     * @param foundPersonas the list of found personas
     * @param id            the ID from the request
     */
    private void sendUserResults(Socket socket, List<PersonaEntity> foundPersonas, String id) {
        for (PersonaEntity persona : foundPersonas) {
            Map<String, String> userContent = Stream.of(new String[][]{
                    {"ID", id}, // Use the same ID from the request
                    {"USER", persona.getPers()}
            }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

            SocketData userSocketData = new SocketData("USER", null, userContent);
            socketWriter.write(socket, userSocketData);
        }
    }

    /**
     * RADM - Send a friend request
     *
     * @param socket             the socket to write into
     * @param socketData         the object to use to write the message
     * @param buddySocketWrapper the wrapper containing user data
     */
    public void radm(Socket socket, SocketData socketData, BuddySocketWrapper buddySocketWrapper) {
        String id = getValueFromSocket(socketData.getInputMessage(), "ID");
        String user = getValueFromSocket(socketData.getInputMessage(), "USER"); // Username to invite

        PersonaEntity fromPersona = buddySocketWrapper.getPersonaEntity();
        Optional<PersonaEntity> toPersonaOpt = personaRepository.findByPers(user);

        if (toPersonaOpt.isPresent()) {
            PersonaEntity toPersona = toPersonaOpt.get();

            // Check if buddy relationship already exists
            BuddyEntity existingFromTo = buddyRepository.findByFromPersonaAndToPersona(fromPersona, toPersona);
            BuddyEntity existingToFrom = buddyRepository.findByFromPersonaAndToPersona(toPersona, fromPersona);

            if (existingFromTo == null && existingToFrom == null) {
                // Create new buddy invitation with status "P" (pending)
                BuddyEntity buddyEntity = new BuddyEntity();
                buddyEntity.setFromPersona(fromPersona);
                buddyEntity.setToPersona(toPersona);
                buddyEntity.setList("B"); // B = buddies
                buddyEntity.setStatus("P"); // P = pending
                buddyRepository.save(buddyEntity);

                // Notify the target user if online
                Optional<BuddySocketWrapper> targetWrapperOpt = socketManager.getBuddySocketWrapperByPersona(user);
                targetWrapperOpt.ifPresent(targetWrapper -> sendRostNotification(targetWrapper.getSocket(), fromPersona.getPers(), "R", "1"));
            }

            sendSuccessResponse(socket, socketData, id, user);
        } else {
            log.warn("Attempted to invite non-existent user: {}", user);
            sendErrorResponse(socket, socketData, id);
        }
    }

    /**
     * RRSP - Respond to friend request
     *
     * @param socket             the socket to write into
     * @param socketData         the object to use to write the message
     * @param buddySocketWrapper the wrapper containing user data
     */
    @Transactional
    public void rrsp(Socket socket, SocketData socketData, BuddySocketWrapper buddySocketWrapper) {
        String id = getValueFromSocket(socketData.getInputMessage(), "ID");
        String user = getValueFromSocket(socketData.getInputMessage(), "USER"); // Username who sent the request
        String answ = getValueFromSocket(socketData.getInputMessage(), "ANSW"); // Y=accept, N=refuse, B=block

        PersonaEntity toPersona = buddySocketWrapper.getPersonaEntity(); // Current user (receiver)
        Optional<PersonaEntity> fromPersonaOpt = personaRepository.findByPers(user); // Original sender

        if (fromPersonaOpt.isPresent()) {
            PersonaEntity fromPersona = fromPersonaOpt.get(); // Original sender

            // Find the pending invitation (fromPersona sent to toPersona)
            BuddyEntity pendingInvitation = buddyRepository.findByFromPersonaAndToPersona(fromPersona, toPersona);

            if (pendingInvitation != null && "P".equals(pendingInvitation.getStatus()) && "B".equals(pendingInvitation.getList())) {
                switch (answ) {
                    case "Y":
                        pendingInvitation.setStatus("A");
                        buddyRepository.save(pendingInvitation);

                        // Notify the original sender that their request was accepted
                        Optional<BuddySocketWrapper> senderWrapperOpt = socketManager.getBuddySocketWrapperByPersona(user);
                        senderWrapperOpt.ifPresent(senderWrapper -> sendRostNotification(senderWrapper.getSocket(), toPersona.getPers(), "T", "1"));
                        break;

                    case "N":
                        buddyRepository.deleteByFromPersonaAndToPersonaAndList(fromPersona, toPersona, "B");
                        // Don't notify the original sender that their request was rejected
                        break;

                    case "B":
                        buddyRepository.deleteByFromPersonaAndToPersonaAndList(fromPersona, toPersona, "B");
                        addUserToList(toPersona, fromPersona, "I"); // toPersona blocks fromPersona
                        // Don't notify the original sender that their request was rejected
                        break;

                    default:
                        log.warn("Unknown answer for friend request: {}", answ);
                        sendErrorResponse(socket, socketData, id);
                        return;
                }
            } else {
                log.warn("No pending friend request found from {} to {}", fromPersona.getPers(), toPersona.getPers());
            }

            sendSuccessResponse(socket, socketData, id, user);
        } else {
            log.warn("Attempted to respond to request from non-existent user: {}", user);
            sendErrorResponse(socket, socketData, id);
        }
    }

    /**
     * RDEM - Cancel friend request or remove a buddy
     *
     * @param socket             the socket to write into
     * @param socketData         the object to use to write the message
     * @param buddySocketWrapper the wrapper containing user data
     */
    @Transactional
    public void rdem(Socket socket, SocketData socketData, BuddySocketWrapper buddySocketWrapper) {
        String id = getValueFromSocket(socketData.getInputMessage(), "ID");
        String user = getValueFromSocket(socketData.getInputMessage(), "USER"); // Username to remove

        PersonaEntity fromPersona = buddySocketWrapper.getPersonaEntity();
        Optional<PersonaEntity> toPersonaOpt = personaRepository.findByPers(user);

        if (toPersonaOpt.isPresent()) {
            PersonaEntity toPersona = toPersonaOpt.get();

            // Remove buddy relationship in both directions
            buddyRepository.deleteByFromPersonaAndToPersonaAndList(fromPersona, toPersona, "B");
            buddyRepository.deleteByFromPersonaAndToPersonaAndList(toPersona, fromPersona, "B");

            sendSuccessResponse(socket, socketData, id, user);
        } else {
            log.warn("Attempted to remove non-existent user: {}", user);
            sendErrorResponse(socket, socketData, id);
        }
    }

    /**
     * RADD - Add user to a list (mainly for Ignored list)
     *
     * @param socket             the socket to write into
     * @param socketData         the object to use to write the message
     * @param buddySocketWrapper the wrapper containing user data
     */
    public void radd(Socket socket, SocketData socketData, BuddySocketWrapper buddySocketWrapper) {
        String id = getValueFromSocket(socketData.getInputMessage(), "ID");
        String user = getValueFromSocket(socketData.getInputMessage(), "USER"); // Username to add
        String list = getValueFromSocket(socketData.getInputMessage(), "LIST"); // List type (mainly "I" for Ignored)

        PersonaEntity fromPersona = buddySocketWrapper.getPersonaEntity();
        Optional<PersonaEntity> toPersonaOpt = personaRepository.findByPers(user);

        if (toPersonaOpt.isPresent()) {
            PersonaEntity toPersona = toPersonaOpt.get();

            addUserToList(fromPersona, toPersona, list);
            sendSuccessResponse(socket, socketData, id, user);
        } else {
            log.warn("Attempted to add non-existent user to list: {}", user);
            sendErrorResponse(socket, socketData, id);
        }
    }

    /**
     * Add user to a list with special handling for ignored list
     *
     * @param fromPersona the persona doing the action
     * @param toPersona   the persona being added
     * @param list        the list type ("I" for ignored, "B" for buddy, etc.)
     */
    private void addUserToList(PersonaEntity fromPersona, PersonaEntity toPersona, String list) {
        if ("I".equals(list)) {
            // For ignored list, check and remove existing BUDDY relationships in both directions
            BuddyEntity existingFromTo = buddyRepository.findByFromPersonaAndToPersona(fromPersona, toPersona);
            BuddyEntity existingToFrom = buddyRepository.findByFromPersonaAndToPersona(toPersona, fromPersona);

            // Remove only buddy relationships (LIST="B"), keep existing ignore relationships
            if (existingFromTo != null && "B".equals(existingFromTo.getList())) {
                buddyRepository.delete(existingFromTo);
            }
            if (existingToFrom != null && "B".equals(existingToFrom.getList())) {
                buddyRepository.delete(existingToFrom);
            }
        } else {
            // For other lists, only check the direct relationship
            BuddyEntity existingBuddy = buddyRepository.findByFromPersonaAndToPersona(fromPersona, toPersona);
            if (existingBuddy != null && !list.equals(existingBuddy.getList())) {
                buddyRepository.delete(existingBuddy);
            }
        }

        // Check if the exact relationship already exists
        BuddyEntity existingExactRelation = buddyRepository.findByFromPersonaAndToPersona(fromPersona, toPersona);
        if (existingExactRelation == null || !list.equals(existingExactRelation.getList())) {
            // Create new relationship
            BuddyEntity buddyEntity = new BuddyEntity();
            buddyEntity.setFromPersona(fromPersona);
            buddyEntity.setToPersona(toPersona);
            buddyEntity.setList(list);
            buddyEntity.setStatus("A"); // A = Active

            buddyRepository.save(buddyEntity);
        }
    }

    /**
     * RDEL - Remove user from a list
     *
     * @param socket             the socket to write into
     * @param socketData         the object to use to write the message
     * @param buddySocketWrapper the wrapper containing user data
     */
    @Transactional
    public void rdel(Socket socket, SocketData socketData, BuddySocketWrapper buddySocketWrapper) {
        String id = getValueFromSocket(socketData.getInputMessage(), "ID");
        String user = getValueFromSocket(socketData.getInputMessage(), "USER"); // Username to remove
        String list = getValueFromSocket(socketData.getInputMessage(), "LIST");

        PersonaEntity fromPersona = buddySocketWrapper.getPersonaEntity();
        Optional<PersonaEntity> toPersonaOpt = personaRepository.findByPers(user);

        if (toPersonaOpt.isPresent()) {
            PersonaEntity toPersona = toPersonaOpt.get();

            if ("B".equals(list)) {
                // For buddy list, remove relationship in both directions
                buddyRepository.deleteByFromPersonaAndToPersonaAndList(fromPersona, toPersona, list);
                buddyRepository.deleteByFromPersonaAndToPersonaAndList(toPersona, fromPersona, list);
            } else {
                // For other lists (like ignored), only remove in one direction
                buddyRepository.deleteByFromPersonaAndToPersonaAndList(fromPersona, toPersona, list);
            }

            sendSuccessResponse(socket, socketData, id, user);
        } else {
            log.warn("Attempted to remove non-existent user from list: {}", user);
            sendErrorResponse(socket, socketData, id);
        }
    }

    /**
     * PADD - Add user to recently met players list
     *
     * @param socket             the socket to write into
     * @param socketData         the object to use to write the message
     * @param buddySocketWrapper the wrapper containing user data
     */
    public void padd(Socket socket, SocketData socketData, BuddySocketWrapper buddySocketWrapper) {
        String user = getValueFromSocket(socketData.getInputMessage(), "USER"); // Username to add to recent players

        // Add user to buddyList
        synchronized (this) {
            buddySocketWrapper.getBuddyList().add(user);
        }

        // Send presence update for the newly added user if online
        Optional<BuddySocketWrapper> addedUserWrapperOpt = socketManager.getBuddySocketWrapperByPersona(user);
        if (addedUserWrapperOpt.isPresent()) {
            BuddySocketWrapper addedUserWrapper = addedUserWrapperOpt.get();
            sendPgetPacket(socket, user, addedUserWrapper.getPresence(), addedUserWrapper.getVers());
        } else {
            // User is offline
            sendPgetPacket(socket, user, "DISC", "");
        }

        socketWriter.write(socket, socketData);
    }

    /**
     * PDEL - Remove user from buddy list
     *
     * @param socket             the socket to write into
     * @param socketData         the object to use to write the message
     * @param buddySocketWrapper the wrapper containing user data
     */
    public void pdel(Socket socket, SocketData socketData, BuddySocketWrapper buddySocketWrapper) {
        String user = getValueFromSocket(socketData.getInputMessage(), "USER"); // Username to remove from the list

        // Remove user from buddyList in memory
        synchronized (this) {
            buddySocketWrapper.getBuddyList().remove(user);
        }

        socketWriter.write(socket, socketData);
    }

    /**
     * SEND - Send a message
     *
     * @param socket             the socket to write into
     * @param socketData         the object to use to write the message
     * @param buddySocketWrapper the wrapper containing user data
     */
    public void send(Socket socket, SocketData socketData, BuddySocketWrapper buddySocketWrapper) {
//        String type = getValueFromSocket(socketData.getInputMessage(), "TYPE"); // Message type (usually "C")
        String user = getValueFromSocket(socketData.getInputMessage(), "USER"); // Target username
        String body = getValueFromSocket(socketData.getInputMessage(), "BODY"); // Message content

        if (user == null || body == null) {
            log.warn("Missing USER or BODY in SEND packet");
            socketWriter.write(socket, socketData);
            return;
        }

        // Remove quotes if present, extract username before "/" if present, then re-add quotes if necessary
        String targetUsername = user.replace("\"", "");
        targetUsername = targetUsername.contains("/") ? targetUsername.split("/")[0] : targetUsername;
        targetUsername = targetUsername.contains(" ") ? "\"" + targetUsername + "\"" : targetUsername;

        PersonaEntity fromPersona = buddySocketWrapper.getPersonaEntity();
        Optional<PersonaEntity> toPersonaOpt = personaRepository.findByPers(targetUsername);

        if (toPersonaOpt.isEmpty()) {
            log.warn("Target user not found: {}", targetUsername);
            socketWriter.write(socket, socketData);
            return;
        }

        PersonaEntity toPersona = toPersonaOpt.get();

        // Create and save the message
        MessageEntity messageEntity = new MessageEntity();
        messageEntity.setFromPersona(fromPersona);
        messageEntity.setToPersona(toPersona);
        messageEntity.setBody(body);
        messageEntity.setAck(false); // Initially unacknowledged
        messageEntity.setCreatedOn(LocalDateTime.now());
        messageRepository.save(messageEntity);

        // Check if target user is online
        Optional<BuddySocketWrapper> targetWrapperOpt = socketManager.getBuddySocketWrapperByPersona(targetUsername);

        if (targetWrapperOpt.isPresent()) {
            // Target is online - send RECV packet immediately and mark as acknowledged
            BuddySocketWrapper targetWrapper = targetWrapperOpt.get();
            sendRecvPacket(targetWrapper.getSocket(), messageEntity);

            // Mark as acknowledged since it was delivered
            messageEntity.setAck(true);
            messageRepository.save(messageEntity);
        }

        socketWriter.write(socket, socketData);
    }

    /**
     * RECV - Deliver a message
     *
     * @param socket  the socket to send to
     * @param message the message to deliver
     */
    private void sendRecvPacket(Socket socket, MessageEntity message) {
        String fromUser = message.getFromPersona().getPers();
        String body = message.getBody();

        // Convert LocalDateTime to epoch seconds
        long timeSeconds = message.getCreatedOn().atZone(ZoneOffset.UTC).toEpochSecond();

        Map<String, String> recvContent = Stream.of(new String[][]{
                {"USER", fromUser},
                {"BODY", body},
                {"TIME", String.valueOf(timeSeconds)}
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        SocketData recvSocketData = new SocketData("RECV", null, recvContent);
        socketWriter.write(socket, recvSocketData);
    }

    /**
     * DISC - Disconnect from Buddy Service
     *
     * @param buddySocketWrapper the wrapper containing user data
     */
    public void disc(BuddySocketWrapper buddySocketWrapper) {
        // Broadcast disconnect presence update to all buddies
        if (buddySocketWrapper.getPersonaEntity() != null) {
            broadcastPresenceUpdate(buddySocketWrapper.getPersonaEntity().getPers(), "DISC");
        }
    }

    /**
     * Update presence for all buddies when a user changes its status
     *
     * @param changedPersona the persona whose presence changed
     * @param newPresence    the new presence status
     */
    private void broadcastPresenceUpdate(String changedPersona, String newPresence) {
        // Get all buddy socket wrappers
        for (BuddySocketWrapper buddyWrapper : socketManager.getAllBuddySocketWrappers()) {
            if (buddyWrapper.getBuddyList() != null &&
                    buddyWrapper.getBuddyList().contains(changedPersona) &&
                    buddyWrapper.getPersonaEntity() != null &&
                    !buddyWrapper.getPersonaEntity().getPers().equals(changedPersona)) { // Don't send to self

                String vers = socketManager.getBuddySocketWrapperByPersona(changedPersona)
                        .map(BuddySocketWrapper::getVers)
                        .orElse("");

                sendPgetPacket(buddyWrapper.getSocket(), changedPersona, newPresence, vers);
            }
        }
    }

    /**
     * Send presence updates for a specific list of buddy entities
     *
     * @param socket         the socket to send updates to
     * @param buddyEntities  the list of buddy entities to send presence for
     * @param currentPersona the current user's persona
     */
    private void sendPresenceUpdatesForBuddies(Socket socket, List<BuddyEntity> buddyEntities, PersonaEntity currentPersona) {
        for (BuddyEntity buddy : buddyEntities) {
            String targetUser;

            // Determine the other user's name based on direction
            if (buddy.getFromPersona().getPers().equals(currentPersona.getPers())) {
                targetUser = buddy.getToPersona().getPers();
            } else {
                targetUser = buddy.getFromPersona().getPers();
            }

            Optional<BuddySocketWrapper> buddyWrapperOpt = socketManager.getBuddySocketWrapperByPersona(targetUser);

            if (buddyWrapperOpt.isPresent()) {
                // Buddy is online - send their current presence
                BuddySocketWrapper buddyWrapper = buddyWrapperOpt.get();
                sendPgetPacket(socket, targetUser, buddyWrapper.getPresence(), buddyWrapper.getVers());
            } else {
                // Buddy is offline - send DISC
                sendPgetPacket(socket, targetUser, "DISC", "");
            }
        }
    }

    /**
     * PGET - Send presence information to a specific socket
     *
     * @param socket     the socket to send to
     * @param targetUser the user whose presence to report
     * @param presence   the presence status (CHAT, PASS, AWAY, DISC)
     * @param vers       the version string
     */
    private void sendPgetPacket(Socket socket, String targetUser, String presence, String vers) {
        Map<String, String> pgetContent = Stream.of(new String[][]{
                {"USER", targetUser},
                {"SHOW", presence},
                {"TITL", vers != null ? vers : ""}
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        SocketData pgetSocketData = new SocketData("PGET", null, pgetContent);
        socketWriter.write(socket, pgetSocketData);
    }

    /**
     * Send pending messages to a user
     *
     * @param socket             the socket to write into
     * @param buddySocketWrapper the wrapper containing user data
     */
    @Transactional
    private void sendPendingMessages(Socket socket, BuddySocketWrapper buddySocketWrapper) {
        PersonaEntity persona = buddySocketWrapper.getPersonaEntity();
        if (persona == null) {
            return;
        }

        // Find all unacknowledged messages for this persona
        List<MessageEntity> pendingMessages = messageRepository.findUnacknowledgedMessagesByToPersona(persona);
        if (pendingMessages.isEmpty()) {
            return;
        }

        // Get the IDs of the pending messages
        List<Long> messageIds = pendingMessages.stream()
                .map(MessageEntity::getId)
                .toList();

        // Send all pending messages
        for (MessageEntity message : pendingMessages) {
            sendRecvPacket(socket, message);
        }

        // Mark all messages as acknowledged
        int updatedCount = messageRepository.markMessagesAsAcknowledgedByIds(messageIds);
        log.debug("Marked {} messages as acknowledged for {}", updatedCount, persona.getPers());
    }

    /**
     * Get PersonaEntity from aries socket using LKEY
     *
     * @param buddySocketWrapper the buddy socket wrapper
     * @return the PersonaEntity from aries socket
     */
    private PersonaEntity getPersonaFromAriesSocket(BuddySocketWrapper buddySocketWrapper) {
        String lkey = buddySocketWrapper.getLkey();
        SocketWrapper ariesSocketWrapper = socketManager.getAriesSocketWrapperByLkey(lkey);
        return ariesSocketWrapper != null ? ariesSocketWrapper.getPersonaEntity() : null;
    }

    /**
     * Send success response with FUSR
     *
     * @param socket     the socket to write into
     * @param socketData the socket data to update
     * @param id         the ID from the request
     * @param user       the username to include in FUSR
     */
    private void sendSuccessResponse(Socket socket, SocketData socketData, String id, String user) {
        Map<String, String> content = Stream.of(new String[][]{
                {"ID", id},
                {"FUSR", user},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    /**
     * Send error response when operation fails
     *
     * @param socket     the socket to write into
     * @param socketData the socket data to update
     * @param id         the ID from the request
     */
    private void sendErrorResponse(Socket socket, SocketData socketData, String id) {
        Map<String, String> content = Stream.of(new String[][]{
                {"ID", id},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

}
