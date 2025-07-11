package com.ea.utils;

import org.bouncycastle.util.encoders.Hex;

public class ByteUtils {

    public static final byte[] MD5_CIPHER_SIGNATURE = Hex.decode("2a864886f70d010104");

    /**
     * Find the byte pattern in the buffer
     * Returns index of provided byte pattern in a buffer,
     * Returns -1 if not found
     * @param array
     * @param pattern
     * @param startOffset
     * @return
     */
    public static int findBytePattern(byte[] array, byte[] pattern, int startOffset) {
        boolean found;
        for (int i = startOffset; i < array.length - pattern.length + 1; ++i) {
            found = true;
            for (int j = 0; j < pattern.length; ++j) {
                if (array[i + j] != pattern[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }

}
