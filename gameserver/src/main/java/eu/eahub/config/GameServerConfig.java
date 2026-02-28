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
