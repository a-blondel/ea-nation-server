package com.ea.utils;

import com.ea.dto.Room;
import com.ea.dto.SocketWrapper;
import com.ea.entities.core.AccountEntity;
import com.ea.entities.core.GameEntity;
import com.ea.entities.core.PersonaEntity;
import com.ea.repositories.core.GameRepository;
import com.ea.services.core.RoomService;
import com.ea.services.stats.MohhStatsService;
import com.ea.services.stats.NhlStatsService;
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

import static com.ea.services.server.GameServerService.MOH07_OR_MOH08;
import static com.ea.services.server.GameServerService.PSP_NHL_07;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersonaUtils {

    private final GameRepository gameRepository;
    private final RoomService roomService;
    private final MohhStatsService mohhStatsService;
    private final NhlStatsService nhlStatsService;

    public Map<String, String> getPersonaInfo(Socket socket, SocketWrapper socketWrapper) {
        PersonaEntity personaEntity = socketWrapper.getPersonaEntity();
        AccountEntity accountEntity = socketWrapper.getAccountEntity();
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();

        String stats = ",,,,,,,,,";
        String rank = "";

        // MoHH specific stats
        if (MOH07_OR_MOH08.contains(vers)) {
            Map<String, String> mohhData = mohhStatsService.getStatsAndRank(personaEntity, vers);
            stats = mohhData.get("stats");
            rank = mohhData.get("rank");
        } else if (PSP_NHL_07.equals(vers)) {
            Map<String, String> mohhData = nhlStatsService.getStatsAndRank(personaEntity, vers);
            stats = mohhData.get("stats");
            rank = mohhData.get("rank");
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

        String hostPrefix = socketWrapper.getIsDedicatedHost().get() ? "@" : "";

        Room room = roomService.getRoomByPersonaId(personaEntity.getId());

        return Stream.of(new String[][]{
                {"I", String.valueOf(accountEntity.getId())},
                {"M", hostPrefix + accountEntity.getName()},
                {"N", hostPrefix + personaEntity.getPers()},
                {"F", "U"},
                {"P", "80"},
                {"S", stats},
                {"X", "0"},
                {"G", String.valueOf(gameId)},
                {"AT", ""},
                {"CL", "511"},
                {"LV", "1049601"},
                {"MD", "0"},
                // Rank (in decimal)
                {"R", rank},
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
                {"RI", room != null ? String.valueOf(room.getId()) : "0"}, // Room identifier
                {"RT", room != null ? String.valueOf(room.getPersonaIds().size()) : "0"}, // Room current population
                {"RG", "0"},
                {"RGC", "0"},
                // 0x80021468 if RI != ?? then read RM and RF
                {"RM", room != null ? room.getName() : "room"}, // Room name
                {"RF", room != null ? room.getFlags() : "CK"}, // Room flags
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    }


}
