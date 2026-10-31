package eu.eahub.dirtysdk;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LobbyTagFieldTest {

    @Test
    void decodeString() {
        String encoded = "3c7b6a25323529334f6e4337555775294e2d6d5c5a7a4d686377784131772b59";
        String decoded = LobbyTagField.decodeString(encoded);
        assertEquals("3c7b6a2529334f6e4337555775294e2d6d5c5a7a4d686377784131772b59", decoded);
    }

    @Test
    void flagsUseOneLetterPerBit() {
        assertEquals(0x40010000, LobbyTagField.decodeFlags("P3"));
        assertEquals("P3", LobbyTagField.encodeFlags(0x40010000));
        assertEquals(0, LobbyTagField.decodeFlags("65536"));
        assertEquals("", LobbyTagField.encodeFlags(0));
    }

    @Test
    void decodeSpaceSeparatedAndQuotedEscapedFields() {
        assertEquals(Map.of("ROOMS", "1", "USERS", "1"), LobbyTagField.decode("ROOMS=1 USERS=1\0"));
        assertEquals(Map.of("NAME", "Match.C.Our Room", "TEXT", "a=b:c%d\"e\nnext"),
                LobbyTagField.decode("NAME=\"Match.C.Our Room\"\nTEXT=a%3db%3ac%25d%22e%0anext\n\0"));
    }

    @Test
    void decodeTabSeparatedFieldsWithSpacesInValues() {
        assertEquals(Map.of("WHEN", "2026.10.24 13:48:49", "REPT", "tw04test", "QUIT1", "1"),
                LobbyTagField.decode("WHEN=2026.10.24 13:48:49\tREPT=tw04test\tQUIT1=1\0"));
    }

    @Test
    void encodeEscapesStringsAndKeepsBinaryFields() {
        assertEquals("TEXT=\"hello%3a %22golf%22%0a\"\n", LobbyTagField.encode(Map.of("TEXT", "hello: \"golf\"\n")));
        assertEquals("RNKRS=$00ff\n", LobbyTagField.encode(Map.of("RNKRS", "$00ff")));
    }
}
