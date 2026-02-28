package eu.eahub.services.server;

import eu.eahub.config.GameServerConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameServerService {

    //    MOH
    public static final String PSP_MOH07 = "PSP_MOH07";
    public static final String PSP_MOH08 = "PSP_MOH08";
    public static final String WII_MOH08 = "WII_MOH08";
    public static final List<String> ALL_MOH = List.of(PSP_MOH07, PSP_MOH08, WII_MOH08);

    //    NFS
    public static final String PC_NFS06 = "PC_NFS06";
    public static final String PS2_NFS06 = "PS2_NFS06";
    public static final String PSP_NFS06 = "PSP_NFS06";
    public static final String PSP_NFS07 = "PSP_NFS07";
    public static final String PSP_NFS08 = "PSP_NFS08";
    public static final String PSP_NFS09 = "PSP_NFS09";
    public static final List<String> ALL_PSP_NFS = List.of(PSP_NFS06, PSP_NFS07, PSP_NFS08, PSP_NFS09);

    //    NHL
    public static final String PSP_NHL07 = "PSP_NHL07";
    public static final String PS2_NHL08 = "PS2_NHL08";
    public static final List<String> ALL_NHL = List.of(PSP_NHL07, PS2_NHL08);

    //    FIFA
    public static final String PS2_FIFA06 = "PS2_FIFA06";
    public static final String PS2_FIFA07 = "PS2_FIFA07";
    public static final String PS2_FIFA08 = "PS2_FIFA08";
    public static final String PSP_UEFA07 = "PSP_UEFA07";
    public static final String PSP_FIFA07 = "PSP_FIFA07";
    public static final String PSP_FIFA08 = "PSP_FIFA08";
    public static final String PSP_FIFA09 = "PSP_FIFA09";
    public static final String PSP_FIFA10 = "PSP_FIFA10";
    public static final String PSP_WORLDCUP06 = "PSP_WORLDCUP06";
    public static final String PSP_WORLDCUP10 = "PSP_WORLDCUP10";
    public static final List<String> ALL_FIFA = List.of(PS2_FIFA06, PS2_FIFA07, PS2_FIFA08, PSP_UEFA07, PSP_FIFA07, PSP_FIFA08, PSP_FIFA09, PSP_FIFA10, PSP_WORLDCUP06, PSP_WORLDCUP10);

    // Games using usersets instead of rooms
    public static final List<String> USERSETS_GAMES = List.of(PC_NFS06, PS2_NFS06);

    private final GameServerConfig gameServerConfig;

    /**
     * Get game name by TCP port
     *
     * @param port TCP port number
     * @return Game name or empty string if not found
     */
    public String getNameByPort(int port) {
        return gameServerConfig.getServers().stream()
                .filter(GameServerConfig.GameServer::isEnabled)
                .filter(server -> server.getPorts().stream()
                        .anyMatch(portConfig -> portConfig.getPort() == port))
                .map(GameServerConfig.GameServer::getName)
                .findFirst()
                .orElse("");
    }

    /**
     * Get a server by its name
     *
     * @param name Game name (arbitrary identifier)
     * @return Optional containing the server if found, otherwise empty
     */
    public Optional<GameServerConfig.GameServer> getServerByName(String name) {
        return gameServerConfig.getServers().stream()
                .filter(GameServerConfig.GameServer::isEnabled)
                .filter(server -> server.getName().equals(name))
                .findFirst();
    }

    /**
     * Check if a given game name is P2P
     *
     * @param name Game name
     * @return true if the game is P2P, false otherwise
     */
    public boolean isP2P(String name) {
        return gameServerConfig.getServers().stream()
                .filter(GameServerConfig.GameServer::isEnabled)
                .filter(server -> server.getName().equals(name))
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
