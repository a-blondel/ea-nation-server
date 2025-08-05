package com.ea.frontend;

import com.ea.repositories.core.GameConnectionRepository;
import com.ea.repositories.core.PersonaConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static com.ea.services.server.GameServerService.MOH07_OR_UHS;

@Service
@RequiredArgsConstructor
public class API {
    private final GameConnectionRepository gameConnectionRepository;
    private final PersonaConnectionRepository personaConnectionRepository;

    public int getPlayersInGame() {
        return gameConnectionRepository.countPlayersInGame(MOH07_OR_UHS);
    }

    public int getPlayersInLobby() {
        return personaConnectionRepository.countPlayersInLobby(MOH07_OR_UHS);
    }

    public Instant toUTCInstant(LocalDateTime localDateTime) {
        return localDateTime != null ? localDateTime.atZone(ZoneId.systemDefault()).toInstant() : null;
    }

    public String formatDuration(Instant startTime) {
        if (startTime == null) {
            return "N/A";
        }

        long minutes = ChronoUnit.MINUTES.between(startTime, Instant.now());
        if (minutes < 0) {
            return "0 min";
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    public String formatSeconds(int seconds) {
        if (seconds < 0) {
            return "0D 0H";
        }
        int days = seconds / (60 * 60 * 24);
        seconds %= (60 * 60 * 24);
        int hours = seconds / (60 * 60);
        return String.format("%dD %dH", days, hours);
    }
}