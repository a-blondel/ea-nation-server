package eu.eahub.services.discord;

import eu.eahub.entities.core.GameConnectionEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaConnectionEntity;
import eu.eahub.enums.Game;
import eu.eahub.enums.GameGenre;
import eu.eahub.repositories.core.GameRepository;
import eu.eahub.repositories.core.PersonaConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for generating status message content for different game genres.
 * This service creates formatted Markdown content showing lobby players, active games, and game participants.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusMessageContentService {

    private final PersonaConnectionRepository personaConnectionRepository;
    private final GameRepository gameRepository;

    /**
     * Generate status message content for a specific game genre.
     *
     * @param gameGenre the game genre to generate status for
     * @return formatted Markdown content
     */
    public String generateStatusContent(GameGenre gameGenre) {
        log.debug("Generating status content for genre: {}", gameGenre);

        // Handle ALL genre - generate summary view
        if (gameGenre == GameGenre.ALL) {
            return generateSummaryContent();
        }

        // Get all games for this genre
        Game[] games = Game.getGamesByGenre(gameGenre);
        if (games.length == 0) {
            return "No games found for genre: " + gameGenre.name();
        }

        // Get client and server VERS codes
        List<String> clientVersCodes = Arrays.asList(Game.getClientVersCodesByGenre(gameGenre));
        List<String> serverVersCodes = Arrays.asList(Game.getServerVersCodesByGenre(gameGenre));

        // Fetch data
        List<PersonaConnectionEntity> lobbyPlayers = personaConnectionRepository.findPlayersInLobbyByVers(clientVersCodes);
        List<GameEntity> activeGames = gameRepository.findActiveGamesByServerVers(serverVersCodes);

        // Group data by game
        Map<Game, List<PersonaConnectionEntity>> playersByGame = groupPlayersByGame(lobbyPlayers);
        Map<Game, List<GameEntity>> gamesByGame = groupActiveGamesByGame(activeGames, games);

        // Build the status message
        StringBuilder content = new StringBuilder();

        // Display status for ALL games in enum order (even without activity)
        for (Game game : games) {
            List<PersonaConnectionEntity> gameLobbyPlayers = playersByGame.getOrDefault(game, List.of());
            List<GameEntity> gameActiveGames = gamesByGame.getOrDefault(game, List.of());

            addGameSection(content, game, gameLobbyPlayers, gameActiveGames);
        }

        return content.toString();
    }

    /**
     * Group lobby players by their corresponding Game enum.
     */
    private Map<Game, List<PersonaConnectionEntity>> groupPlayersByGame(List<PersonaConnectionEntity> lobbyPlayers) {
        return lobbyPlayers.stream()
                .collect(Collectors.groupingBy(player -> Game.findByVers(player.getVers())));
    }

    /**
     * Group active games by their corresponding Game enum.
     */
    private Map<Game, List<GameEntity>> groupActiveGamesByGame(List<GameEntity> activeGames, Game[] genreGames) {
        return activeGames.stream()
                .collect(Collectors.groupingBy(gameEntity -> Game.findByServerVers(gameEntity.getVers())));
    }

    /**
     * Add a complete section for a specific game.
     */
    private void addGameSection(StringBuilder content, Game game,
                                List<PersonaConnectionEntity> lobbyPlayers,
                                List<GameEntity> activeGames) {
        content.append("## ").append(game.getName()).append("\n\n");

        // Lobby players subsection
        content.append("**Players in Lobby (").append(lobbyPlayers.size()).append(")**\n");
        if (!lobbyPlayers.isEmpty()) {
            for (PersonaConnectionEntity player : lobbyPlayers) {
                content.append("🔸 ").append(player.getPersona().getPers()).append("\n");
            }
        } else {
            content.append("*No players in lobby*\n");
        }
        content.append("\n");

        // Active games subsection
        content.append("**Active Games (").append(activeGames.size()).append(")**\n");
        if (!activeGames.isEmpty()) {
            for (GameEntity gameEntity : activeGames) {
                addGameDetails(content, gameEntity);
                if (activeGames.indexOf(gameEntity) < activeGames.size() - 1)
                    content.append("\n");
            }
        } else {
            content.append("*No active games*\n");
        }
        content.append("\n");
    }

    /**
     * Add details for a specific game instance.
     */
    private void addGameDetails(StringBuilder content, GameEntity game) {
        String status = determineGameStatus(game);
        int playerCount = game.getGameConnections() != null ?
                (int) game.getGameConnections().stream().filter(gc -> gc.getEndTime() == null).count() : 0;

        // Game header with status and player count - using dash and code quotes
        content.append("🔹 `").append(game.getName()).append("` ");
        content.append(status).append(" ");
        content.append("(").append(playerCount);
        if (game.getMaxsize() > 0) {
            content.append("/").append(game.getMaxsize());
        }
        content.append(" players)\n");

        // List players in game with double -- indentation
        if (game.getGameConnections() != null && !game.getGameConnections().isEmpty()) {
            List<GameConnectionEntity> activePlayers = game.getGameConnections().stream()
                    .filter(gc -> gc.getEndTime() == null)
                    .sorted((a, b) -> Boolean.compare(b.isHost(), a.isHost())) // Hosts first
                    .toList();

            if (!activePlayers.isEmpty()) {
                for (GameConnectionEntity connection : activePlayers) {
                    String playerName = connection.getPersonaConnection().getPersona().getPers();
                    content.append("  🔸 ");
                    if (connection.isHost()) {
                        content.append(playerName).append(" 👑");
                    } else {
                        content.append(playerName);
                    }
                    content.append("\n");
                }
            }
        }
    }

    /**
     * Determine the status of a game based on its properties.
     */
    private String determineGameStatus(GameEntity game) {
        if (game.isStarted()) {
            return "🟢 Started";
        } else {
            return "🟡 Waiting for players";
        }
    }

    /**
     * Generate summary content showing total players per game genre.
     *
     * @return formatted Markdown content with player counts per genre
     */
    private String generateSummaryContent() {
        log.debug("Generating summary content for all genres");

        StringBuilder content = new StringBuilder();
        content.append("## Player count\n\n\n");

        int totalPlayers = 0;

        for (GameGenre genre : GameGenre.values()) {
            // Skip ALL genre itself
            if (genre == GameGenre.ALL) {
                continue;
            }

            int genreCount = getPlayerCountForGenre(genre);
            totalPlayers += genreCount;

            String emoji = getEmojiForGenre(genre);
            String genreName = getDisplayNameForGenre(genre);

            content.append("- ").append(emoji).append(" ").append(genreName).append(" - ");
            content.append(genreCount).append("\n");
        }

        content.append("\n---\n");
        content.append("Total: ").append(totalPlayers);
        content.append("\n");

        return content.toString();
    }

    /**
     * Get the total player count for a specific genre (lobby + in-game combined).
     * For FPS games, hosts are not counted as they don't play (they only host).
     * For other games, hosts are counted as active players.
     */
    private int getPlayerCountForGenre(GameGenre genre) {
        Game[] games = Game.getGamesByGenre(genre);
        if (games.length == 0) {
            return 0;
        }

        // Get client and server VERS codes
        List<String> clientVersCodes = Arrays.asList(Game.getClientVersCodesByGenre(genre));
        List<String> serverVersCodes = Arrays.asList(Game.getServerVersCodesByGenre(genre));

        // Count players in lobby
        List<PersonaConnectionEntity> lobbyPlayers = personaConnectionRepository.findPlayersInLobbyByVers(clientVersCodes);
        int lobbyCount = lobbyPlayers.size();

        // Count players in active games
        List<GameEntity> activeGames = gameRepository.findActiveGamesByServerVers(serverVersCodes);
        int inGameCount = 0;
        boolean excludeHosts = (genre == GameGenre.FPS); // Don't count hosts for FPS games (dedicated servers)

        for (GameEntity game : activeGames) {
            if (game.getGameConnections() != null) {
                inGameCount += (int) game.getGameConnections().stream()
                        .filter(gc -> gc.getEndTime() == null)
                        .filter(gc -> !excludeHosts || !gc.getPersonaConnection().isHost())
                        .count();
            }
        }

        return lobbyCount + inGameCount;
    }

    /**
     * Get the emoji for a game genre.
     */
    private String getEmojiForGenre(GameGenre genre) {
        return switch (genre) {
            case ALL -> "🎮";
            case FOOTBALL -> "⚽";
            case FIGHTING -> "🥊";
            case AMERICAN_FOOTBALL -> "🏈";
            case BASKETBALL -> "🏀";
            case RACING -> "🏎️";
            case HOCKEY -> "🏒";
            case FPS -> "🔫";
            case GOLF -> "⛳";
        };
    }

    /**
     * Get the display name for a game genre.
     */
    private String getDisplayNameForGenre(GameGenre genre) {
        return switch (genre) {
            case ALL -> "All Genres";
            case FOOTBALL -> "Football (FIFA, UEFA)";
            case FIGHTING -> "Fighting (Fight Night)";
            case AMERICAN_FOOTBALL -> "American Football (Madden, NCAA)";
            case BASKETBALL -> "Basketball (NBA Live)";
            case RACING -> "Racing (Need for Speed)";
            case HOCKEY -> "Hockey (NHL)";
            case FPS -> "FPS (Medal of Honor)";
            case GOLF -> "Golf (Tiger Woods PGA)";
        };
    }
}
