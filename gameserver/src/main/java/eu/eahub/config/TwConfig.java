package eu.eahub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Tiger Woods PGA Tour 2004 settings, bound from the tw04.* properties
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tw04")
public class TwConfig {

    private String timezone = "UTC";
    private String news = "Welcome back to Tiger Woods PGA Tour 2004 online.";
    private String newsFile = "";
    private boolean autoNews = true;
    private Relay relay = new Relay();

    @Getter
    @Setter
    public static class Relay {
        private boolean enabled;
        private String bindAddress = "0.0.0.0";
        private String publicAddress = "";
        private int port = 3658;
    }

    /**
     * Get the clock that decides the tournament day
     *
     * @return A system clock in the configured timezone
     */
    public Clock clock() {
        return Clock.system(ZoneId.of(timezone));
    }
}
