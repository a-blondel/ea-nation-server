package eu.eahub.enums;

import lombok.Getter;

/**
 * Represents different game categories supported by the bot.
 * Each genre groups related games together for subscription and service management.
 */
@Getter
public enum GameGenre {
    ALL("ALL"),
    FOOTBALL("FOOTBALL"),
    FIGHTING("FIGHTING"),
    AMERICAN_FOOTBALL("AMERICAN_FOOTBALL"),
    BASKETBALL("BASKETBALL"),
    RACING("RACING"),
    HOCKEY("HOCKEY"),
    FPS("FPS"),
    GOLF("GOLF");

    private final String value;

    GameGenre(String value) {
        this.value = value;
    }

    /**
     * Converts a string value to the corresponding GameGenre enum.
     *
     * @param value the string value to convert
     * @return the corresponding GameGenre
     * @throws IllegalArgumentException if the value doesn't match any genre
     */
    public static GameGenre fromValue(String value) {
        for (GameGenre genre : values()) {
            if (genre.value.equalsIgnoreCase(value)) {
                return genre;
            }
        }
        throw new IllegalArgumentException("Unknown game genre: " + value);
    }

}
