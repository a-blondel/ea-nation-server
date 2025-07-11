package com.ea.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.util.HexFormat;
import java.util.zip.CRC32;

@Slf4j
public final class HexUtils {

    /**
     * Convert a string to a hex string
     * @param text The string
     * @return The hex string
     */
    public static String stringToHex(String text) {
        StringBuilder hexString = new StringBuilder();
        for (char ch : text.toCharArray()) {
            hexString.append(Integer.toHexString(ch));
        }
        return hexString.toString();
    }

    /**
     * Convert a hex string to a string
     * @param hex The hex string
     * @return The string
     */
    public static String hexToString(String hex) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < hex.length(); i+=2) {
            String str = hex.substring(i, i+2);
            output.append((char)Integer.parseInt(str, 16));
        }
        return output.toString();
    }

    /**
     * Format an int into a Word (32 bits - 4 bytes - 8 hex digits)
     * E.g. : value = 2, return = 00000002
     * @param value
     * @return
     */
    public static String formatIntToWord(int value) {
        return String.format("%08X", value);
    }

    /**
     * Swap hex String endianness
     * @param hex
     * @return
     */
    public static String reverseEndianness(String hex) {
        StringBuilder reversedHex = new StringBuilder();
        for (int i = hex.length(); i > 0; i -= 2) {
            reversedHex.append(hex, i - 2, i);
        }
        return reversedHex.toString();
    }

    /**
     * Calculate the CRC32 checksum
     * E.g. : value = 1d0000001c000000fcff1f00e0ff1f00e0ff1f00e0ff1f0400, return = 21421344
     * @param value
     * @return
     */
    public String calcCRC32Checksum(String value) {
        byte[] bytes = parseHexString(value);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        // The int cast is required to only have one Word instead of two with the long type
        // Still unsure if int cast is safe here... Could also use long and split the result at first Word
        return Integer.toHexString(swapEndian((int) crc.getValue()));
    }

    /**
     * Swap int endianness (little endian to big endian, and the opposite)
     * E.g. : 21421344 <-> 44134221
     * @param value
     * @return
     */
    public static int swapEndian(int value) {
        return Integer.reverseBytes(value);
    }

    /**
     * Format byte array to hex string
     * @param bytes
     * @return
     */
    public static String formatHexString(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Parse hex string to byte array
     * @param hexString
     * @return
     */
    public static byte[] parseHexString(String hexString) {
        return HexFormat.of().parseHex(hexString);
    }

    /**
     * Format a byte array as a hex dump
     * @param array The byte array
     * @return The hex dump
     */
    public static String formatHexDump(byte[] array) {
        final int width = 16;

        StringBuilder builder = new StringBuilder();

        for (int rowOffset = 0; rowOffset < array.length; rowOffset += width) {
            builder.append(String.format("%06d:  ", rowOffset));

            for (int index = 0; index < width; index++) {
                if (rowOffset + index < array.length) {
                    builder.append(String.format("%02x ", array[rowOffset + index]));
                } else {
                    builder.append("   ");
                }
            }

            if (rowOffset < array.length) {
                int asciiWidth = Math.min(width, array.length - rowOffset);
                builder.append("  |  ");
                try {
                    //builder.append(new String(array, rowOffset, asciiWidth, "UTF-8").replaceAll("\r\n", " ").replaceAll("\n", " "));
                    builder.append(new String(array, rowOffset, asciiWidth, "UTF-8").replaceAll("[^\\x20-\\x7E]", "."));
                } catch (UnsupportedEncodingException e) {
                    //If UTF-8 isn't available as an encoding then what can we do?!
                    log.error("UTF-8 encoding not available", e);
                }
            }

            builder.append(String.format("%n"));
        }

        return builder.toString();
    }
}
