package com.ea.dirtysdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LobbyTagFieldTest {

    @Test
    void decodeString() {
        String encoded = "3c7b6a25323529334f6e4337555775294e2d6d5c5a7a4d686377784131772b59";
        String decoded = LobbyTagField.decodeString(encoded);
        assertEquals("3c7b6a2529334f6e4337555775294e2d6d5c5a7a4d686377784131772b59", decoded);
    }
}