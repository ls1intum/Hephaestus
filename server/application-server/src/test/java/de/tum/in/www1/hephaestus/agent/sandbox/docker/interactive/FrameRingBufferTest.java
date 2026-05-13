package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FrameRingBuffer")
class FrameRingBufferTest extends BaseUnitTest {

    private Counter dropped;
    private FrameRingBuffer buffer;

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        dropped = registry.counter("test.dropped");
    }

    @Nested
    @DisplayName("Capacity & offers")
    class Capacity {

        @Test
        @DisplayName("rejects non-positive capacity")
        void rejectsBadCapacity() {
            assertThatThrownBy(() -> new FrameRingBuffer(0, dropped)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new FrameRingBuffer(-3, dropped)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepts under-capacity offers without dropping")
        void underCapacityNoDropping() {
            buffer = new FrameRingBuffer(4, dropped);
            buffer.offer(IntNode.valueOf(1));
            buffer.offer(IntNode.valueOf(2));
            buffer.offer(IntNode.valueOf(3));
            assertThat(buffer.size()).isEqualTo(3);
            assertThat(dropped.count()).isZero();
        }

        @Test
        @DisplayName("drops oldest on overflow and increments counter")
        void dropOldestOnOverflow() {
            buffer = new FrameRingBuffer(3, dropped);
            for (int i = 1; i <= 5; i++) {
                buffer.offer(IntNode.valueOf(i));
            }
            assertThat(buffer.size()).isEqualTo(3);
            // 2 frames evicted (the first two)
            assertThat(dropped.count()).isEqualTo(2.0);
            List<JsonNode> snap = buffer.snapshotSince(-1L);
            assertThat(snap).extracting(JsonNode::intValue).containsExactly(3, 4, 5);
        }
    }

    @Nested
    @DisplayName("Snapshot semantics")
    class Snapshot {

        @Test
        @DisplayName("snapshotSince(-1) returns all retained frames in arrival order")
        void snapshotAll() {
            buffer = new FrameRingBuffer(4, dropped);
            buffer.offer(IntNode.valueOf(10));
            buffer.offer(IntNode.valueOf(20));
            buffer.offer(IntNode.valueOf(30));
            assertThat(buffer.snapshotSince(-1L)).extracting(JsonNode::intValue).containsExactly(10, 20, 30);
        }

        @Test
        @DisplayName("snapshotSince(cursor) returns only frames newer than cursor")
        void snapshotSinceCursor() {
            buffer = new FrameRingBuffer(4, dropped);
            long s0 = buffer.offer(IntNode.valueOf(100));
            long s1 = buffer.offer(IntNode.valueOf(200));
            long s2 = buffer.offer(IntNode.valueOf(300));
            assertThat(buffer.snapshotSince(s0)).extracting(JsonNode::intValue).containsExactly(200, 300);
            assertThat(buffer.snapshotSince(s1)).extracting(JsonNode::intValue).containsExactly(300);
            assertThat(buffer.snapshotSince(s2)).isEmpty();
        }

        @Test
        @DisplayName("monotonic sequence numbers — never reused after eviction")
        void sequenceMonotonic() {
            buffer = new FrameRingBuffer(2, dropped);
            long s0 = buffer.offer(IntNode.valueOf(1));
            long s1 = buffer.offer(IntNode.valueOf(2));
            long s2 = buffer.offer(IntNode.valueOf(3));
            long s3 = buffer.offer(IntNode.valueOf(4));
            assertThat(s0).isLessThan(s1);
            assertThat(s1).isLessThan(s2);
            assertThat(s2).isLessThan(s3);
            assertThat(buffer.latestSequence()).isEqualTo(s3);
        }
    }
}
