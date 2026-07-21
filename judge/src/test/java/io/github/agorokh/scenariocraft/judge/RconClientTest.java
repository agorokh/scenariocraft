package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RconClientTest {
    @Test
    void writesEachRconPacketInOneOutputWrite() throws Exception {
        RecordingOutputStream output = new RecordingOutputStream();

        RconClient.writePacket(output, 7, 2, "battle announce round-20260721-193000");

        assertEquals(1, output.writes);
        assertTrue(output.bytes.size() > 14);
    }

    @Test
    void authenticatesAndSendsTheNarrowAnnouncementCommand() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                var executor = Executors.newSingleThreadExecutor()) {
            var exchange =
                    executor.submit(
                            () -> {
                                try (var socket = server.accept()) {
                                    Packet auth = readPacket(socket.getInputStream());
                                    assertEquals(3, auth.type());
                                    assertEquals("example-value", auth.body());
                                    writePacket(socket.getOutputStream(), auth.id(), 2, "");
                                    Packet command = readPacket(socket.getInputStream());
                                    assertEquals(2, command.type());
                                    assertEquals("battle announce round-20260721-193000", command.body());
                                    writePacket(socket.getOutputStream(), command.id(), 0, "ScenarioCraft announced");
                                }
                                return null;
                            });
            RconSettings settings =
                    new RconSettings(
                            InetAddress.getLoopbackAddress().getHostAddress(),
                            server.getLocalPort(),
                            "example-value",
                            Duration.ofSeconds(2),
                            Duration.ofSeconds(2));

            new RconClient().execute(settings, "battle announce round-20260721-193000");

            exchange.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void hostnameResolutionCannotOutliveTheConnectTimeout() {
        RconClient client =
                new RconClient(
                        ignored -> {
                            try {
                                Thread.sleep(10_000L);
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new IOException("test resolver interrupted", exception);
                            }
                            return new InetAddress[] {InetAddress.getLoopbackAddress()};
                        });
        RconSettings settings =
                new RconSettings(
                        "stalled.example",
                        25_575,
                        "example-value",
                        Duration.ofMillis(50),
                        Duration.ofSeconds(1));
        long started = System.nanoTime();

        assertThrows(
                java.net.SocketTimeoutException.class,
                () -> client.execute(settings, "battle announce round-20260721-193000"));

        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(1)) < 0);
    }

    private static Packet readPacket(InputStream input) throws Exception {
        int length = readInt(input);
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException();
        }
        int id = intAt(bytes, 0);
        int type = intAt(bytes, 4);
        String body = new String(bytes, 8, length - 10, java.nio.charset.StandardCharsets.UTF_8);
        return new Packet(id, type, body);
    }

    private static void writePacket(OutputStream output, int id, int type, String body) throws Exception {
        byte[] payload = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeInt(output, payload.length + 10);
        writeInt(output, id);
        writeInt(output, type);
        output.write(payload);
        output.write(0);
        output.write(0);
        output.flush();
    }

    private static int readInt(InputStream input) throws Exception {
        byte[] bytes = input.readNBytes(4);
        if (bytes.length != 4) {
            throw new EOFException();
        }
        return intAt(bytes, 0);
    }

    private static int intAt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static void writeInt(OutputStream output, int value) throws Exception {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private record Packet(int id, int type, String body) {}

    private static final class RecordingOutputStream extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int writes;

        @Override
        public void write(byte[] value, int offset, int length) {
            writes++;
            bytes.write(value, offset, length);
        }

        @Override
        public void write(int value) {
            writes++;
            bytes.write(value);
        }
    }
}
