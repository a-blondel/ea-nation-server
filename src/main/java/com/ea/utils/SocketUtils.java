package com.ea.utils;

import com.ea.dto.SocketWrapper;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

@Slf4j
public class SocketUtils {

    public static final String DATETIME_FORMAT = "yyyy.M.d-H:mm:ss";
    public static final String SPACE_CHAR = " ";
    public static final String TAB_CHAR = "\u0009";
    public static final String RETURN_CHAR = "\\R";
    public static final String NEWLINE_CHAR = "\n";

    /**
     * Calculate length of the content to parse
     *
     * @param buffer  the request buffer (only efficient way to get the length)
     * @param lastPos the position to begin in the buffer (there can be multiple messages in a buffer)
     * @return int - the size of the content
     */
    public static int getlength(byte[] buffer, int lastPos) {
        String size = "";
        for (int i = lastPos + 8; i < lastPos + 12; i++) {
            size += String.format("%02x", buffer[i]);
        }
        return Integer.parseInt(size, 16);
    }

    /**
     * Get the value from a key in a socket data
     * E.g. : data = "key1=value1\nkey2=value
     * getValueFromSocket(data, "key1") returns "value1"
     * getValueFromSocket(data, "key2") returns "value2"
     *
     * @param data
     * @param key
     * @return
     */
    public static String getValueFromSocket(String data, String key) {
        return getValueFromSocket(data, key, RETURN_CHAR);
    }

    public static String getValueFromSocket(String data, String key, String splitter) {
        String result = null;
        String[] entries = data.split(splitter);
        for (String entry : entries) {
            String[] parts = entry.trim().split("=");
            if (key.equals(parts[0])) {
                if (parts.length > 1) {
                    result = parts[1];
                }
                break;
            }
        }
        return result;
    }

    /**
     * Get a map from a socket data
     * E.g. : data = "key1=value1\nkey2=value2"
     * getMapFromSocket(data) returns {key1=value1, key2=value2}
     *
     * @param data the socket data
     * @return a map of the data
     */
    public static Map<String, String> getMapFromSocket(String data) {
        return Map.ofEntries(data.lines()
                .map(line -> line.split("="))
                .filter(parts -> parts.length > 1)
                .map(parts -> Map.entry(parts[0], parts[1]))
                .toArray(Map.Entry[]::new));
    }

    /**
     * Handle localhost IP
     *
     * @param socketIp
     * @return machine IP instead of 127.0.0.1, or socketIp if != 127.0.0.1
     */
    public static String handleLocalhostIp(String socketIp) {
        if (socketIp.contains("127.0.0.1")) {
            try {
                return socketIp.replace("127.0.0.1", InetAddress.getLocalHost().getHostAddress());
            } catch (UnknownHostException e) {
                log.error(e.getMessage());
            }
        }
        return socketIp;
    }

    /**
     * Get player info from socket wrapper
     *
     * @param socketWrapper
     * @return
     */
    public static String getPlayerInfo(SocketWrapper socketWrapper) {
        String playerInfo = "";
        if (socketWrapper != null && socketWrapper.getPersonaEntity() != null) {
            String pers = socketWrapper.getPersonaEntity().getPers();
            String role = socketWrapper.getIsDedicatedHost().get() ? "host" : "client";
            playerInfo = pers + " (" + role + ")";
        }
        return playerInfo;
    }

}
