package eu.eahub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "game")
public class GameServerConfig {
    private List<GameServer> servers;

    @Data
    public static class GameServer {
        private String name;
        private boolean enabled = true;
        private String sdk = "";
        private boolean aries = true;
        private boolean p2p = true;
        private SslConfig ssl;
        private List<PortConfig> ports;
        private RoomConfig rooms;
        // Product name sent in buddy server AUTH when the game uses its own Messenger dialect (TW04)
        private String buddyProduct = "";
    }

    /**
     * Named lobby rooms, for games that list rooms by name (default: one room named after the game)
     */
    @Data
    public static class RoomConfig {
        private List<String> names = List.of();
        private int limit = 50;
        // Players can create rooms whose name matches this pattern (disabled when empty)
        private String createPattern = "";
        private int nameLength = 31;
        private int max = 74;
    }

    @Data
    public static class SslConfig {
        private boolean enabled = true;
        private String domain;
    }

    @Data
    public static class PortConfig {
        private int port;
    }
}
