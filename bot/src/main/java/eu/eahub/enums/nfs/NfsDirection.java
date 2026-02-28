package eu.eahub.enums.nfs;

import lombok.Getter;

/**
 * Represents race direction in Need For Speed games.
 */
@Getter
public enum NfsDirection {
    FORWARD(0, "Forward"),
    REVERSE(1, "Reverse");

    private final int value;
    private final String label;

    NfsDirection(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public static NfsDirection fromValue(int value) {
        for (NfsDirection direction : values()) {
            if (direction.value == value) {
                return direction;
            }
        }
        return FORWARD; // Default fallback
    }
}
