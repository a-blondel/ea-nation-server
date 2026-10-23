package eu.eahub.services;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JPA test slice for the Tiger Woods services: entities and repositories from the core module, schema from Liquibase
 */
@SpringBootConfiguration
@EntityScan("eu.eahub.entities")
@EnableJpaRepositories("eu.eahub.repositories")
public class TwTestConfig {

    /**
     * Empty the Tiger Woods tables, games and personas, then add one account and persona per name, with ids from 1
     *
     * @param jdbc  the test database
     * @param names the persona names
     */
    public static void reset(JdbcTemplate jdbc, String... names) {
        for (String table : new String[]{"stats.TW_GAME_REPORT", "stats.TW_PERSONA_STATS", "stats.TW_ENTRY", "stats.TW_EVENT", "stats.TW_GOLFER",
                "social.FEEDBACK", "social.BUDDY", "core.GAME_CONNECTION", "core.GAME", "core.PERSONA_CONNECTION", "core.PERSONA", "core.ACCOUNT"}) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.execute("ALTER TABLE core.ACCOUNT ALTER COLUMN ID RESTART WITH 1");
        jdbc.execute("ALTER TABLE core.PERSONA ALTER COLUMN ID RESTART WITH 1");
        for (String name : names) {
            jdbc.update("INSERT INTO core.ACCOUNT (NAME, PASS, MAIL, LOC, BORN, ZIP, GEND, CREATED_ON) VALUES (?, 'x', 'x', 'enUS', '20000101', '0', 'M', CURRENT_TIMESTAMP)", name);
            jdbc.update("INSERT INTO core.PERSONA (ACCOUNT_ID, PERS) SELECT MAX(ID), ? FROM core.ACCOUNT", name);
        }
    }
}
