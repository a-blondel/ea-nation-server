package eu.eahub.dirtysdk;

import java.util.LinkedHashMap;
import java.util.Map;

public class LobbyTagField {

    // One letter per bit, as used by message attributes (ATTR / F)
    private static final String FLAGS = "@ABCDEFGHIJKLMNOPQRSTUVWXYZ0123";

    static final int[] hexDecode = new int[]{
            128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 128, 128, 128, 128, 128, 128,
            128, 10, 11, 12, 13, 14, 15, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            128, 10, 11, 12, 13, 14, 15, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
            128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128
    };

    /**
     * Decode a string from a hex-encoded string (based on TagFieldGetString)
     *
     * @param encoded hex-encoded string
     * @return decoded string
     */
    public static String decodeString(String encoded) {
        StringBuilder decoded = new StringBuilder();
        for (int i = 0; i < encoded.length(); i += 2) {
            String hexStr = encoded.substring(i, i + 2);
            int hexVal = Integer.parseInt(hexStr, 16);
            if (hexVal == 0x25) { // '%'
                String hex1 = encoded.substring(i + 2, i + 4);
                String hex2 = encoded.substring(i + 4, i + 6);
                int decodedVal = (hexDecode[Integer.parseInt(hex1, 16)] << 4) | hexDecode[Integer.parseInt(hex2, 16)];
                decoded.append(Integer.toHexString(decodedVal));
                i += 4; // skip the next 4 characters
            } else {
                decoded.append(hexStr);
            }
        }
        return decoded.toString();
    }

    /**
     * Decode a flag string, one letter per bit, stopping at the first character that is not a flag
     *
     * @param text the encoded flags
     * @return the flags as a bit set
     */
    public static int decodeFlags(String text) {
        int flags = 0;
        for (char c : text.toCharArray()) {
            int bit = FLAGS.indexOf(c);
            if (bit < 0) {
                break;
            }
            flags |= 1 << bit;
        }
        return flags;
    }

    /**
     * Encode a bit set as a flag string, one letter per bit
     *
     * @param flags the flags as a bit set
     * @return the encoded flags
     */
    public static String encodeFlags(int flags) {
        StringBuilder out = new StringBuilder();
        for (int bit = 0; bit < FLAGS.length(); bit++) {
            if ((flags & (1 << bit)) != 0) {
                out.append(FLAGS.charAt(bit));
            }
        }
        return out.toString();
    }

    /**
     * Decode tag fields (based on TagFieldGetString), keeping their order
     * Fields are separated by whitespace, values can be "quoted" and use %xx escapes
     * A body with tabs separates its fields with tabs (and newlines) only: TW04's match report is sent that way, with
     * unquoted spaces in WHEN
     *
     * @param body the tag fields, may be null
     * @return the decoded fields
     */
    public static Map<String, String> decode(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        boolean tabbed = body != null && body.indexOf('\t') >= 0;
        for (char c : (body == null ? "" : body).toCharArray()) {
            if (c == 0) {
                break;
            }
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            boolean separator = tabbed ? c == '\t' || c == '\n' || c == '\r' : Character.isWhitespace(c);
            if (separator && !quoted) {
                addField(result, token);
            } else {
                token.append(c);
            }
        }
        addField(result, token);
        return result;
    }

    private static void addField(Map<String, String> result, StringBuilder token) {
        String text = token.toString();
        token.setLength(0);
        int equals = text.indexOf('=');
        if (equals > 0) {
            result.put(text.substring(0, equals), unescape(text.substring(equals + 1)));
        }
    }

    private static String unescape(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '%' && i + 2 < text.length()) {
                int a = Character.digit(text.charAt(i + 1), 16);
                int b = Character.digit(text.charAt(i + 2), 16);
                if (a >= 0 && b >= 0) {
                    out.append((char) (a * 16 + b));
                    i += 2;
                    continue;
                }
            }
            out.append(text.charAt(i));
        }
        return out.toString();
    }

    /**
     * Get a numeric field (based on TagFieldGetNumber)
     *
     * @param fields   the decoded fields
     * @param key      the field name
     * @param fallback the value when the field is absent or not a number
     * @return the field's value
     */
    public static int getNumber(Map<String, String> fields, String key, int fallback) {
        try {
            return Integer.parseInt(fields.getOrDefault(key, String.valueOf(fallback)).strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Encode tag fields (based on TagFieldSetString), one per line, in the map's order
     * Values with a space are quoted, and control, non-ASCII and =":% characters use %xx escapes
     *
     * @param fields the fields to encode
     * @return the tag fields
     */
    public static String encode(Map<String, String> fields) {
        StringBuilder out = new StringBuilder();
        fields.forEach((key, value) -> {
            out.append(key).append('=');
            boolean quoted = value.indexOf(' ') >= 0;
            if (quoted) {
                out.append('"');
            }
            for (char c : value.toCharArray()) {
                if (c < 32 || c > 126 || "=\"%:".indexOf(c) >= 0) {
                    out.append('%').append(String.format("%02x", c & 255));
                } else {
                    out.append(c);
                }
            }
            if (quoted) {
                out.append('"');
            }
            out.append('\n');
        });
        return out.toString();
    }

}
