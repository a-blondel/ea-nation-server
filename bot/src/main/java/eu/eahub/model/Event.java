package eu.eahub.model;

import eu.eahub.enums.GameGenre;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class Event implements Comparable<Event> {
    private long id;
    private LocalDateTime time;
    private String message;
    private GameGenre gameGenre;

    @Override
    public int compareTo(Event other) {
        int timeComparison = this.time.compareTo(other.time);
        if (timeComparison != 0) {
            return timeComparison;
        }

        int thisMessagePriority = getMessagePriority(this.message);
        int otherMessagePriority = getMessagePriority(other.message);

        if (thisMessagePriority != otherMessagePriority) {
            return Integer.compare(thisMessagePriority, otherMessagePriority);
        }

        return Long.compare(this.id, other.id);
    }

    private int getMessagePriority(String message) {
        if (message.contains(" connected")) return 1;
        if (message.contains(" left game ")) return 2;
        if (message.contains(" joined game ")) return 3;
        if (message.contains(" disconnected")) return 4;
        return 5;
    }
}
