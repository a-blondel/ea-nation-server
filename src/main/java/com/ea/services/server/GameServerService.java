package com.ea.services.server;

import com.ea.config.GameServerConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class GameServerService {

    public static final String PSP_NHL_07 = "PSP/NHL07";
    public static final String PSP_MOH_07_UHS = "PSP/MOHGPS071";
    public static final String PSP_MOH_07 = "PSP/MOH07";
    public static final String PSP_MOH_08 = "PSP/MOH08";
    public static final String WII_MOH_08 = "WII/MOH08";
    public static final List<String> MOH07_OR_UHS = List.of(PSP_MOH_07, PSP_MOH_07_UHS);
    public static final List<String> MOH07_OR_MOH08 = List.of(PSP_MOH_07, PSP_MOH_08, WII_MOH_08);
    public static final List<String> GAMES_WITHOUT_ROOM = List.of(PSP_MOH_07_UHS, PSP_MOH_07, PSP_MOH_08, WII_MOH_08);
    private final GameServerConfig gameServerConfig;

    /**
     * Get TCP port for a given VERS and SLUS
     *
     * @param vers Version identifier
     * @param slus SLUS identifier
     * @return TCP port number or -1 if not found
     */
    public int getTcpPort(String vers, String slus) {
        // First, check if VERS and SLUS match a region
        Optional<Integer> regionPortOpt = gameServerConfig.getServers().stream()
                .filter(GameServerConfig.GameServer::isEnabled)
                .filter(server -> server.getVers().equals(vers))
                .flatMap(server -> server.getRegions().stream())
                .filter(region -> region.getSlus() != null && region.getSlus().contains(slus))
                .map(GameServerConfig.RegionConfig::getPort)
                .findFirst();

        // Otherwise, check if VERS and SLUS match a dedicated server
        return regionPortOpt.orElseGet(() -> gameServerConfig.getServers().stream()
                .filter(GameServerConfig.GameServer::isEnabled)
                .filter(server -> server.getDedicated() != null &&
                        server.getDedicated().getVers() != null &&
                        server.getDedicated().getVers().equals(vers) &&
                        server.getDedicated().getSlus() != null &&
                        server.getDedicated().getSlus().equals(slus))
                .map(server -> server.getDedicated().getPort())
                .findFirst()
                .orElse(-1));
    }

    /**
     * Get all related versions for a given version
     *
     * @param vers Version identifier
     * @return List of related versions
     */
    public List<String> getRelatedVers(String vers) {
        return gameServerConfig.getServers().stream()
                .filter(GameServerConfig.GameServer::isEnabled)
                .filter(server -> server.getVers().equals(vers) ||
                        (server.getDedicated() != null && server.getDedicated().getVers().equals(vers)))
                .flatMap(server -> {
                    // Always include the main server version
                    Stream<String> mainVersion = Stream.of(server.getVers());

                    // Include dedicated server version if it exists
                    Stream<String> dedicatedVersion = server.getDedicated() != null && server.getDedicated().getVers() != null
                            ? Stream.of(server.getDedicated().getVers())
                            : Stream.empty();

                    return Stream.concat(mainVersion, dedicatedVersion);
                })
                .distinct()
                .toList();
    }

    /**
     * Get a server by its version
     *
     * @param vers Version identifier
     * @return Optional containing the server if found, otherwise empty
     */
    public Optional<GameServerConfig.GameServer> getServerByVers(String vers) {
        return gameServerConfig.getServers().stream()
                .filter(GameServerConfig.GameServer::isEnabled)
                .filter(server -> server.getVers().equals(vers))
                .findFirst();
    }

    /**
     * Check if a given version is P2P
     *
     * @param vers Version identifier
     * @return true if the version is P2P, false otherwise
     */
    public boolean isP2P(String vers) {
        return gameServerConfig.getServers().stream()
                .filter(GameServerConfig.GameServer::isEnabled)
                .filter(server -> server.getVers().equals(vers))
                .anyMatch(GameServerConfig.GameServer::isP2p);
    }

    /**
     * Get all enabled game servers
     *
     * @return List of enabled game servers
     */
    public List<GameServerConfig.GameServer> getEnabledServers() {
        return gameServerConfig.getServers().stream()
                .filter(GameServerConfig.GameServer::isEnabled)
                .toList();
    }

    /**
     * Generate SSL subject for a given domain
     *
     * @param domain The domain name to include in the SSL subject
     * @return Formatted SSL subject string
     */
    public String generateSslSubject(String domain) {
        return String.format("CN=%s, OU=Global Online Studio, O=Electronic Arts, Inc., ST=California, C=US", domain);
    }

    /**
     * Get the SSL issuer for the EA certificate
     *
     * @return Formatted SSL issuer string
     */
    public String getSslIssuer() {
        return "OU=Online Technology Group, O=Electronic Arts, Inc., L=Redwood City, ST=California, C=US, CN=OTG3 Certificate Authority";
    }
}
