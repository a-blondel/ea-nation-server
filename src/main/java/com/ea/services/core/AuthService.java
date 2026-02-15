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
    private final UserSetService userSetService;

    public void dir(Socket socket, SocketData socketData) {
        String vers = getValueFromSocket(socketData.getInputMessage(), "VERS");
        String slus = getValueFromSocket(socketData.getInputMessage(), "SLUS");

        int port = gameServerService.getTcpPort(vers, slus);
        if (port == -1) {
            log.warn("No TCP port found for VERS={} SLUS={}", vers, slus);
        }

        Map<String, String> content = Stream.of(new String[][]{
                // { "DIRECT", "0" }, // 0x8001FC04
                // if DIRECT == 0 then read ADDR and PORT
                {"ADDR", props.getTcpHost()}, // 0x8001FC18
                {"PORT", String.valueOf(port)}, // 0x8001fc30
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
        String tosUrl = props.getDnsName() + "/tos/";
        String newsUrl = props.getDnsName() + "/news/";
        String faqUrl = props.getDnsName() + "/faq/";
        String eaconnectUrl = props.getDnsName() + "/eaconnect/";
        String rosterUrl = props.getDnsName() + "/roster";

        String vers = gameServerService.getVersByPort(socket.getLocalPort());
        log.debug("Detected VERS={} for port {}", vers, socket.getLocalPort());
        if (vers.contains("PS2")) {
            tosUrl = props.getDnsName() + "/tos-ps2/";
        }
        Map<String, String> content = Stream.of(new String[][]{
                {"BUDDY_SERVER", props.getTcpHost()},
                {"BUDDYSERVERNAME", props.getTcpHost()}, // For SSX3
                {"BUDDY_PORT", String.valueOf(props.getTcpBuddyPort())},
                {"BUDDYPORT", String.valueOf(props.getTcpBuddyPort())}, // For SSX3
                {"CONTEXT", "buddy"},
                {"TOSAC_URL", tosUrl},
                {"TOSA_URL", tosUrl},
                {"TOS_URL", tosUrl},
                {"NEWS_URL", newsUrl},
                {"FAQ_URL", faqUrl},
                {"EACONNECT_WEBOFFER_URL", eaconnectUrl},
                {"ROSTER_URL", rosterUrl}, // Required by NHL/FIFA 07 (roster download isn't implemented, but it is required by the game)
                {"ROSTER_VER", "1.0"}, // Trick to skip roster download for NHL 07
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        String name = getValueFromSocket(socketData.getInputMessage(), "NAME");
        // if name exists and is a number
        if (name != null && name.matches("\\d+")) {
            socketData.setIdMessage("newsnew" + name);
        }

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    public void sele(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String stats = getValueFromSocket(socketData.getInputMessage(), "STATS");
        String inGame = getValueFromSocket(socketData.getInputMessage(), "INGAME");
        String rooms = getValueFromSocket(socketData.getInputMessage(), "ROOMS", SPACE_CHAR); // Not the same separator
        String userSets = getValueFromSocket(socketData.getInputMessage(), "USERSETS");

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
            String games = getValueFromSocket(socketData.getInputMessage(), "GAMES");
            String async = getValueFromSocket(socketData.getInputMessage(), "ASYNC");
            String mesgs = getValueFromSocket(socketData.getInputMessage(), "MESGS");
            String userset0 = getValueFromSocket(socketData.getInputMessage(), "USERSET0");
            String userset1 = getValueFromSocket(socketData.getInputMessage(), "USERSET1");
            String userset2 = getValueFromSocket(socketData.getInputMessage(), "USERSET2");
            String userset3 = getValueFromSocket(socketData.getInputMessage(), "USERSET3");
            rooms = getValueFromSocket(socketData.getInputMessage(), "ROOMS");
            String users = getValueFromSocket(socketData.getInputMessage(), "USERS");
            String mesgTypes = getValueFromSocket(socketData.getInputMessage(), "MESGTYPES");

            if ("1".equals(inGame)) {
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
                        {"USERSET0", userset0 != null ? userset0 : ""},
                        {"USERSET1", userset1 != null ? userset1 : ""},
                        {"USERSET2", userset2 != null ? userset2 : ""},
                        {"USERSET3", userset3 != null ? userset3 : ""},
                        {"STATS", stats},
                }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
            } else {
                if (inGame != null) {
                    content = Stream.of(new String[][]{
                            {"INGAME", inGame},
                            {"GAMES", games != null ? games : "0"},
                            {"MYGAME", myGame != null ? myGame : "0"},
                            {"ROOMS", rooms != null ? rooms : "0"},
                            {"USERS", users != null ? users : "0"},
                            {"USERSETS", userSets != null ? userSets : "0"},
                            {"MESGS", mesgs != null ? mesgs : "1"},
                            {"MESGTYPES", mesgTypes != null ? mesgTypes : "GPY"},
                            {"ASYNC", async != null ? async : "0"},
                            {"STATS", stats != null ? stats : "0"},
                            {"SLOTS", "3"},
                    }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
                } else if (myGame != null) {
                    // When client sends MYGAME=1 with additional fields (USERSETS, ASYNC, MESGS, etc.)
                    // we should return all requested fields
                    if (async != null || mesgs != null || userSets != null || rooms != null) {
                        content = Stream.of(new String[][]{
                                {"MYGAME", myGame},
                                {"GAMES", games != null ? games : "0"},
                                {"STATS", stats},
                                {"ASYNC", async != null ? async : "0"},
                                {"MESGS", mesgs != null ? mesgs : "0"},
                                {"MESGTYPES", mesgTypes != null ? mesgTypes : "GPY"},
                                {"ROOMS", rooms != null ? rooms : "0"},
                                {"USERSETS", userSets != null ? userSets : "0"},
                                {"USERSET0", userset0 != null ? userset0 : ""},
                                {"USERS", users != null ? users : "0"},
                                {"SLOTS", "3"},
                                {"INGAME", "0"},
                        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
                    } else {
                        content = Stream.of(new String[][]{
                                {"MYGAME", myGame},
                                {"STATS", stats},
                        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
                    }
                } else {
                    content = Collections.emptyMap();
                }

            }
        }

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData, SPACE_CHAR);

        if ((null != stats || null != inGame) && socketWrapper.getPersonaEntity() != null) {
            personaService.who(socket, socketWrapper);
        }

        // If USERSETS=1, send +ust and +usm for each member of the UserSet
        if ("1".equals(userSets) && socketWrapper.getUserSetId() != null) {
            userSetService.sendUserSetUpdatesToSocket(socket, socketWrapper.getUserSetId());
        }
    }

    /**
     * Set private message mode.
     *
     * @param socket     the socket
     * @param socketData the socket data
     */
    public void priv(Socket socket, SocketData socketData) {
        String mode = getValueFromSocket(socketData.getInputMessage(), "MODE");
        String result = mode.equals("off") ? "0" : "1";
        Map<String, String> content = Collections.singletonMap("PRIV", result);
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    /**
     * qdef - Quick Message Defaults -- load a user's default quick messages.
     *
     * @param socket     the socket
     * @param socketData the socket data
     */
    public void qdef(Socket socket, SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][]{
                {"IMGATE", "0"},
                {"QMSG0", "\"Do you want to play a game?\""},
                {"QMSG1", "Yes"},
                {"QMSG2", "No"},
                {"QMSG3", "\"OK, let's start\""},
                {"QMSG4", "\"Have fun!\""},
                {"QMSG5", "\"Good game!\""},
                {"QMSG6", "Thanks!"},
                {"QMSG7", "\"Catch you later\""},
                {"QMSG8", "\"See Ya!\""},
                {"SPM_EA", "0"},
                {"SPM_PART", "0"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    /**
     * slst - Stats List -- load the list of available stats views.
     * No idea how to get career stats to work, but it is required to set at least one view to access online.
     * The view name is displayed in the "Stats > My Carrer" screen.
     *
     * @param socket     the socket
     * @param socketData the socket data
     */
    public void slst(Socket socket, SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][]{
                {"COUNT", "1"},
                {"VIEW0", "Career,\"My Carrer\"", "1"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }


    /**
     * uatr - Update user attributes and hardware flags
     * User attributes are stored as bitfields in a 32 bit unsigned integer and
     * are non-persistent between logins.  These attributes can be used in
     * conjunction with room entry filtering to control user access to certain
     * rooms.  Hardware flags indicate which hardware the user has available
     * (such as a headset or keyboard).  The HWMASK parameter is used to filter
     * out attributes which aren't appropriate for the client's hardware.
     * A user's flags (LOBBYAPI_USER_FL_xxx) may be set with this command as
     * well.  Only the FILT, PRIV, and ATTR[0-3] flags may be modified though.
     *
     * @param socket     the socket
     * @param socketData the socket data
     */
    public void uatr(Socket socket, SocketData socketData) {
        // Should update user attributes with HWFLAG and HWMASK and send back +who and +usr
        socketWriter.write(socket, socketData);
    }

}
