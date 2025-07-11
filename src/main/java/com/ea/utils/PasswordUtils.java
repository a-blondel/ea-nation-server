package com.ea.utils;

import com.ea.dirtysdk.CryptSSC2;
import com.ea.dirtysdk.LobbyTagField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class PasswordUtils {

    @Autowired
    private Props props;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Encode a password using BCrypt
     * @param password The password to encode
     * @return The encoded password
     */
    public String bCryptEncode(String password) {
        return passwordEncoder.encode(password);
    }

    /**
     * Check if a password matches a BCrypt encoded password
     * @param password The password to check
     * @param dbPassword The BCrypt encoded password
     * @return True if the password matches, false otherwise
     */
    public boolean bCryptMatches(String password, String dbPassword) {
        return passwordEncoder.matches(password, dbPassword);
    }

    /**
     * Decode a SSC2 encoded password
     * @param encodedPassword The encoded password
     * @return The decoded password
     */
    public String ssc2Decode(String encodedPassword) {
        encodedPassword = sanitizeInput(encodedPassword);
        String ssc2Key = props.getSsc2Key();
        byte[] decodeHexKey = HexFormat.of().parseHex(ssc2Key);
        byte[] decodeBuffer = new byte[32];
        CryptSSC2.cryptSSC2StringDecrypt(decodeBuffer, decodeBuffer.length, encodedPassword.getBytes(), decodeHexKey, decodeHexKey.length, decodeHexKey.length);
        return truncateAtNull(new String(decodeBuffer));
    }

    /**
     * Truncate a string at the first null terminator (0x00 - '\0')
     * @param input The input string
     * @return The truncated string
     */
    public String truncateAtNull(String input) {
        int nullPos = input.indexOf('\0');
        return (nullPos != -1) ? input.substring(0, nullPos) : input;
    }

    /**
     * Sanitize input by removing enclosing quotes and leading tilde
     * @param input The input string
     * @return The sanitized string
     */
    public String sanitizeInput(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Remove enclosing quotes
        if (input.charAt(0) == 0x22 && input.charAt(input.length() - 1) == 0x22) {
            input = input.substring(1, input.length() - 1);
        }

        // Remove leading tilde
        if (input.charAt(0) == 0x7E) {
            input = input.substring(1);
        }

        input = HexUtils.stringToHex(input);
        input = LobbyTagField.decodeString(input);
        input = HexUtils.hexToString(input);

        return input;
    }

    /**
     * Generate a random password
     * @return The random password
     */
    public String generateRandomPassword() {
        int length = 8;
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        return sb.toString();
    }


}
