package com.ea.services.core;

import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.entities.core.AccountEntity;
import com.ea.entities.core.GameEntity;
import com.ea.entities.core.PersonaConnectionEntity;
import com.ea.entities.core.PersonaEntity;
import com.ea.entities.social.FeedbackEntity;
import com.ea.entities.social.FeedbackTypeEntity;
import com.ea.entities.stats.MohhPersonaStatsEntity;
import com.ea.repositories.buddy.FeedbackRepository;
import com.ea.repositories.buddy.FeedbackTypeRepository;
import com.ea.repositories.core.*;
import com.ea.repositories.stats.MohhPersonaStatsRepository;
import com.ea.services.server.GameServerService;
import com.ea.services.server.SocketManager;
import com.ea.steps.SocketWriter;
import com.ea.utils.AccountUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.services.server.GameServerService.PSP_MOH_07_UHS;
import static com.ea.utils.HexUtils.formatHexString;
import static com.ea.utils.SocketUtils.getValueFromSocket;

@Slf4j
@RequiredArgsConstructor
@Service
public class PersonaService {

    private static final String SHA_256_ALGORITHM = "SHA-256";

    private final AccountRepository accountRepository;
    private final PersonaRepository personaRepository;
    private final PersonaConnectionRepository personaConnectionRepository;
    private final MohhPersonaStatsRepository mohhPersonaStatsRepository;
    private final GameRepository gameRepository;
    private final GameConnectionRepository gameConnectionRepository;
    private final SocketWriter socketWriter;
    private final SocketManager socketManager;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackTypeRepository feedbackTypeRepository;
    private final GameServerService gameServerService;

    /**
     * Persona creation
     *
     * @param socket
     * @param socketData
     * @param socketWrapper
     */
    public void cper(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String pers = getValueFromSocket(socketData.getInputMessage(), "PERS");
        String normalizedPers = pers.replaceAll("\"", "").trim();

        if (normalizedPers.length() < 3) {
            socketData.setIdMessage("cperinam");
            socketWriter.write(socket, socketData);
            return;
        } else if (!normalizedPers.matches("[a-zA-Z0-9 ]+") || !Character.isLetter(normalizedPers.charAt(0))) {
            socketData.setIdMessage("cperiper");
            socketWriter.write(socket, socketData);
            return;
        }

        if (normalizedPers.contains(" ")) {
            // Readd the quotes around the persona name if it contains spaces
            normalizedPers = "\"" + normalizedPers + "\"";
        }

        Optional<PersonaEntity> personaEntityOpt = personaRepository.findByPers(normalizedPers);
        if (personaEntityOpt.isPresent()) {
            socketData.setIdMessage("cperdupl");
            int alts = Integer.parseInt(getValueFromSocket(socketData.getInputMessage(), "ALTS"));
            if (alts > 0) {
                String opts = AccountUtils.suggestNames(alts, normalizedPers);
                Map<String, String> content = Stream.of(new String[][]{
                        {"OPTS", opts}
                }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
                socketData.setOutputData(content);
            }
        } else {
            PersonaEntity personaEntity = new PersonaEntity();
            personaEntity.setAccount(socketWrapper.getAccountEntity());
            personaEntity.setPers(normalizedPers);
            personaEntity.setRp(5);
            personaEntity.setCreatedOn(LocalDateTime.now());

            MohhPersonaStatsEntity mohhPersonaStatsEntity = new MohhPersonaStatsEntity();
            mohhPersonaStatsEntity.setPersona(personaEntity);
            mohhPersonaStatsEntity.setVers(socketWrapper.getPersonaConnectionEntity().getVers());
            mohhPersonaStatsEntity.setSlus(socketWrapper.getPersonaConnectionEntity().getSlus());
            Set<MohhPersonaStatsEntity> personaStatsEntities = Set.of(mohhPersonaStatsEntity);
            personaEntity.setPersonaStats(personaStatsEntities);

            personaRepository.save(personaEntity);
        }

        socketWriter.write(socket, socketData);
    }

    /**
     * Get persona
     *
     * @param socket
     * @param socketData
     * @param socketWrapper
     */
    public void pers(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String pers = getValueFromSocket(socketData.getInputMessage(), "PERS");
        if (pers.contains("@")) { // Remove @ from persona name (UHS naming convention)
            socketWrapper.getIsHost().set(true);
            pers = pers.split("@")[0] + pers.split("@")[1];
        }

        // Check if the persona is already connected (allowed for host only)
        Optional<PersonaConnectionEntity> personaConnectionEntityOpt =
                personaConnectionRepository.findByVersAndSlusAndPersonaPersAndIsHostFalseAndEndTimeIsNull(
                        socketWrapper.getPersonaConnectionEntity().getVers(),
                        socketWrapper.getPersonaConnectionEntity().getSlus(),
                        pers);
        if (personaConnectionEntityOpt.isPresent()) {
//            socketData.setIdMessage("perspset");
//            socketWriter.write(socket, socketData);
//            return;
            log.warn("Persona {} already connected, ending old session", pers);
            PersonaConnectionEntity personaConnectionEntity = personaConnectionEntityOpt.get();
            Socket socketToClose = socketManager.getSocketWrapper(personaConnectionEntity.getAddress()).getSocket();
            if (socketToClose != null) {
                log.info("Closing old socket {}", socketToClose.getRemoteSocketAddress());
                try {
                    socketToClose.close();
                } catch (IOException e) {
                    log.error("Error while closing socket", e);
                }
            } else {
                log.error("Socket to close not found");
                personaConnectionEntity.setEndTime(LocalDateTime.now());
                personaConnectionRepository.save(personaConnectionEntity);
            }

        }

        Optional<PersonaEntity> personaEntityOpt = personaRepository.findByPers(pers);
        if (personaEntityOpt.isPresent()) {
            PersonaEntity personaEntity = personaEntityOpt.get();
            if (personaEntity.getDeletedOn() != null) {
                socketData.setIdMessage("perslock");
                socketWriter.write(socket, socketData);
                return;
            }

            String lkey = "";
            try {
                lkey = formatHexString(MessageDigest.getInstance(SHA_256_ALGORITHM).digest(socketWrapper.getIdentifier().getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException e) {
                log.error("Error generating lkey for persona {}: {}", personaEntity.getPers(), e.getMessage());
            }

            synchronized (this) {
                socketWrapper.setPersonaEntity(personaEntity);
                socketWrapper.setLkey(lkey);
            }

            Map<String, String> content;
            content = Stream.of(new String[][]{
                    {"PERS", personaEntity.getPers()},
                    {"LKEY", lkey},
                    {"EX-ticker", ""},
                    {"LOC", personaEntity.getAccount().getLoc()},
                    {"A", socket.getInetAddress().getHostAddress()},
                    {"LA", socket.getInetAddress().getHostAddress()},
                    {"IDLE", "100000"},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

            socketData.setOutputData(content);
            socketWriter.write(socket, socketData);

            startPersonaConnection(socketWrapper);

            // Check if the persona has stats for this game title ("VERS"), and create them if not
            String vers = socketWrapper.getPersonaConnectionEntity().getVers();
            if (!vers.equals(PSP_MOH_07_UHS) && mohhPersonaStatsRepository.findByPersonaIdAndVers(personaEntity.getId(), vers) == null) {
                MohhPersonaStatsEntity mohhPersonaStatsEntity = new MohhPersonaStatsEntity();
                mohhPersonaStatsEntity.setPersona(personaEntity);
                mohhPersonaStatsEntity.setVers(vers);
                mohhPersonaStatsEntity.setSlus(socketWrapper.getPersonaConnectionEntity().getSlus());
                mohhPersonaStatsRepository.save(mohhPersonaStatsEntity);
            }
        }
    }

    /**
     * Registers a connection of the persona
     *
     * @param socketWrapper
     */
    private void startPersonaConnection(SocketWrapper socketWrapper) {
        PersonaEntity personaEntity = socketWrapper.getPersonaEntity();
        PersonaConnectionEntity personaConnectionEntity = socketWrapper.getPersonaConnectionEntity();
        personaConnectionEntity.setPersona(personaEntity);
        personaConnectionEntity.setHost(socketWrapper.getIsHost().get());
        personaConnectionEntity.setStartTime(LocalDateTime.now());
        personaConnectionRepository.save(personaConnectionEntity);
    }

    /**
     * Ends the current connection of the persona
     */
    public void endPersonaConnection(SocketWrapper socketWrapper) {
        PersonaConnectionEntity personaConnectionEntity = socketWrapper.getPersonaConnectionEntity();
        if (personaConnectionEntity != null && personaConnectionEntity.getPersona() != null) {
            personaConnectionEntity.setEndTime(LocalDateTime.now());
            personaConnectionRepository.save(personaConnectionEntity);
        }
    }

    /**
     * Delete persona
     *
     * @param socket
     * @param socketData
     * @param socketWrapper
     */
    public void dper(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String pers = getValueFromSocket(socketData.getInputMessage(), "PERS");

        Optional<PersonaEntity> personaEntityOpt = personaRepository.findByPers(pers);
        if (personaEntityOpt.isPresent()) {
            PersonaEntity personaEntity = personaEntityOpt.get();
            // If persona is not linked to the logged-in account, we should ban the account (imposter cheat detection)
            AccountEntity account = socketWrapper.getAccountEntity();
            if (!personaEntity.getAccount().getId().equals(account.getId())) {
                log.error("Imposter detected, persona {} not linked to account {}", pers, socketWrapper.getAccountEntity().getName());
                Set<PersonaEntity> personas = account.getPersonas();
                personas.forEach(persona -> {
                    if (persona.getDeletedOn() == null) {
                        persona.setDeletedOn(LocalDateTime.now());
                        personaRepository.save(persona);
                    }
                });
                account.setBanned(true);
                account.setUpdatedOn(LocalDateTime.now());
                accountRepository.save(account);
                socketData.setIdMessage("dperband");
                socketWriter.write(socket, socketData);
                return;
            }

            // If the persona is linked to the account, we can delete it
            personaEntity.setDeletedOn(LocalDateTime.now());
            personaRepository.save(personaEntity);
        }
        socketWriter.write(socket, socketData);
    }

    public void llvl(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        Map<String, String> content = Stream.of(new String[][]{
                {"SKILL_PTS", "211"},
                {"SKILL_LVL", "1049601"},
                {"SKILL", ""},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        who(socket, socketWrapper);

        if (socketWrapper != null && socketWrapper.getPersonaConnectionEntity() != null) {
            List<String> vers = gameServerService.getRelatedVers(socketWrapper.getPersonaConnectionEntity().getVers());
            int playersInLobby = personaConnectionRepository.countPlayersInLobby(vers);
            int playersInGame = gameConnectionRepository.countPlayersInGame(vers);
            content = Stream.of(new String[][]{
                    {"UIL", String.valueOf(playersInLobby)},
                    {"UIG", String.valueOf(playersInGame)},
                    {"UIR", "0"},
                    {"GIP", "0"},
                    {"GCR", "0"},
                    {"GCM", "0"},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
            socketWriter.write(socket, new SocketData("+sst", null, content));
        }
    }

    /**
     * Send a user update record for the current logged in user.
     *
     * @param socket
     * @param socketWrapper
     */
    public void who(Socket socket, SocketWrapper socketWrapper) {
        PersonaEntity personaEntity = socketWrapper.getPersonaEntity();
        AccountEntity accountEntity = socketWrapper.getAccountEntity();
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();

        MohhPersonaStatsEntity mohhPersonaStatsEntity = mohhPersonaStatsRepository.findByPersonaIdAndVers(personaEntity.getId(), vers);
        boolean hasStats = null != mohhPersonaStatsEntity;

        List<GameEntity> gameIds = gameRepository.findCurrentGameOfPersona(socketWrapper.getPersonaConnectionEntity().getId());
        if (gameIds.size() > 1) {
            log.error("Multiple current games found for persona {}", personaEntity.getPers());
        }

        long gameId = gameIds
                .stream()
                .max(Comparator.comparing(GameEntity::getStartTime))
                .map(gameEntity -> Optional.ofNullable(gameEntity.getOriginalId()).orElse(gameEntity.getId()))
                .orElse(0L);
        ;

        String hostPrefix = socketWrapper.getIsHost().get() ? "@" : "";

        Map<String, String> content = Stream.of(new String[][]{
                {"I", String.valueOf(accountEntity.getId())},
                {"M", hostPrefix + accountEntity.getName()},
                {"N", hostPrefix + personaEntity.getPers()},
                {"F", "U"},
                {"P", "80"},
                // Stats : kills (in hex) at 8th position, deaths (in hex) at 9th
                {"S", ",,,,,,," +
                        (hasStats ? Long.toHexString(mohhPersonaStatsEntity.getKill()) : "0") +
                        "," +
                        (hasStats ? Long.toHexString(mohhPersonaStatsEntity.getDeath()) : "0")},
                {"X", "0"},
                {"G", String.valueOf(gameId)},
                {"AT", ""},
                {"CL", "511"},
                {"LV", "1049601"},
                {"MD", "0"},
                // Rank (in decimal)
                {"R", hasStats ? String.valueOf(mohhPersonaStatsRepository.getRankByPersonaIdAndVers(mohhPersonaStatsEntity.getPersona().getId(), vers)) : ""},
                {"US", "0"},
                {"HW", "0"},
                {"RP", String.valueOf(personaEntity.getRp())}, // Reputation (0 to 5 stars)
                {"LO", accountEntity.getLoc()}, // Locale (used to display country flag)
                {"CI", "0"},
                {"CT", "0"},
                // 0x800225E0
                {"A", socket.getInetAddress().getHostAddress()},
                {"LA", socket.getInetAddress().getHostAddress()},
                // 0x80021384
                {"C", "4000,,7,1,1,,1,1,5553"},
                {"RI", "1"},
                {"RT", "1"},
                {"RG", "0"},
                {"RGC", "0"},
                // 0x80021468 if RI != ?? then read RM and RF
                {"RM", "room"},
                {"RF", "C"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketWriter.write(socket, new SocketData("+who", null, content));
    }

    /**
     * rept - Report/Give feedback on a player
     *
     * @param socket        the socket to write into
     * @param socketData    the object to use to write the message
     * @param socketWrapper the wrapper containing user data
     */
    public void rept(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String pers = getValueFromSocket(socketData.getInputMessage(), "PERS"); // Target persona name
        //String text = getValueFromSocket(socketData.getInputMessage(), "TEXT"); // Optional text comment
        String prod = getValueFromSocket(socketData.getInputMessage(), "PROD"); // Product version (goes into VERS)
        String typeStr = getValueFromSocket(socketData.getInputMessage(), "TYPE"); // Feedback type number

        if (pers == null || typeStr == null || prod == null) {
            log.warn("Missing required parameters in REPT packet");
            socketWriter.write(socket, socketData);
            return;
        }

        try {
            BigDecimal typeNumber = new BigDecimal(typeStr);

            // Find the feedback type by number
            Optional<FeedbackTypeEntity> feedbackTypeOpt = feedbackTypeRepository.findByNumber(typeNumber);
            if (!feedbackTypeOpt.isPresent()) {
                log.warn("Unknown feedback type: {}", typeNumber);
                socketWriter.write(socket, socketData);
                return;
            }

            // Find the target persona
            Optional<PersonaEntity> targetPersonaOpt = personaRepository.findByPers(pers);
            if (!targetPersonaOpt.isPresent()) {
                log.warn("Target persona not found: {}", pers);
                socketWriter.write(socket, socketData);
                return;
            }

            PersonaEntity fromPersona = socketWrapper.getPersonaEntity();
            PersonaEntity toPersona = targetPersonaOpt.get();
            FeedbackTypeEntity feedbackType = feedbackTypeOpt.get();

            // Check if feedback already exists between these personas for this type
            Optional<FeedbackEntity> existingFeedbackOpt = feedbackRepository.findByFromPersonaAndToPersonaAndFeedbackType(
                    fromPersona, toPersona, feedbackType);

            if (existingFeedbackOpt.isEmpty()) {
                FeedbackEntity feedbackEntity = new FeedbackEntity();
                feedbackEntity.setFromPersona(fromPersona);
                feedbackEntity.setToPersona(toPersona);
                feedbackEntity.setVers(prod);
                feedbackEntity.setFeedbackType(feedbackType);
                feedbackEntity.setCreatedOn(LocalDateTime.now());

                feedbackRepository.save(feedbackEntity);
            }

        } catch (NumberFormatException e) {
            log.warn("Invalid TYPE format in REPT packet: {}", typeStr);
        }

        socketWriter.write(socket, socketData);
    }

}
