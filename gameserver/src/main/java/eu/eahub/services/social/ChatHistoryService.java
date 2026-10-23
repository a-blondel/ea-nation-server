package eu.eahub.services.social;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Recent chat kept in memory, so that an abuse report can include what was said
 */
@Service
public class ChatHistoryService {

    private static final int MAX_LINES = 2000;

    private final Deque<Line> lines = new ArrayDeque<>();

    /**
     * A chat line; to is 0 for a message to the whole room
     */
    public record Line(long at, long from, String fromName, long to, String room, String text) {
    }

    public synchronized void add(long from, String fromName, long to, String room, String text) {
        lines.addLast(new Line(Instant.now().getEpochSecond(), from, fromName, to, room == null ? "" : room, text));
        while (lines.size() > MAX_LINES) {
            lines.removeFirst();
        }
    }

    /**
     * Get the recent lines between two players: the accused player's messages to the room, and the private messages
     * between both
     *
     * @param reporter the reporting persona id
     * @param accused  the reported persona id
     * @param room     the room where the report was made
     * @param seconds  how far back to look
     * @param max      the maximum number of lines, the latest ones
     * @return the lines, oldest first
     */
    public synchronized List<Line> between(long reporter, long accused, String room, long seconds, int max) {
        long since = Instant.now().getEpochSecond() - seconds;
        List<Line> found = lines.stream()
                .filter(c -> c.at() >= since
                        && ((c.to() == 0 && c.from() == accused && Objects.equals(c.room(), room == null ? "" : room))
                        || (c.from() == reporter && c.to() == accused)
                        || (c.from() == accused && c.to() == reporter)))
                .toList();
        return found.subList(Math.max(0, found.size() - max), found.size());
    }
}
