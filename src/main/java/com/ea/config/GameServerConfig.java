package com.ea.config;

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
        private String vers;
        private boolean enabled = true;
        private String sdk = "";
        private boolean aries = true;
        private boolean p2p = true;
        private DedicatedConfig dedicated;
        private SslConfig ssl;
        private List<RegionConfig> regions;
    }

    @Data
    public static class DedicatedConfig {
        private String vers;
        private String slus;
        private int port;
    }

    @Data
    public static class SslConfig {
        private boolean enabled = true;
        private String domain;
    }

    @Data
    public static class RegionConfig {
        private String name;
        private int port;
        private List<String> slus;
    }
}
