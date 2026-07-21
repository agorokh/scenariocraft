package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RconClientTest {
    @Test
    void serializesEachPacketIntoOneSocketWriteForThePaperReader() throws Exception {
        CountingOutputStream bytes = new CountingOutputStream();

        RconClient.writePacket(
                new DataOutputStream(bytes),
                new RconClient.Packet(7, RconClient.SERVERDATA_AUTH, "password"));

        assertEquals(1, bytes.bulkWrites);
        assertEquals("password", RconClient.readPacket(
                new DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())))
                .body());
    }

    @Test
    void authenticatesAndSendsTheBoundedAnnouncementCommand() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                var executor = Executors.newSingleThreadExecutor()) {
            var command = executor.submit(() -> {
                try (var socket = server.accept()) {
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    RconClient.Packet auth = RconClient.readPacket(input);
                    assertEquals(RconClient.SERVERDATA_AUTH, auth.type());
                    assertEquals("test-password", auth.body());
                    RconClient.writePacket(
                            output,
                            new RconClient.Packet(
                                    auth.requestId(),
                                    RconClient.SERVERDATA_AUTH_RESPONSE,
                                    ""));
                    RconClient.Packet request = RconClient.readPacket(input);
                    RconClient.writePacket(
                            output,
                            new RconClient.Packet(
                                    request.requestId(),
                                    RconClient.SERVERDATA_RESPONSE_VALUE,
                                    "ScenarioCraft announced the latest judge results."));
                    return request.body();
                }
            });

            RconClient.execute(
                    new RconConfig(
                            InetAddress.getLoopbackAddress().getHostAddress(),
                            server.getLocalPort(),
                            "test-password",
                            Duration.ofSeconds(3)),
                    "battle announce-results");

            assertEquals("battle announce-results", command.get(3, TimeUnit.SECONDS));
        }
    }

    private static final class CountingOutputStream extends ByteArrayOutputStream {
        private int bulkWrites;

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            bulkWrites++;
            super.write(bytes, offset, length);
        }
    }
}
