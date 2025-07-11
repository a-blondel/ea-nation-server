package com.ea.frontend;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public class DTO {
    public record MonitorResponse(
            Instant timestamp,
            Statistics stats,
            List<GameInfo> activeGames
    ) {}

    public record Statistics(
            int activeGames,
            int playersInGame,
            int playersInLobby,
            int totalPlayers
    ) {}

    public record GameInfo(
            Long id,
            String name,
            String version,
            String map,
            String params,
            boolean hasPassword,
            Instant startTime,
            Integer maxPlayers,
            String hostName,
            List<PlayerInfo> activePlayers
    ) {}

    public record PlayerInfo(
            String name,
            boolean isHost,
            Instant joinTime,
            String playTime
    ) {}

    // Database DTOs
    public record GameStatusDTO(
            Long id,
            String name,
            String version,
            String params,
            String pass,
            LocalDateTime startTime,
            Integer maxPlayers,
            String hostName,
            Long playerCount
    ) {}

    public record PlayerInfoDTO(
            String playerName,
            boolean isHost,
            LocalDateTime startTime
    ) {}

    public record ConnectionStatsDTO(
            long totalConnections,
            long nonHostConnections,
            long hostConnections
    ) {}

    public record LeaderboardPlayerDTO(
            String username,
            int rank,
            int kills,
            int deaths,
            int headshots,
            int playTime,
            String playTimeString,
            int wins,
            int losses,
            String mostPlayedMap,
            String mostPlayedMode,
            String favoriteTeam,
            double accuracy,
            int dmGames,
            int tdmGames,
            int domGames,
            int demGames,
            int htlGames,
            int blGames,
            int infGames
    ) {}
}
