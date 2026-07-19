package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchedWorkQueueTest {
    @Test
    void everyTickHonorsTheConfiguredBudget() {
        BatchedWorkQueue queue = new BatchedWorkQueue(4);
        CountingWork work = new CountingWork("plot", 10, new ArrayList<>());
        queue.add(work);

        assertEquals(4, queue.runTick());
        assertEquals(6, queue.pendingSteps());
        assertEquals(4, queue.runTick());
        assertEquals(2, queue.pendingSteps());
        assertEquals(2, queue.runTick());
        assertEquals(0, queue.pendingSteps());
        assertFalse(queue.hasWork());
        assertEquals(0, queue.runTick());
    }

    @Test
    void operationsStayInQueueOrderAcrossTickBoundaries() {
        List<String> order = new ArrayList<>();
        BatchedWorkQueue queue = new BatchedWorkQueue(3);
        queue.add(new CountingWork("clear", 2, order));
        queue.add(new CountingWork("wall", 3, order));

        assertEquals(3, queue.runTick());
        assertEquals(List.of("clear", "clear", "wall"), order);
        assertTrue(queue.hasWork());
        assertEquals(2, queue.runTick());
        assertEquals(List.of("clear", "clear", "wall", "wall", "wall"), order);
    }

    @Test
    void batchingMathRoundsOnlyPartialTicksUp() {
        assertEquals(0, BatchedWorkQueue.ticksRequired(0, 4_000));
        assertEquals(1, BatchedWorkQueue.ticksRequired(4_000, 4_000));
        assertEquals(2, BatchedWorkQueue.ticksRequired(4_001, 4_000));
        assertEquals(21, BatchedWorkQueue.ticksRequired(81_660, 4_000));
    }

    private static final class CountingWork implements IncrementalWork {
        private final String label;
        private final List<String> order;
        private long remaining;

        private CountingWork(String label, long remaining, List<String> order) {
            this.label = label;
            this.remaining = remaining;
            this.order = order;
        }

        @Override
        public boolean hasNext() {
            return remaining > 0;
        }

        @Override
        public void runNext() {
            order.add(label);
            remaining--;
        }

        @Override
        public long remaining() {
            return remaining;
        }
    }
}
