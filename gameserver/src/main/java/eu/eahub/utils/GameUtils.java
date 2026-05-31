package eu.eahub.utils;

import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.GameConnectionEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaConnectionEntity;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.entities.stats.MohhPersonaStatsEntity;
import eu.eahub.repositories.core.GameConnectionRepository;
import eu.eahub.repositories.stats.MohhPersonaStatsRepository;
import eu.eahub.services.server.GameServerService;
import eu.eahub.services.server.SocketManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eu.eahub.services.server.GameServerService.ALL_MOH;
import static eu.eahub.utils.HexUtils.*;
import static eu.eahub.utils.SocketUtils.DATETIME_FORMAT;

@Component
@RequiredArgsConstructor
@Slf4j
public class GameUtils {

    private final GameConnectionRepository gameConnectionRepository;
    private final MohhPersonaStatsRepository mohhPersonaStatsRepository;
    private final GameServerService gameServerService;
    private final SocketManager socketManager;

    public Map<String, String> getGameInfo(GameEntity gameEntity) {
        return getGameInfo(gameEntity, false);
    }

    /**
     * Get game info
     *
     * @param gameEntity The game entity to get info for
     * @return Map with game info
     */
    public Map<String, String> getGameInfo(GameEntity gameEntity, boolean isHost) {
        Long gameId = gameEntity.getId();
        SocketWrapper hostSocketWrapperOfGame = socketManager.getHostSocketWrapperOfGame(gameId);

        List<GameConnectionEntity> gameConnections = gameConnectionRepository.findByGameIdAndEndTimeIsNull(gameId);

        boolean isP2P = gameServerService.isP2P(gameEntity.getVers());

        String host = hostSocketWrapperOfGame.getPersonaEntity().getPers();
        int count = gameConnections.size();

        String sysflags = gameEntity.getSysflags();
        if (StringUtils.isNotEmpty(gameEntity.getPass())) {
            sysflags = String.valueOf(Integer.parseInt(sysflags) | (1 << 16)); // Add password flag (16th bit)
        }
        Map<String, String> content = Stream.of(new String[][]{
                {"IDENT", String.valueOf(Optional.ofNullable(gameEntity.getOriginalId()).orElse(gameEntity.getId()))},
                {"NAME", gameEntity.getName()},
                {"HOST", host},
                // { "GPSHOST", hostSocketWrapperOfGame.getPers() },
                {"PARAMS", gameEntity.getParams() != null ? gameEntity.getParams() : ""},
                {"PLATPARAMS", "0"},  // ???
                {"ROOM", String.valueOf(gameEntity.getRoomId() != null ? gameEntity.getRoomId() : 0)},
                {"CUSTFLAGS", "413082880"},
                {"SYSFLAGS", sysflags},
                {"COUNT", String.valueOf(count)},
                // { "GPSREGION", "2" },
                {"PRIV", "0"},
                {"MINSIZE", String.valueOf(gameEntity.getMinsize())},
                {"MAXSIZE", String.valueOf(gameEntity.getMaxsize())},
                {"NUMPART", "1"},
                {"SEED", "3"}, // random seed
                {"WHEN", DateTimeFormatter.ofPattern(DATETIME_FORMAT).format(gameEntity.getStartTime())},
                // { "GAMEPORT", String.valueOf(props.getUdpPort())},
                // { "VOIPPORT", "9667" },
                // { "GAMEMODE", "0" }, // ???
                {"AUTH", (Integer.parseInt(gameEntity.getSysflags()) & (1 << 18)) != 0 ? "098f6bcd4621d373cade4e832627b4f6" : ""}, // Required for ranked

                // loop 0x80022058 only if COUNT>=0

                // another loop 0x8002225C only if NUMPART>=0
                // { "SELF", "" },

                {"SESS", "0"}, // %s-%s-%08x 0--498ea96f

                {"EVID", "0"},
                {"EVGID", "0"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        int[] idx = {0};

        if (isHost) {
            gameConnections = gameConnections.stream().sorted(Comparator.comparing(GameConnectionEntity::getId).reversed()).toList();
        } else {
            gameConnections = gameConnections.stream().sorted(Comparator.comparing(GameConnectionEntity::getId)).toList();
        }

        gameConnections.stream()
//                .sorted(Comparator.comparing(GameConnectionEntity::getId))
                .forEach(gameConnectionEntity -> {
                    PersonaConnectionEntity personaConnectionEntity = gameConnectionEntity.getPersonaConnection();
                    PersonaEntity personaEntity = personaConnectionEntity.getPersona();
                    SocketWrapper socketWrapper = socketManager.getSocketWrapperByPersonaConnectionId(personaConnectionEntity.getId());
                    if (socketWrapper == null) {
                        log.warn("SocketWrapper not found for PersonaConnectionEntity ID: {}", personaConnectionEntity.getId());
                        return;
                    }
//                    boolean isHost = !isP2P && gameConnectionEntity.isHost();
                    String ipAddr = personaConnectionEntity.getAddress().replace("/", "").split(":")[0];
                    String hostPrefix = "";
                    String opparamValue = !socketWrapper.getUserparams().isEmpty() ? socketWrapper.getUserparams() : generateOpParam(personaEntity, gameEntity.getVers());

                    content.putAll(Stream.of(new String[][]{
                            {"OPID" + idx[0], String.valueOf(personaEntity.getId())},
                            {"OPPO" + idx[0], hostPrefix + personaEntity.getPers()},
                            {"ADDR" + idx[0], ipAddr},
                            {"LADDR" + idx[0], ipAddr},
                            {"MADDR" + idx[0], ""},
                            {"OPPART" + idx[0], "0"},
                            {"OPPARAM" + idx[0], opparamValue},
                            {"OPFLAG" + idx[0], socketWrapper.getUserflags()},
                            {"PRES" + idx[0], "0"},
                            {"PARTSIZE" + idx[0], String.valueOf(gameEntity.getMaxsize())},
                            {"PARTPARAMS" + idx[0], ""},
                    }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
                    idx[0]++;
                });
        return content;
    }

    /**
     * Generate the OPPARAM for a player
     *
     * @param personaEntity The persona entity of the player
     * @param vers          The version of the game
     * @return Base64 encoded OPPARAM string
     */
    private String generateOpParam(PersonaEntity personaEntity, String vers) {
        if (!ALL_MOH.contains(vers)) {
            return "";
        }
        MohhPersonaStatsEntity mohhPersonaStatsEntity = mohhPersonaStatsRepository.findByPersonaIdAndVers(personaEntity.getId(), vers);
        Long rankLong = mohhPersonaStatsRepository.getRankByPersonaIdAndVers(personaEntity.getId(), vers);
        int rank = (rankLong != null) ? rankLong.intValue() : 0;
        String loc = personaEntity.getAccount().getLoc();
        String killHex = mohhPersonaStatsEntity != null ? reverseEndianness(formatIntToWord(mohhPersonaStatsEntity.getKill())) : "00000000";
        String deathHex = mohhPersonaStatsEntity != null ? reverseEndianness(formatIntToWord(mohhPersonaStatsEntity.getDeath())) : "00000000";
        String rankHex = reverseEndianness(formatIntToWord(rank));
        String locHex = reverseEndianness(formatIntToWord(Integer.parseInt(stringToHex(loc.substring(loc.length() - 2)), 16)));
        String repHex = reverseEndianness(formatIntToWord(personaEntity.getRp()));
        String lastHex = reverseEndianness(formatIntToWord(1));

        String concatenatedHex = killHex + deathHex + rankHex + locHex + repHex + lastHex;
        byte[] byteArray = parseHexString(concatenatedHex);
        return Base64.getEncoder().encodeToString(byteArray);
    }

}
