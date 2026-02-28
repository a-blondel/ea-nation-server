package eu.eahub.utils;

import eu.eahub.enums.Game;
import eu.eahub.enums.GameGenre;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utility class for game VERS and genre operations.
 * This class provides helper methods to work with game identifiers and categories.
 */
@Slf4j
public class GameVersUtils {

    /**
     * Get all VERS codes for a specific game genre.
     *
     * @param genre the game genre
     * @return list of VERS codes for games in the genre
     */
    public static List<String> getVersForGenre(GameGenre genre) {
        return Arrays.stream(Game.getGamesByGenre(genre))
                .map(Game::getVers)
                .toList();
    }

    /**
     * Get all server VERS codes for a specific game genre.
     *
     * @param genre the game genre
     * @return list of server VERS codes for games in the genre
     */
    public static List<String> getServerVersForGenre(GameGenre genre) {
        return Arrays.stream(Game.getServerVersCodesByGenre(genre)).toList();
    }

    /**
     * Get all VERS codes (both client and server) for a specific game genre.
     * This combines client VERS and server VERS to handle cases where they are different.
     *
     * @param genre the game genre
     * @return list of all VERS codes (client and server) for games in the genre
     */
    public static List<String> getAllVersForGenre(GameGenre genre) {
        List<String> clientVers = getVersForGenre(genre);
        List<String> serverVers = getServerVersForGenre(genre);

        return Stream.concat(clientVers.stream(), serverVers.stream())
                .distinct()
                .toList();
    }

    /**
     * Get all game names for a specific game genre.
     *
     * @param genre the game genre
     * @return list of game names for games in the genre
     */
    public static List<String> getNamesForGenre(GameGenre genre) {
        return Arrays.stream(Game.getGamesByGenre(genre))
                .map(Game::getName)
                .toList();
    }

    /**
     * Get the game genre for a given VERS code.
     *
     * @param vers the VERS code
     * @return the corresponding game genre, or null if not found
     */
    public static GameGenre getGenreForVers(String vers) {
        Game game = Game.findByVers(vers);
        if (game == null) {
            game = Game.findByServerVers(vers);
        }
        return game != null ? game.getGameGenre() : null;
    }
}
