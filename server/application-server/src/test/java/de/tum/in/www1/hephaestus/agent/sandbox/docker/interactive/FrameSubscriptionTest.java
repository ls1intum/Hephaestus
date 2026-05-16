package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FrameSubscription")
class FrameSubscriptionTest extends BaseUnitTest {

    @Test
    @DisplayName("delivers offered frames in order")
    void deliversInOrder() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        Counter dropped = reg.counter("test.drop");
        Counter errors = reg.counter("test.err");
        CopyOnWriteArrayList<Integer> received = new CopyOnWriteArrayList<>();

        FrameSubscription sub = new FrameSubscription(
            frame -> received.add(frame.intValue()),
            frame -> true,
            16,
            dropped,
            errors,
            () -> {}
        );
        sub.start();

        sub.offer(IntNode.valueOf(1));
        sub.offer(IntNode.valueOf(2));
        sub.offer(IntNode.valueOf(3));

        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(received).containsExactly(1, 2, 3));
        assertThat(dropped.count()).isZero();
        sub.dispose();
    }

    @Test
    @DisplayName("slow listener drops oldest beyond bounded queue, counter increments")
    void slowListenerDropsOldest() throws Exception {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        Counter dropped = reg.counter("test.drop");
        Counter errors = reg.counter("test.err");
        CountDownLatch gate = new CountDownLatch(1);
        CopyOnWriteArrayList<Integer> received = new CopyOnWriteArrayList<>();

        FrameSubscription sub = new FrameSubscription(
            frame -> {
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                received.add(frame.intValue());
            },
            frame -> true,
            4,
            dropped,
            errors,
            () -> {}
        );
        sub.start();

        for (int i = 0; i < 10; i++) {
            sub.offer(IntNode.valueOf(i));
        }
        // capacity=4 → 5 enter (1 in-flight + 4 queued); 5 evict.
        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(dropped.count()).isGreaterThan(0));

        gate.countDown();
        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(received).isNotEmpty());
        sub.dispose();
    }

    @Test
    @DisplayName("listener throwing does not kill the dispatcher; error counter increments")
    void listenerThrowsHandled() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        Counter dropped = reg.counter("test.drop");
        Counter errors = reg.counter("test.err");
        AtomicInteger called = new AtomicInteger();

        FrameSubscription sub = new FrameSubscription(
            frame -> {
                called.incrementAndGet();
                throw new RuntimeException("boom");
            },
            frame -> true,
            8,
            dropped,
            errors,
            () -> {}
        );
        sub.start();

        sub.offer(IntNode.valueOf(1));
        sub.offer(IntNode.valueOf(2));
        sub.offer(IntNode.valueOf(3));

        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> {
                assertThat(called.get()).isEqualTo(3);
                assertThat(errors.count()).isEqualTo(3.0);
            });
        sub.dispose();
    }

    @Test
    @DisplayName("dispose() is idempotent and runs onDispose exactly once")
    void disposeIdempotent() throws Exception {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        AtomicInteger onDisposeFires = new AtomicInteger();
        FrameSubscription sub = new FrameSubscription(
            frame -> {},
            frame -> true,
            4,
            reg.counter("test.drop"),
            reg.counter("test.err"),
            onDisposeFires::incrementAndGet
        );
        sub.start();
        assertThat(sub.isDisposed()).isFalse();
        sub.dispose();
        sub.dispose();
        sub.dispose();
        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> {
                assertThat(sub.isDisposed()).isTrue();
                assertThat(onDisposeFires).hasValue(1);
            });
    }

    @Test
    @DisplayName("offer after dispose is a no-op")
    void offerAfterDisposeNoOp() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        List<Integer> received = new CopyOnWriteArrayList<>();
        FrameSubscription sub = new FrameSubscription(
            frame -> received.add(frame.intValue()),
            frame -> true,
            4,
            reg.counter("test.drop"),
            reg.counter("test.err"),
            () -> {}
        );
        sub.start();
        sub.dispose();
        sub.offer(IntNode.valueOf(99));
        sub.offer(IntNode.valueOf(100));
        assertThat(received).doesNotContain(99, 100);
    }
}
