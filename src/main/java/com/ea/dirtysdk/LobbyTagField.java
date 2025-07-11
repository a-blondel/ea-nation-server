package com.ea.dirtysdk;

public class LobbyTagField {

    static final int[] hexDecode = new int[] {
            128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,
            128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,
            128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,
            0,  1,  2,  3,  4,  5,  6,  7,  8,  9,128,128,128,128,128,128,
            128, 10, 11, 12, 13, 14, 15,128,128,128,128,128,128,128,128,128,
            128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,
            128, 10, 11, 12, 13, 14, 15,128,128,128,128,128,128,128,128,128,
            128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,
            128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,
            128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,
            128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,
            128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,
            128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,
            128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,
            128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,
            128,128,128,128,128,128,128,128,128,128,128,128,128,128,128,128
    };

    /**
     * Decode a string from a hex-encoded string (based on TagFieldGetString)
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

}
