package com.ea.frontend;

import com.ea.enums.MohhMap;
import com.ea.repositories.core.GameConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ServerStatusAPI {
    @Autowired
    private final API api;

    @Autowired
    private final GameConnectionRepository gameConnectionRepository;

    @GetMapping("/api/games")
    public ResponseEntity<DTO.MonitorResponse> getGameMonitorJson() {
        List<DTO.GameStatusDTO> gameStats = gameConnectionRepository.findAllActiveGamesWithStats();

        int playersInGame = api.getPlayersInGame();
        int playersInLobby = api.getPlayersInLobby();

        DTO.MonitorResponse response = new DTO.MonitorResponse(
                Instant.now(),
                new DTO.Statistics(
                        gameStats.size(),
                        playersInGame,
                        playersInLobby,
                        playersInGame + playersInLobby
                ),
                gameStats.stream()
                        .map(this::convertToGameInfo)
                        .toList()
        );

        return ResponseEntity.ok(response);
    }

    private DTO.GameInfo convertToGameInfo(DTO.GameStatusDTO game) {
        return new DTO.GameInfo(
                game.id(),
                game.name().replaceAll("\"", ""),
                game.version(),
                MohhMap.getMapNameByHexId(game.params().split(",")[1]),
                game.params(),
                game.pass() != null,
                api.toUTCInstant(game.startTime()),
                getMaxPlayerSize(game.maxPlayers()),
                game.hostName(),
                getActivePlayers(game.id())
        );
    }

    private int getMaxPlayerSize(Integer maxSize) {
        return maxSize != null ? Math.max(maxSize - 1, 0) : 0;
    }

    private List<DTO.PlayerInfo> getActivePlayers(Long gameId) {
        return gameConnectionRepository.findActivePlayersByGameId(gameId)
                .stream()
                .map(player -> new DTO.PlayerInfo(
                        player.playerName().replaceAll("\"", ""),
                        player.isHost(),
                        api.toUTCInstant(player.startTime()),
                        api.formatDuration(api.toUTCInstant(player.startTime()))
                ))
                .toList();
    }
}