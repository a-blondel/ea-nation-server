package eu.eahub.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In most game titles, a room is used to manage players and games.
 * On the opposite of other elements like games, a room is not physically stored in the database.
 */
@Getter
@Setter
@NoArgsConstructor
public class Room {
    private Long id;
    private String name;
    private String vers;
    private String flags = "CK";
    private String password = "";
    private int limit = 50;
    private boolean created; // Created by a player, removed once empty
    private Set<Long> gameIds = ConcurrentHashMap.newKeySet();
    private Set<Long> personaIds = ConcurrentHashMap.newKeySet();
}
