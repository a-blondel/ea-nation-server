package eu.eahub.dirtysdk;

import eu.eahub.dirtysdk.TwStatsCodec.Field;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TwStatsCodecTest {

    @Test
    void pack() throws Exception {
        // Generated independently using TW04OnlineServer/twstats.py pack()
        String[] expected;
        try (InputStream input = getClass().getResourceAsStream("/tw04-stats-reference.hex")) {
            expected = new String(input.readAllBytes(), StandardCharsets.US_ASCII).strip().split("\\R");
        }
        long[] win = TwStatsCodec.defaults();
        long[] loss = TwStatsCodec.defaults();
        win[Field.POINTS.index] = 2;
        win[Field.MATCH_POINTS.index] = 2;
        win[Field.MATCH_WINS.index] = 1;
        win[Field.MATCH_STREAK.index] = 1;
        win[Field.MATCH_COMPLETE.index] = 1;
        loss[Field.MATCH_LOSSES.index] = 1;
        loss[Field.MATCH_STREAK.index] = -1;
        loss[Field.MATCH_INCOMPLETE.index] = 1;
        long[][] fixtures = {win, loss};
        for (int i = 0; i < 2; i++) {
            String packed = TwStatsCodec.pack(fixtures[i]);
            assertEquals(expected[i], HexFormat.of().formatHex(packed.getBytes(StandardCharsets.ISO_8859_1)));
            assertEquals(packed, LobbyTagField.decode(LobbyTagField.encode(Map.of("S", packed))).get("S"));
        }
    }
}
