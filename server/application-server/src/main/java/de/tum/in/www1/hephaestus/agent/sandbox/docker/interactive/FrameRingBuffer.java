package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded ring buffer of frames with drop-oldest on overflow. Frames carry a monotonic sequence
 * number; {@link #snapshotSince} lets a subscriber resume without duplicates from a known cursor.
 *
 * <p>The {@code onDrop} callback fires exactly once per evicted frame. The buffer itself stays
 * metric-agnostic — the callback can route to one or more counters (debounced or not) without
 * pulling Micrometer into the buffer's surface. Callbacks must be cheap and non-blocking; they
 * run while holding the buffer's monitor.
 */
final class FrameRingBuffer {

    private final int capacity;
    private final ArrayDeque<Entry> entries;
    private final Runnable onDrop;
    private long nextSequence;

    FrameRingBuffer(int capacity, Runnable onDrop) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got: " + capacity);
        }
        this.capacity = capacity;
        this.entries = new ArrayDeque<>(capacity);
        this.onDrop = Objects.requireNonNull(onDrop, "onDrop");
        this.nextSequence = 0L;
    }

    synchronized long offer(JsonNode frame) {
        long seq = nextSequence++;
        if (entries.size() == capacity) {
            entries.removeFirst();
            try {
                onDrop.run();
            } catch (RuntimeException ignored) {
                // A metric callback throwing must not corrupt the buffer's state. The observability
                // path is best-effort; the buffer's correctness guarantees are unconditional.
            }
        }
        entries.addLast(new Entry(seq, frame));
        return seq;
    }

    /** Frames with sequence {@code > since}, in arrival order. Pass {@code -1} for the full snapshot. */
    synchronized List<JsonNode> snapshotSince(long since) {
        List<JsonNode> result = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            if (e.sequence > since) {
                result.add(e.frame);
            }
        }
        return result;
    }

    /** Sequence of the most recently added frame, or {@code -1} if empty. */
    synchronized long latestSequence() {
        return nextSequence - 1;
    }

    int capacity() {
        return capacity;
    }

    synchronized int size() {
        return entries.size();
    }

    private record Entry(long sequence, JsonNode frame) {}
}
