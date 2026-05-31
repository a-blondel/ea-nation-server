package eu.eahub.utils;

import eu.eahub.dto.Room;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.AccountEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.repositories.core.GameRepository;
import eu.eahub.repositories.core.UserSetRepository;
import eu.eahub.services.stats.FifaStatsService;
import eu.eahub.services.stats.MohhStatsService;
import eu.eahub.services.stats.NfsStatsService;
import eu.eahub.services.stats.NhlStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eu.eahub.services.server.GameServerService.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersonaUtils {

    private final GameRepository gameRepository;
    private final MohhStatsService mohhStatsService;
    private final NhlStatsService nhlStatsService;
    private final NfsStatsService nfsStatsService;
    private final FifaStatsService fifaStatsService;
    private final UserSetRepository userSetRepository;

    public Map<String, String> getPersonaInfo(Socket socket, SocketWrapper socketWrapper, Room room) {
        PersonaEntity personaEntity = socketWrapper.getPersonaEntity();
        AccountEntity accountEntity = socketWrapper.getAccountEntity();
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();

        String stats = ",,,,,,,,,";
        String rank = "";

        // MoHH specific stats
        if (ALL_MOH.contains(vers)) {
            Map<String, String> mohhData = mohhStatsService.getStatsAndRank(personaEntity, vers);
            stats = mohhData.get("stats");
            rank = mohhData.get("rank");
        } else if (ALL_NHL.contains(vers)) {
            Map<String, String> nhlData = nhlStatsService.getStatsAndRank(personaEntity, vers);
            stats = nhlData.get("stats");
            rank = nhlData.get("rank");
        } else if (ALL_PSP_NFS.contains(vers)) {
            Map<String, String> nfsData = nfsStatsService.getStatsAndRank(personaEntity, vers);
            stats = nfsData.get("stats");
            rank = nfsData.get("rank");
        } else if (ALL_FIFA.contains(vers)) {
            Map<String, String> fifaData = fifaStatsService.getStatsAndRank(personaEntity, vers);
            stats = fifaData.get("stats");
            rank = fifaData.get("rank");
        }

        List<GameEntity> gameIds = gameRepository.findCurrentGameOfPersona(socketWrapper.getPersonaConnectionEntity().getId());
        if (gameIds.size() > 1) {
            log.error("Multiple current games found for persona {}", personaEntity.getPers());
        }

        long gameId = gameIds
                .stream()
                .max(Comparator.comparing(GameEntity::getStartTime))
                .map(gameEntity -> Optional.ofNullable(gameEntity.getOriginalId()).orElse(gameEntity.getId()))
                .orElse(0L);

        String hostPrefix = socketWrapper.getIsDedicatedHost().get() ? "" : "";

        // Get auxiliary text from socket wrapper (used by NFS MW)
        String auxText = socketWrapper.getAuxText() != null ? socketWrapper.getAuxText() : "";

        String userSetName = getUserSetName(socketWrapper);

        return Stream.of(new String[][]{
                {"I", String.valueOf(accountEntity.getId())},
                {"M", hostPrefix + accountEntity.getName()},
                {"N", hostPrefix + personaEntity.getPers()},
                {"F", "U"},
                {"P", "80"},
                {"S", stats},
                {"X", auxText},
                {"G", String.valueOf(gameId)},
                {"AT", ""},
                {"CL", "511"},
                {"LV", "1049601"},
                {"MD", "0"},
                // Rank (in decimal)
                {"R", rank},
                {"US", userSetName}, // UserSet name
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
                {"RI", room != null ? String.valueOf(room.getId()) : "0"}, // Room identifier
                {"RT", room != null ? String.valueOf(room.getPersonaIds().size()) : "0"}, // Room current population
                {"RG", "0"},
                {"RGC", "0"},
                // 0x80021468 if RI != ?? then read RM and RF
                {"RM", "LVL.1"}, // Room name
                {"RF", room != null ? room.getFlags() : "CK"}, // Room flags
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    }

    /**
     * Get UserSet name for the given SocketWrapper
     *
     * @param socketWrapper the SocketWrapper
     * @return the UserSet name, or empty string if not in a UserSet
     */
    private String getUserSetName(SocketWrapper socketWrapper) {
        String userSetName = "";
        if (socketWrapper.getUserSetId() != null) {
            userSetName = userSetRepository.findById(socketWrapper.getUserSetId())
                    .map(us -> {
                        String name = us.getName();
                        // Add quotes if name contains spaces
                        if (name != null && name.contains(" ") && !name.startsWith("\"")) {
                            return "\"" + name + "\"";
                        }
                        return name;
                    })
                    .orElse("");
        }
        return userSetName;
    }


}
