package io.github.agorokh.scenariocraft.buildbattle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Atomic plugin-owned registry for players whose hub recovery is still pending. */
public final class TeleportRecoveryStore {
    private final Path path;
    private final Set<UUID> pending;

    private TeleportRecoveryStore(Path path, Set<UUID> pending) {
        this.path = path;
        this.pending = pending;
    }

    public static TeleportRecoveryStore open(Path path) {
        Path absolutePath = path.toAbsolutePath().normalize();
        try {
            Path parent = absolutePath.getParent();
            if (parent == null) {
                throw new IOException("recovery registry has no parent directory");
            }
            Files.createDirectories(parent);
            Set<UUID> entries = new LinkedHashSet<>();
            if (Files.exists(absolutePath)) {
                for (String line : Files.readAllLines(absolutePath, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        entries.add(UUID.fromString(line));
                    }
                }
            }
            return new TeleportRecoveryStore(absolutePath, entries);
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException(
                    "Could not load teleport recovery registry " + absolutePath, failure);
        }
    }

    static TeleportRecoveryStore inMemory() {
        return new TeleportRecoveryStore(null, new LinkedHashSet<>());
    }

    public synchronized Set<UUID> pendingPlayers() {
        return Set.copyOf(pending);
    }

    public synchronized boolean contains(UUID playerId) {
        return pending.contains(playerId);
    }

    public synchronized void add(UUID playerId) {
        replaceWith(playerId, true);
    }

    public synchronized void remove(UUID playerId) {
        replaceWith(playerId, false);
    }

    private void replaceWith(UUID playerId, boolean present) {
        Set<UUID> next = new LinkedHashSet<>(pending);
        boolean changed = present ? next.add(playerId) : next.remove(playerId);
        if (!changed) {
            return;
        }
        if (path != null) {
            writeAtomically(next);
        }
        pending.clear();
        pending.addAll(next);
    }

    private void writeAtomically(Set<UUID> entries) {
        Path parent = path.getParent();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            List<String> lines = entries.stream().map(UUID::toString).sorted().toList();
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Could not persist teleport recovery registry " + path, failure);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The authoritative registry was either replaced or the write failed visibly.
                }
            }
        }
    }
}
