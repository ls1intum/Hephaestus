package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the C4 subscriber-ordering bug surfaced by the PE concurrency audit.
 *
 * <p>Before the fix, a subscriber that arrived while the pump was actively fanning out frames
 * could observe interleaved [live N+1, snapshot 1..N, live N+2] — violating the
 * {@link de.tum.in.www1.hephaestus.agent.sandbox.spi.AttachedSandbox#subscribe} contract.
 *
 * <p>We don't construct a full sandbox here (that needs Docker). We construct the two collaborators
 * that race — a ring buffer and a fresh subscription — and drive a producer thread that races
 * against the subscribe call, asserting the subscribe-thread-side ordering is monotonic.
 */
@DisplayName("Subscribe ordering: snapshot precedes live frames")
class SubscribeOrderingPropertyTest extends BaseUnitTest {

    @Test
    @DisplayName("a subscriber that races a producer sees snapshot frames before any live frames")
    void snapshotThenLive() throws Exception {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        FrameRingBuffer ring = new FrameRingBuffer(1024, reg.counter("test.dropped"));
        Object lock = new Object();
        CopyOnWriteArrayList<FrameSubscription> subs = new CopyOnWriteArrayList<>();

        // Pre-seed the ring with the snapshot range.
        for (int i = 1; i <= 100; i++) {
            ring.offer(IntNode.valueOf(i));
        }

        List<Integer> received = new ArrayList<>();
        CountDownLatch listenerCalledOnce = new CountDownLatch(1);
        FrameSubscription[] holder = new FrameSubscription[1];
        FrameSubscription sub = new FrameSubscription(
            frame -> {
                received.add(frame.intValue());
                listenerCalledOnce.countDown();
            },
            4096,
            reg.counter("test.sub.dropped"),
            reg.counter("test.sub.error"),
            () -> {}
        );
        holder[0] = sub;

        // Producer: hammers the lock with new frames, mimicking the pump's fan-out path.
        Thread producer = Thread.ofVirtual().start(() -> {
            for (int i = 101; i <= 1100; i++) {
                synchronized (lock) {
                    ring.offer(IntNode.valueOf(i));
                    for (FrameSubscription s : subs) {
                        s.offer(IntNode.valueOf(i));
                    }
                }
            }
        });

        // Subscribe: must mirror the production fix — under the same lock, snapshot then add.
        synchronized (lock) {
            List<JsonNode> snapshot = ring.snapshotSince(-1L);
            subs.add(sub);
            sub.start();
            for (JsonNode frame : snapshot) {
                sub.offer(frame);
            }
        }
        producer.join();

        // Wait for the subscriber's dispatcher to drain its queue.
        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(received.size()).isGreaterThan(1000));

        // The KEY assertion: the received sequence is strictly monotonic. If a live frame ever
        // landed BEFORE a snapshot frame, the sequence would dip — assertion would fail.
        for (int i = 1; i < received.size(); i++) {
            assertThat(received.get(i)).as("frame %d not monotonic", i).isGreaterThan(received.get(i - 1));
        }
        listenerCalledOnce.await();
        sub.dispose();
    }
}
