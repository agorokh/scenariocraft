package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeleportRecoveryStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void openingEmptyStoreProbesAndMaterializesAtomicRegistry() throws IOException {
        Path registry = temporaryDirectory.resolve("pending-recovery.txt");

        TeleportRecoveryStore store = TeleportRecoveryStore.open(registry);

        assertTrue(Files.isRegularFile(registry));
        assertEquals(List.of(), Files.readAllLines(registry));
        assertEquals(Set.of(), store.pendingPlayers());
    }

    @Test
    void additionsAndRemovalsSurviveStoreReopen() {
        Path registry = temporaryDirectory.resolve("pending-recovery.txt");
        UUID first = UUID.fromString("cced86d0-d0d8-4b3e-8f8f-123455a2a181");
        UUID second = UUID.fromString("81ccbfd0-55b4-45d2-b750-15b70c394f7d");
        TeleportRecoveryStore store = TeleportRecoveryStore.open(registry);

        store.add(first);
        store.add(second);
        assertEquals(Set.of(first, second), TeleportRecoveryStore.open(registry).pendingPlayers());

        store.remove(first);
        assertEquals(Set.of(second), TeleportRecoveryStore.open(registry).pendingPlayers());
        assertFalse(
                Files.exists(temporaryDirectory.resolve("pending-recovery.txt.tmp")),
                "atomic replacement must not leave a fixed-name partial file");
    }

    @Test
    void failedAtomicReplacementDoesNotMutateInMemoryRegistry() throws IOException {
        Path registry = temporaryDirectory.resolve("pending-recovery.txt");
        UUID retained = UUID.fromString("b5788b31-1ec5-4749-b112-03c7da61d89e");
        UUID rejected = UUID.fromString("05d3b9de-052f-40f3-a273-61dd494efb25");
        TeleportRecoveryStore store = TeleportRecoveryStore.open(registry);
        store.add(retained);
        Files.delete(registry);
        Files.createDirectory(registry);

        assertThrows(IllegalStateException.class, () -> store.add(rejected));

        assertTrue(store.contains(retained));
        assertFalse(store.contains(rejected));
    }

    @Test
    void malformedRegistryFailsClosed() throws IOException {
        Path registry = temporaryDirectory.resolve("pending-recovery.txt");
        Files.writeString(registry, "not-a-player-id\n");

        assertThrows(
                IllegalStateException.class,
                () -> TeleportRecoveryStore.open(registry));
    }
}
