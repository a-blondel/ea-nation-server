package eu.eahub.services.core;

import eu.eahub.dto.SocketData;
import eu.eahub.steps.SocketWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class LeagueService {

    private final SocketWriter socketWriter;

    /**
     * ilgs - Get schedule information for all Interactive Leagues schedules
     * No idea how to implement leagues properly, there is likely a ton of data to manage (and persist in database)
     * PS2 clients can be soft locked if the response is empty
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void ilgs(Socket socket, SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][]{
                {"NUM", "1"},
                {"SID", "1"},
                {"SNAME", "Default"},
                {"SIMAGE", "default_image"},
                {"SPKSTART", "480"},
                {"SPKEND", "1320"},
                {"START", "2001.11.11-11:11:11"},
                {"END", "2099.11.11-11:11:11"},
                {"MINLVL", "1"},
                {"MAXLVL", "100"},
                {"SLOT", "100"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    /**
     * ilgf - Get user's selected favorite teams for any Interactive Leagues schedules
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void ilgf(Socket socket, SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][]{
                {"NUM", "1"},
                {"SID", "1"},
                {"TID", "1"},
                {"CHANGES", "0"},
                {"MAXCHANGES", "5"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    /**
     * ilgt - Get list of allowed favourite teams for a given schedule
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void ilgt(Socket socket, SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][]{
                {"NUM", "1"},
                {"TID", "1"},
                {"COUNTS", "1"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    /**
     * ilsc - Request Interactive Leagues scheduled games snapshot
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void ilsc(Socket socket, SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][]{
                {"CHAN", "1"},
                {"NUM", "0"}, // Number of games found, send +ils for each one
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    /**
     * ilou - Get number of users online for a given favorite team
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void ilou(Socket socket, SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][]{
                {"NUM", "0"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

}
