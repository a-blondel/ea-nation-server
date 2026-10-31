package eu.eahub.services;

import eu.eahub.dirtysdk.LobbyTagField;
import eu.eahub.steps.SocketWriter;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Captures the frames a mocked SocketWriter is asked to send with writeTagFields and writeText
 */
public class TwFrames {

    private final List<Frame> frames = new ArrayList<>();

    /**
     * A captured frame: the message id (verb), its fields, and the raw text for a text body
     */
    public record Frame(Socket socket, String verb, Map<String, String> fields, String text) {
    }

    /**
     * Create a SocketWriter mock whose frames are captured
     *
     * @return the mock
     */
    public SocketWriter writer() {
        SocketWriter writer = mock(SocketWriter.class);
        capture(writer);
        return writer;
    }

    /**
     * Capture the frames of an existing SocketWriter mock
     *
     * @param writer the mock
     */
    public void capture(SocketWriter writer) {
        doAnswer(call -> frames.add(new Frame(call.getArgument(0), call.getArgument(1), Map.copyOf(call.<Map<String, String>>getArgument(2)), null)))
                .when(writer).writeTagFields(any(), anyString(), anyMap());
        doAnswer(call -> frames.add(new Frame(call.getArgument(0), call.getArgument(1), LobbyTagField.decode(call.getArgument(2)), call.getArgument(2))))
                .when(writer).writeText(any(), anyString(), anyString());
    }

    public List<Frame> all() {
        return frames;
    }

    public Frame first() {
        return frames.getFirst();
    }

    public Frame last() {
        return frames.getLast();
    }

    public void clear() {
        frames.clear();
    }

    /**
     * Get the frames sent to a socket with a message id
     *
     * @param socket the socket
     * @param verb   the message id
     * @return the frames, in order
     */
    public List<Frame> received(Socket socket, String verb) {
        return frames.stream().filter(frame -> frame.socket() == socket && frame.verb().equals(verb)).toList();
    }
}
