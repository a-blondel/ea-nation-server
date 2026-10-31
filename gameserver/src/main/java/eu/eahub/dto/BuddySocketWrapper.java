package eu.eahub.dto;

import eu.eahub.entities.core.PersonaEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.net.Socket;
import java.util.HashSet;
import java.util.Map;
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
    private volatile Map<String, String> presenceFields = Map.of(); // Presence sent to buddies (TW04: SHOW, STAT, PROD)
    private volatile String keepAlive = "~png"; // Keep-alive message sent every 20 seconds
}
