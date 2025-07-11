package com.ea.dto;

import com.ea.entities.core.PersonaEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BuddySocketWrapper {
    private Socket socket;
    private String identifier;
    private volatile String lkey;
    private volatile String vers;
    private volatile PersonaEntity personaEntity;
    private volatile String presence; // CHAT = online, PASS(ive) = in-game, AWAY = idle
    private volatile Set<String> buddyList = new HashSet<>();
}
