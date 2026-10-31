package eu.eahub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tiger Woods PGA Tour 2004 settings, bound from the tw04.* properties
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tw04")
public class TwConfig {

    private String news = "Welcome back to Tiger Woods PGA Tour 2004 online.";
}
