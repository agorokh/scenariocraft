package io.github.agorokh.scenariocraft.judge;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/** Minimal bounded client for Minecraft's Source-RCON protocol. */
final class RconClient {
    static final int SERVERDATA_RESPONSE_VALUE = 0;
    static final int SERVERDATA_EXECCOMMAND = 2;
    static final int SERVERDATA_AUTH_RESPONSE = 2;
    static final int SERVERDATA_AUTH = 3;
    static final int MAX_PACKET_BYTES = 4 * 1024 * 1024;

    private RconClient() {}

    static void execute(RconConfig config, String command) throws IOException {
        if (command == null || command.isBlank() || command.length() > 1_024
                || command.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("RCON command must be bounded safe text");
        }
        int timeoutMillis = Math.toIntExact(config.timeout().toMillis());
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(config.host(), config.port()), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());

            int authId = positiveRequestId();
            writePacket(output, new Packet(authId, SERVERDATA_AUTH, config.password()));
            Packet authResponse = readAuthResponse(input);
            if (authResponse.requestId() == -1 || authResponse.requestId() != authId
                    || authResponse.type() != SERVERDATA_AUTH_RESPONSE) {
                throw new IOException("RCON authentication failed");
            }

            int commandId = positiveRequestId();
            writePacket(output, new Packet(commandId, SERVERDATA_EXECCOMMAND, command));
            Packet commandResponse = readPacket(input);
            if (commandResponse.requestId() != commandId
                    || commandResponse.type() != SERVERDATA_RESPONSE_VALUE) {
                throw new IOException("RCON returned an invalid command response");
            }
            String normalizedResponse = commandResponse.body().toLowerCase(java.util.Locale.ROOT);
            if (normalizedResponse.contains("unknown command")
                    || normalizedResponse.contains("unknown or incomplete command")) {
                throw new IOException("RCON server rejected the announcement command");
            }
        }
    }

    private static Packet readAuthResponse(DataInputStream input) throws IOException {
        Packet first = readPacket(input);
        if (first.type() == SERVERDATA_AUTH_RESPONSE) {
            return first;
        }
        if (first.type() != SERVERDATA_RESPONSE_VALUE) {
            throw new IOException("RCON returned an invalid authentication response");
        }
        return readPacket(input);
    }

    static void writePacket(DataOutputStream output, Packet packet) throws IOException {
        byte[] body = packet.body().getBytes(StandardCharsets.UTF_8);
        int payloadLength = Math.addExact(body.length, 10);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(payloadLength + 4);
        DataOutputStream packetOutput = new DataOutputStream(bytes);
        writeLittleEndianInt(packetOutput, payloadLength);
        writeLittleEndianInt(packetOutput, packet.requestId());
        writeLittleEndianInt(packetOutput, packet.type());
        packetOutput.write(body);
        packetOutput.writeByte(0);
        packetOutput.writeByte(0);
        output.write(bytes.toByteArray());
        output.flush();
    }

    static Packet readPacket(DataInputStream input) throws IOException {
        int payloadLength = readLittleEndianInt(input);
        if (payloadLength < 10 || payloadLength > MAX_PACKET_BYTES) {
            throw new IOException("RCON packet length is outside the supported range");
        }
        byte[] payload = input.readNBytes(payloadLength);
        if (payload.length != payloadLength) {
            throw new EOFException("RCON packet ended early");
        }
        int requestId = littleEndianInt(payload, 0);
        int type = littleEndianInt(payload, 4);
        if (payload[payload.length - 1] != 0 || payload[payload.length - 2] != 0) {
            throw new IOException("RCON packet is missing terminators");
        }
        String body = new String(payload, 8, payload.length - 10, StandardCharsets.UTF_8);
        return new Packet(requestId, type, body);
    }

    private static int positiveRequestId() {
        return ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
    }

    private static int readLittleEndianInt(DataInputStream input) throws IOException {
        return Integer.reverseBytes(input.readInt());
    }

    private static void writeLittleEndianInt(DataOutputStream output, int value)
            throws IOException {
        output.writeInt(Integer.reverseBytes(value));
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    record Packet(int requestId, int type, String body) {}
}
