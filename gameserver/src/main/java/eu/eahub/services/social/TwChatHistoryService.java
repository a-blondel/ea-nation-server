package eu.eahub.services.social;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Recent TW04 chat kept in memory, so that an abuse report can include what was said
 */
@Service
public class TwChatHistoryService {

    private static final int MAX_LINES = 2000;

    private final Deque<Line> lines = new ArrayDeque<>();

    /**
     * A chat line; to is 0 for a message to the whole room
     */
    private static class Line {
        private final long at = Instant.now().getEpochSecond();
        private final long from;
        private final String fromName;
        private final long to;
        private final String room;
        private final String text;

        private Line(long from, String fromName, long to, String room, String text) {
            this.from = from;
            this.fromName = fromName;
            this.to = to;
            this.room = room;
            this.text = text;
        }
    }

    /**
     * Keep a chat line
     *
     * @param from     the sender's persona id
     * @param fromName the sender's persona name
     * @param to       the recipient's persona id, or 0 for the whole room
     * @param room     the sender's room, or null
     * @param text     the message
     */
    public synchronized void add(long from, String fromName, long to, String room, String text) {
        lines.addLast(new Line(from, fromName, to, room == null ? "" : room, text));
        while (lines.size() > MAX_LINES) {
            lines.removeFirst();
        }
    }

    /**
     * Get the recent chat between two players, formatted one message per line: the accused player's messages to the
     * room where the report was made (or to any room when it was made outside a room), and the private messages between
     * both
     *
     * @param reporter the reporting persona id
     * @param accused  the reported persona id
     * @param room     the room where the report was made, or an empty string
     * @param seconds  how far back to look
     * @param max      the maximum number of lines, the latest ones
     * @return the lines, oldest first
     */
    public synchronized List<String> transcript(long reporter, long accused, String room, long seconds, int max) {
        long since = Instant.now().getEpochSecond() - seconds;
        List<Line> found = lines.stream()
                .filter(line -> line.at >= since)
                .filter(line -> (line.to == 0 && line.from == accused && (room.isEmpty() || room.equals(line.room)))
                        || (line.from == reporter && line.to == accused)
                        || (line.from == accused && line.to == reporter))
                .toList();
        return found.subList(Math.max(0, found.size() - max), found.size()).stream()
                .map(line -> line.fromName + (line.to == 0 ? " (room): " : " (private): ") + line.text.replace('\n', ' '))
                .toList();
    }
}
