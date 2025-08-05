package com.ea.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

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
    private Set<Long> gameIds = new HashSet<>();
    private Set<Long> personaIds = new HashSet<>();
}
