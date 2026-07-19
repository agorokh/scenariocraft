package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.WorldType;
import org.junit.jupiter.api.Test;

final class ArenaWorldServiceTest {
    @Test
    void acceptsFlatArenaWorld() {
        assertDoesNotThrow(() -> ArenaWorldService.requireFlat(WorldType.FLAT));
    }

    @Test
    void rejectsExistingNonFlatArenaWorld() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> ArenaWorldService.requireFlat(WorldType.NORMAL));

        assertTrue(exception.getMessage().contains("not superflat"));
    }
}
