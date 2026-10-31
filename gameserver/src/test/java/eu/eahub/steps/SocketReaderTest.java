package eu.eahub.steps;

import eu.eahub.dto.SocketData;
import eu.eahub.services.server.SocketManager;
import eu.eahub.utils.Props;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocketReaderTest {

    /**
     * Returns the same bytes using the given read boundaries, like a TCP stream would
     */
    private static class FragmentedStream extends InputStream {

        private final byte[] data;
        private final int[] ends;
        private int position;
        private int chunk;

        FragmentedStream(byte[] data, int... ends) {
            this.data = data;
            this.ends = ends;
        }

        @Override
        public int read() {
            return position == data.length ? -1 : data[position++] & 255;
        }

        @Override
        public int read(byte[] dest, int offset, int length) {
            if (length == 0) {
                return 0;
            }
            if (position == data.length) {
                return -1;
            }
            while (chunk < ends.length && position >= ends[chunk]) {
                chunk++;
            }
            int end = chunk < ends.length ? ends[chunk] : data.length;
            int count = Math.min(length, end - position);
            System.arraycopy(data, position, dest, offset, count);
            position += count;
            return count;
        }
    }

    private List<SocketData> read(byte[] bytes, int... boundaries) throws Exception {
        List<SocketData> messages = new ArrayList<>();
        SocketProcessor processor = mock(SocketProcessor.class);
        doAnswer(call -> messages.add(call.getArgument(1))).when(processor).process(any(), any());
        Props props = mock(Props.class);
        when(props.getTcpDebugExclusions()).thenReturn(List.of());
        SocketParser parser = new SocketParser(props, mock(SocketManager.class), processor);
        Socket socket = mock(Socket.class);
        when(socket.getInputStream()).thenReturn(new FragmentedStream(bytes, boundaries));
        when(socket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("192.0.2.10", 12345));
        new SocketReader(parser).read(socket);
        return messages;
    }

    private byte[] message(String id, String body) {
        byte[] payload = body.getBytes(StandardCharsets.ISO_8859_1);
        return ByteBuffer.allocate(12 + payload.length)
                .put(id.getBytes(StandardCharsets.US_ASCII))
                .putInt(0)
                .putInt(12 + payload.length)
                .put(payload)
                .array();
    }

    @Test
    void messageSplitAnywhereIsReadOnce() throws Exception {
        String body = "PERS=Player\nTEXT=" + "x".repeat(200) + "\n\0";
        byte[] bytes = message("mesg", body);
        for (int split = 1; split < bytes.length; split++) {
            List<SocketData> messages = read(bytes, split);
            assertEquals(1, messages.size(), "split=" + split);
            assertEquals("mesg", messages.getFirst().getIdMessage());
            assertEquals(body, messages.getFirst().getInputMessage(), "split=" + split);
        }
    }

    @Test
    void mergedAndByteByByteMessagesAreReadInOrder() throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(message("auth", "NAME=a\nPASS=b\n\0"));
        data.write(message("move", "NAME=Room\n\0"));
        data.write(message("~png", ""));
        byte[] bytes = data.toByteArray();
        int[] everyByte = IntStream.range(1, bytes.length).toArray();
        for (int[] splits : List.of(new int[0], everyByte, new int[]{5, 20, bytes.length - 3})) {
            List<SocketData> messages = read(bytes, splits);
            assertEquals(List.of("auth", "move", "~png"), messages.stream().map(SocketData::getIdMessage).toList());
            assertEquals("NAME=Room\n\0", messages.get(1).getInputMessage());
        }
    }

    @Test
    void messagesLargerThanOneReadArePreserved() throws Exception {
        String body = "DATA=" + "x".repeat(9000) + "\0";
        List<SocketData> messages = read(message("cusr", body), 8, 4096, 8192);
        assertEquals(body, messages.getFirst().getInputMessage());
    }

    @Test
    void truncatedMessagesAreNotProcessed() throws Exception {
        byte[] complete = message("auth", "NAME=a\n\0");
        for (int length : new int[]{1, 11, 12, complete.length - 1}) {
            assertTrue(read(Arrays.copyOf(complete, length)).isEmpty(), "length=" + length);
        }
    }

    @Test
    void invalidLengthsStopReading() throws Exception {
        for (int length : new int[]{0, 11, -1, 0x80000000, SocketReader.MAX_MESSAGE_SIZE + 1}) {
            byte[] header = ByteBuffer.allocate(12).putInt(0).putInt(0).putInt(length).array();
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            data.write(header);
            data.write(message("auth", "NAME=a\n\0"));
            assertTrue(read(data.toByteArray(), 5).isEmpty(), "length=" + length);
        }
    }
}
