package eu.eahub.enums;

import eu.eahub.enums.GameGenre;
import lombok.Getter;

import java.util.Arrays;

/**
 * Represents individual games supported by the bot.
 * Each game has a name, VERS code (used for API queries), and belongs to a genre.
 */
@Getter
public enum Game {
    // FOOTBALL genre
    UEFA_CHAMPIONS_LEAGUE_2006_2007_PSP("[PSP] UEFA Champions League 2006-2007", "PSP_UEFA07", GameGenre.FOOTBALL),
    FIFA_07_PSP("[PSP] FIFA 07", "PSP_FIFA07", GameGenre.FOOTBALL),
    FIFA_07_PS2("[PS2] FIFA 07", "PS2_FIFA07", GameGenre.FOOTBALL),
    FIFA_08_PSP("[PSP] FIFA 08", "PSP_FIFA08", GameGenre.FOOTBALL),
    FIFA_08_PS2("[PS2] FIFA 08", "PS2_FIFA08", GameGenre.FOOTBALL),
    FIFA_09_PSP("[PSP] FIFA 09", "PSP_FIFA09", GameGenre.FOOTBALL),
    FIFA_10_PSP("[PSP] FIFA 10", "PSP_FIFA10", GameGenre.FOOTBALL),
    FIFA_WORLD_CUP_GERMANY_2006_PSP("[PSP] FIFA World Cup Germany 2006", "PSP_WORLDCUP06", GameGenre.FOOTBALL),
    FIFA_WORLD_CUP_SOUTH_AFRICA_2010_PSP("[PSP] FIFA World Cup South Africa 2010", "PSP_WORLDCUP10", GameGenre.FOOTBALL),

    // FIGHTING genre
    FIGHT_NIGHT_ROUND_3_PSP("[PSP] Fight Night Round 3", "PSP_KOK06", GameGenre.FIGHTING),

    // AMERICAN_FOOTBALL genre
    MADDEN_NFL_07_PSP("[PSP] Madden NFL 07", "PSP_MADDEN07", GameGenre.AMERICAN_FOOTBALL),
    MADDEN_NFL_08_PSP("[PSP] Madden NFL 08", "PSP_MADDEN08", GameGenre.AMERICAN_FOOTBALL),
    MADDEN_NFL_09_PSP("[PSP] Madden NFL 09", "PSP_MADDEN09", GameGenre.AMERICAN_FOOTBALL),
    MADDEN_NFL_10_PSP("[PSP] Madden NFL 10", "PSP_MADDEN10", GameGenre.AMERICAN_FOOTBALL),
    NCAA_FOOTBALL_07_PSP("[PSP] NCAA Football 07", "PSP_NCAA07", GameGenre.AMERICAN_FOOTBALL),

    // BASKETBALL genre
    NBA_LIVE_06_PSP("[PSP] NBA Live 06", "PSP_NBA06", GameGenre.BASKETBALL),
    NBA_LIVE_07_PSP("[PSP] NBA Live 07", "PSP_NBA07", GameGenre.BASKETBALL),
    NBA_LIVE_08_PSP("[PSP] NBA Live 08", "PSP_NBA08", GameGenre.BASKETBALL),

    // RACING genre
    NEED_FOR_SPEED_MOST_WANTED_PC("[PC] Need for Speed: Most Wanted 2005", "PC_NFS06", GameGenre.RACING),
    NEED_FOR_SPEED_MOST_WANTED_PS2("[PS2] Need for Speed: Most Wanted 2005 (Alpha)", "PS2_NFS06", GameGenre.RACING),
    NEED_FOR_SPEED_MOST_WANTED_5_1_0_PSP("[PSP] Need for Speed: Most Wanted 5-1-0", "PSP_NFS06", GameGenre.RACING),
    NEED_FOR_SPEED_CARBON_OWN_THE_CITY_PSP("[PSP] Need for Speed: Carbon - Own the City", "PSP_NFS07", GameGenre.RACING),
    NEED_FOR_SPEED_PROSTREET_PSP("[PSP] Need for Speed: ProStreet", "PSP_NFS08", GameGenre.RACING),
    NEED_FOR_SPEED_UNDERCOVER_PSP("[PSP] Need for Speed: Undercover", "PSP_NFS09", GameGenre.RACING),

    // HOCKEY genre
    NHL_07_PSP("[PSP] NHL 07", "PSP_NHL07", GameGenre.HOCKEY),

    // FPS genre
    MEDAL_OF_HONOR_HEROES_PSP("[PSP] Medal of Honor: Heroes", "PSP_MOH07", GameGenre.FPS),
//    MEDAL_OF_HONOR_HEROES_2_PSP("[PSP] Medal of Honor: Heroes 2", "PSP_MOH08", GameGenre.FPS),
//    MEDAL_OF_HONOR_HEROES_2_WII("[Wii] Medal of Honor: Heroes 2", "WII_MOH08", GameGenre.FPS),

    // GOLF genre
    TIGER_WOODS_PGA_TOUR_07_PSP("[PSP] Tiger Woods PGA Tour 07", "PSP_TW07", GameGenre.GOLF),
    TIGER_WOODS_PGA_TOUR_08_PSP("[PSP] Tiger Woods PGA Tour 08", "PSP_TW08", GameGenre.GOLF),
    TIGER_WOODS_PGA_TOUR_10_PSP("[PSP] Tiger Woods PGA Tour 10", "PSP_TW10", GameGenre.GOLF);

    private final String name;
    private final String vers; // Client VERS code
    private final String serverVers; // Server VERS code (null if same as client)
    private final GameGenre gameGenre;

    // Constructor for games where client and server VERS are the same
    Game(String name, String vers, GameGenre gameGenre) {
        this.name = name;
        this.vers = vers;
        this.serverVers = null;
        this.gameGenre = gameGenre;
    }

    // Constructor for games where client and server VERS are different
    Game(String name, String vers, String serverVers, GameGenre gameGenre) {
        this.name = name;
        this.vers = vers;
        this.serverVers = serverVers;
        this.gameGenre = gameGenre;
    }

    /**
     * Find a game by its client VERS code.
     *
     * @param vers the client VERS code to search for
     * @return the corresponding Game, or null if not found
     */
    public static Game findByVers(String vers) {
        for (Game game : values()) {
            if (game.vers.equals(vers)) {
                return game;
            }
        }
        return null;
    }

    /**
     * Find a game by its server VERS code.
     *
     * @param serverVers the server VERS code to search for
     * @return the corresponding Game, or null if not found
     */
    public static Game findByServerVers(String serverVers) {
        for (Game game : values()) {
            if (game.getEffectiveServerVers().equals(serverVers)) {
                return game;
            }
        }
        return null;
    }

    /**
     * Get all games belonging to a specific genre.
     *
     * @param genre the genre to filter by
     * @return array of games in the specified genre
     */
    public static Game[] getGamesByGenre(GameGenre genre) {
        return Arrays.stream(values())
                .filter(game -> game.gameGenre == genre)
                .toArray(Game[]::new);
    }

    /**
     * Get all client VERS codes for a specific genre.
     *
     * @param genre the genre to get VERS codes for
     * @return array of client VERS codes
     */
    public static String[] getClientVersCodesByGenre(GameGenre genre) {
        return Arrays.stream(values())
                .filter(game -> game.gameGenre == genre)
                .map(Game::getVers)
                .toArray(String[]::new);
    }

    /**
     * Get all server VERS codes for a specific genre.
     *
     * @param genre the genre to get server VERS codes for
     * @return array of server VERS codes
     */
    public static String[] getServerVersCodesByGenre(GameGenre genre) {
        return Arrays.stream(values())
                .filter(game -> game.gameGenre == genre)
                .map(Game::getEffectiveServerVers)
                .distinct()
                .toArray(String[]::new);
    }

    /**
     * Get the effective server VERS code.
     * Returns serverVers if available, otherwise falls back to vers.
     *
     * @return the VERS code used by the server
     */
    public String getEffectiveServerVers() {
        return serverVers != null ? serverVers : vers;
    }
}
