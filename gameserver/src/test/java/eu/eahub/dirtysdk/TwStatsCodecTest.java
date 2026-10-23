package eu.eahub.dirtysdk;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TwStatsCodecTest {

    @Test
    void matchesIndependentPythonReferenceForWinnerAndNegativeStreak() throws Exception {
        // Generated independently using TW04OnlineServer/twstats.py pack()
        String[] expected;
        try (InputStream input = getClass().getResourceAsStream("/tw04-stats-reference.hex")) {
            expected = new String(input.readAllBytes(), StandardCharsets.US_ASCII).strip().split("\\R");
        }
        long[] win = TwStatsCodec.defaults();
        long[] loss = TwStatsCodec.defaults();
        win[1] = 2;
        win[4] = 2;
        win[7] = 1;
        win[14] = 1;
        win[20] = 1;
        loss[8] = 1;
        loss[14] = -1;
        loss[16] = 1;
        long[][] fixtures = {win, loss};
        for (int i = 0; i < 2; i++) {
            String packed = TwStatsCodec.pack(fixtures[i]);
            assertEquals(expected[i], HexFormat.of().formatHex(packed.getBytes(StandardCharsets.ISO_8859_1)));
            assertEquals(packed, LobbyTagField.decode(LobbyTagField.encode(Map.of("S", packed))).get("S"));
        }
    }
}
