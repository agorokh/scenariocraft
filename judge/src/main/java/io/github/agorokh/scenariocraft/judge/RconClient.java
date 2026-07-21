package io.github.agorokh.scenariocraft.judge;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class RconClient {
    private static final int AUTH_TYPE = 3;
    private static final int COMMAND_TYPE = 2;
    private static final int MAX_PACKET_BYTES = 1024 * 1024;
    private static final int REQUEST_ID = 0x5343;
    private final HostResolver resolver;

    RconClient() {
        this(InetAddress::getAllByName);
    }

    RconClient(HostResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    static void execute(RconConfig config, String command) throws IOException {
        Objects.requireNonNull(config, "config");
        new RconClient()
                .execute(
                        new RconSettings(
                                config.host(),
                                config.port(),
                                config.password(),
                                config.timeout(),
                                config.timeout()),
                        command);
    }

    void execute(RconSettings settings, String command) throws IOException {
        if (command == null || command.isBlank() || command.length() > 512
                || command.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("RCON command must be a non-blank safe command");
        }
        long connectTimeoutNanos = settings.connectTimeout().toNanos();
        long connectDeadline = System.nanoTime() + connectTimeoutNanos;
        InetAddress address = resolve(settings.host(), settings.connectTimeout().toMillis());
        long remainingNanos = connectDeadline - System.nanoTime();
        if (remainingNanos <= 0L) {
            throw new SocketTimeoutException("RCON hostname resolution exhausted the connect timeout");
        }
        int remainingMillis =
                Math.toIntExact(
                        Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(address, settings.port()), remainingMillis);
            socket.setSoTimeout(Math.toIntExact(settings.readTimeout().toMillis()));
            writePacket(socket.getOutputStream(), REQUEST_ID, AUTH_TYPE, settings.password());
            Packet auth = readPacket(socket.getInputStream());
            if (auth.id() == -1) {
                throw new IOException("RCON authentication failed");
            }
            if (auth.id() != REQUEST_ID || auth.type() != COMMAND_TYPE) {
                auth = readPacket(socket.getInputStream());
            }
            if (auth.id() == -1) {
                throw new IOException("RCON authentication failed");
            }
            if (auth.id() != REQUEST_ID || auth.type() != COMMAND_TYPE) {
                throw new IOException("RCON authentication returned an unexpected request id");
            }
            writePacket(socket.getOutputStream(), REQUEST_ID + 1, COMMAND_TYPE, command);
            Packet response = readPacket(socket.getInputStream());
            if (response.id() != REQUEST_ID + 1) {
                throw new IOException("RCON command returned an unexpected request id");
            }
        }
    }

    private InetAddress resolve(String host, long timeoutMillis) throws IOException {
        FutureTask<InetAddress[]> resolution =
                new FutureTask<>(() -> resolver.resolve(host));
        Thread.ofPlatform()
                .daemon(true)
                .name("scenariocraft-rcon-dns")
                .start(resolution);
        try {
            InetAddress[] addresses = resolution.get(timeoutMillis, TimeUnit.MILLISECONDS);
            if (addresses.length == 0) {
                throw new IOException("RCON hostname resolved to no addresses");
            }
            return addresses[0];
        } catch (TimeoutException exception) {
            resolution.cancel(true);
            throw new SocketTimeoutException("RCON hostname resolution timed out");
        } catch (InterruptedException exception) {
            resolution.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("RCON hostname resolution was interrupted", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw new IOException("RCON hostname resolution failed", exception.getCause());
        }
    }

    private static void writePacket(OutputStream output, int id, int type, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        int length = Math.addExact(10, payload.length);
        writeLittleEndianInt(output, length);
        writeLittleEndianInt(output, id);
        writeLittleEndianInt(output, type);
        output.write(payload);
        output.write(0);
        output.write(0);
        output.flush();
    }

    private static Packet readPacket(InputStream input) throws IOException {
        int length = readLittleEndianInt(input);
        if (length < 10 || length > MAX_PACKET_BYTES) {
            throw new IOException("RCON returned an invalid packet length");
        }
        byte[] packet = input.readNBytes(length);
        if (packet.length != length) {
            throw new EOFException("RCON response ended early");
        }
        if (packet[length - 1] != 0 || packet[length - 2] != 0) {
            throw new IOException("RCON response is missing terminators");
        }
        int id = littleEndianInt(packet, 0);
        int type = littleEndianInt(packet, 4);
        ByteArrayOutputStream body = new ByteArrayOutputStream(length - 10);
        body.write(packet, 8, length - 10);
        return new Packet(id, type, body.toString(StandardCharsets.UTF_8));
    }

    private static int readLittleEndianInt(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(4);
        if (bytes.length != 4) {
            throw new EOFException("RCON response ended before its length");
        }
        return littleEndianInt(bytes, 0);
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static void writeLittleEndianInt(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private record Packet(int id, int type, String body) {}

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws IOException;
    }
}
