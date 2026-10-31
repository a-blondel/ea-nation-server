package eu.eahub.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Numeric IPv4 addresses, as TW04 exchanges them (the game never resolves names)
 */
public class TwAddressUtils {

    /**
     * Parse a dotted numeric IPv4 address, without any name resolution
     *
     * @param text the address
     * @return the address, or null if the text is not a numeric IPv4 address
     */
    public static Inet4Address parseIpv4(String text) {
        if (text == null || !text.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
            return null;
        }
        byte[] bytes = new byte[4];
        String[] pieces = text.split("\\.");
        for (int i = 0; i < 4; i++) {
            int value = Integer.parseInt(pieces[i]);
            if (value > 255) {
                return null;
            }
            bytes[i] = (byte) value;
        }
        try {
            return (Inet4Address) InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * Whether an address is a numeric IPv4 address another player could connect to (not unspecified nor loopback)
     *
     * @param text the address
     * @return true if it is
     */
    public static boolean isPeerIpv4(String text) {
        Inet4Address address = parseIpv4(text);
        return address != null && !address.isAnyLocalAddress() && !address.isLoopbackAddress();
    }
}
