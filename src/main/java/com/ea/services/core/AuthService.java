package com.ea.services.core;

import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.services.server.GameServerService;
import com.ea.steps.SocketWriter;
import com.ea.utils.Props;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.utils.SocketUtils.SPACE_CHAR;
import static com.ea.utils.SocketUtils.getValueFromSocket;

@Slf4j
@RequiredArgsConstructor
@Service
public class AuthService {

    private final Props props;
    private final PersonaService personaService;
    private final GameServerService gameServerService;
    private final SocketWriter socketWriter;

    public void dir(Socket socket, SocketData socketData) {
        String vers = getValueFromSocket(socketData.getInputMessage(), "VERS");
        String slus = getValueFromSocket(socketData.getInputMessage(), "SLUS");

        Map<String, String> content = Stream.of(new String[][]{
                // { "DIRECT", "0" }, // 0x8001FC04
                // if DIRECT == 0 then read ADDR and PORT
                {"ADDR", props.getTcpHost()}, // 0x8001FC18
                {"PORT", String.valueOf(gameServerService.getTcpPort(vers, slus))}, // 0x8001fc30
                // { "SESS", "0" }, // 0x8001fc48 %s-%s-%08x 0--498ea96f
                // { "MASK", "0" }, // 0x8001fc60
                // if ADDR == 0 then read DOWN
                // { "DOWN", "0" }, // 0x8001FC90
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    public void addr(Socket socket, SocketData socketData) {
        socketWriter.write(socket, socketData);
    }

    public void skey(Socket socket, SocketData socketData) {
        Map<String, String> content = Collections.singletonMap("SKEY", "$51ba8aee64ddfacae5baefa6bf61e009");
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    public void news(Socket socket, SocketData socketData) {
        String tosUrl = props.getDnsName() + "/legalapp/webterms/us/fr/pc/";
        String rosterUrl = props.getDnsName() + "/roster";
        Map<String, String> content = Stream.of(new String[][]{
                {"BUDDY_SERVER", props.getTcpHost()},
                {"BUDDY_PORT", String.valueOf(props.getTcpBuddyPort())},
                {"CONTEXT", "buddy"},
                {"TOSAC_URL", tosUrl},
                {"TOSA_URL", tosUrl},
                {"TOS_URL", tosUrl},
                {"FAQ_URL", tosUrl},
                {"EACONNECT_WEBOFFER_URL", tosUrl},
                {"ROSTER_URL", rosterUrl}, // Required by NHL/FIFA 07 (roster download isn't implemented, but it is required by the game)
                {"ROSTER_VER", "1.0"}, // Trick to skip roster download for NHL 07
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    public void sele(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String stats = getValueFromSocket(socketData.getInputMessage(), "STATS");
        String inGame = getValueFromSocket(socketData.getInputMessage(), "INGAME");
        String rooms = getValueFromSocket(socketData.getInputMessage(), "ROOMS", SPACE_CHAR); // Not the same separator

        Map<String, String> content;
        // Request separates attributes either by 0x20 or 0x0a...
        if (null == stats && null == inGame) { // If both NULL, then the separator is 0x20, so we know which request was sent
            content = Stream.of(new String[][]{
                    {"MORE", "0"},
                    {"SLOTS", "4"},
                    {"STATS", "0"},
//                    { "GAMES", "1" },
//                    { "ROOMS", "1" },
//                    { "USERS", "1" },
//                    { "MESGS", "1" },
//                    { "MYGAME", "1" },
//                    { "ASYNC", "1" },
            }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        } else {
            String myGame = getValueFromSocket(socketData.getInputMessage(), "MYGAME");
            String async = getValueFromSocket(socketData.getInputMessage(), "ASYNC");

            if ("1".equals(inGame)) {
                String games = getValueFromSocket(socketData.getInputMessage(), "GAMES");
                rooms = getValueFromSocket(socketData.getInputMessage(), "ROOMS");
                String mesgs = getValueFromSocket(socketData.getInputMessage(), "MESGS");
                String mesgTypes = getValueFromSocket(socketData.getInputMessage(), "MESGTYPES");
                String users = getValueFromSocket(socketData.getInputMessage(), "USERS");
                String userSets = getValueFromSocket(socketData.getInputMessage(), "USERSETS");
                content = Stream.of(new String[][]{
                        {"INGAME", inGame},
                        {"MESGS", mesgs},
                        {"MESGTYPES", mesgTypes},
                        {"USERS", users},
                        {"GAMES", games},
                        {"MYGAME", myGame},
                        {"ROOMS", rooms},
                        {"ASYNC", async},
                        {"USERSETS", userSets},
                        {"STATS", stats},
                }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
            } else {
                content = Stream.of(new String[][]{
                        {"INGAME", inGame},
                }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
            }
        }

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData, SPACE_CHAR);

        if (null != stats || null != inGame) {
            personaService.who(socket, socketWrapper);
        }
    }

}
