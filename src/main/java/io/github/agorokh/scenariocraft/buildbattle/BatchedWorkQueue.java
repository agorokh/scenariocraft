package io.github.agorokh.scenariocraft.buildbattle;

import java.util.ArrayDeque;
import java.util.Deque;

/** Runs no more than a configured number of incremental operations per tick. */
public final class BatchedWorkQueue {
    private final int workPerTick;
    private final Deque<IncrementalWork> work = new ArrayDeque<>();
    private long pendingSteps;

    public BatchedWorkQueue(int workPerTick) {
        if (workPerTick <= 0) {
            throw new IllegalArgumentException("workPerTick must be positive");
        }
        this.workPerTick = workPerTick;
    }

    public void add(IncrementalWork next) {
        if (!next.hasNext()) {
            return;
        }
        pendingSteps = Math.addExact(pendingSteps, next.remaining());
        work.addLast(next);
    }

    public int runTick() {
        int completed = 0;
        while (completed < workPerTick && !work.isEmpty()) {
            IncrementalWork current = work.getFirst();
            current.runNext();
            pendingSteps--;
            completed++;
            if (!current.hasNext()) {
                work.removeFirst();
            }
        }
        return completed;
    }

    public boolean hasWork() {
        return !work.isEmpty();
    }

    public long pendingSteps() {
        return pendingSteps;
    }

    public static long ticksRequired(long operations, int workPerTick) {
        if (operations < 0 || workPerTick <= 0) {
            throw new IllegalArgumentException(
                    "operations must be non-negative and workPerTick must be positive");
        }
        return operations / workPerTick + (operations % workPerTick == 0 ? 0 : 1);
    }
}
