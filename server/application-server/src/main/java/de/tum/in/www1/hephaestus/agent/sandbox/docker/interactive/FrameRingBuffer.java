package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Counter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded ring buffer of frames with drop-oldest semantics on overflow.
 *
 * <p>One instance per session. Stores the most-recent N frames so that late subscribers can
 * replay recent context — primarily an SSE reconnect affordance for #1071. When full, the oldest
 * frame is evicted and {@link #droppedCounter} is incremented; this preserves the "what just
 * happened" window that a reconnecting SSE consumer needs.
 *
 * <p>Frames are tagged with a monotonic sequence number. {@link #snapshotSince(long)} returns
 * frames with sequence {@code > since}, allowing a subscriber to resume without duplicates after
 * having seen up to a known cursor. New subscribers pass {@code -1} (or any value below the
 * lowest retained sequence) to receive the full snapshot.
 *
 * <p>All operations are synchronised on the buffer instance. Producer and consumers compete for
 * the same lock, but contention is bounded by capacity and reads complete in O(N).
 */
final class FrameRingBuffer {

    private final int capacity;
    private final ArrayDeque<Entry> entries;
    private final Counter droppedCounter;
    private long nextSequence;

    FrameRingBuffer(int capacity, Counter droppedCounter) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got: " + capacity);
        }
        this.capacity = capacity;
        this.entries = new ArrayDeque<>(capacity);
        this.droppedCounter = droppedCounter;
        this.nextSequence = 0L;
    }

    /**
     * Append a frame to the buffer.
     *
     * <p>If the buffer is at capacity, the oldest frame is evicted before the new frame is
     * appended (drop-oldest). The dropped-frame counter is incremented per eviction.
     *
     * @param frame the frame to append
     * @return the sequence number assigned to this frame
     */
    synchronized long offer(JsonNode frame) {
        long seq = nextSequence++;
        if (entries.size() == capacity) {
            entries.removeFirst();
            droppedCounter.increment();
        }
        entries.addLast(new Entry(seq, frame));
        return seq;
    }

    /**
     * Return frames with sequence number strictly greater than {@code since}, in arrival order.
     *
     * @param since the cursor; {@code -1} (or any value below the oldest retained sequence)
     *     returns the full snapshot
     * @return a fresh list (safe to iterate without holding the buffer lock)
     */
    synchronized List<JsonNode> snapshotSince(long since) {
        List<JsonNode> result = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            if (e.sequence > since) {
                result.add(e.frame);
            }
        }
        return result;
    }

    /** @return the sequence number of the most recently added frame, or {@code -1} if empty. */
    synchronized long latestSequence() {
        return nextSequence - 1;
    }

    /** @return the configured capacity. */
    int capacity() {
        return capacity;
    }

    /** @return current number of buffered frames. */
    synchronized int size() {
        return entries.size();
    }

    private record Entry(long sequence, JsonNode frame) {}
}
