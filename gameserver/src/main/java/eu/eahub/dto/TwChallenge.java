package eu.eahub.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * A Tiger Woods head-to-head challenge: the challenger hosts, the opponent accepts, then both mark themselves ready
 */
@Getter
@Setter
public class TwChallenge {
    // MFLG, the setup's mode word: 1 Match Play, 2 Stroke Play, and the RANKED GAME setting
    public static final int MODE_MATCH = 1;
    public static final int MODE_STROKE = 2;
    public static final int MODE_RANKED = 0x20;

    private final SocketWrapper host;
    private final SocketWrapper guest;
    private final Long roomId; // The room both players were in, or null for a challenge from Messenger
    private final long created = System.nanoTime();
    private volatile boolean accepted;
    private volatile boolean hostReady;
    private volatile boolean guestReady;
    private volatile boolean started;
    private volatile Integer course;
    private volatile Integer modeFlags;
    private volatile Long gameId;

    public TwChallenge(SocketWrapper host, SocketWrapper guest, Long roomId) {
        this.host = host;
        this.guest = guest;
        this.roomId = roomId;
    }

    /**
     * Get the other player
     *
     * @param wrapper one of the players
     * @return the opponent
     */
    public SocketWrapper getOpponent(SocketWrapper wrapper) {
        return wrapper == host ? guest : host;
    }

    /**
     * Whether the setup names the mode (Match Play or Stroke Play)
     *
     * @return true if MFLG was sent with the challenge
     */
    public boolean hasMode() {
        return modeFlags != null && (modeFlags & (MODE_MATCH | MODE_STROKE)) != 0;
    }

    /**
     * Whether the setup says Stroke Play
     *
     * @return true for Stroke Play, false for Match Play or when the setup does not say
     */
    public boolean isStrokePlay() {
        return modeFlags != null && (modeFlags & (MODE_MATCH | MODE_STROKE)) == MODE_STROKE;
    }
}
