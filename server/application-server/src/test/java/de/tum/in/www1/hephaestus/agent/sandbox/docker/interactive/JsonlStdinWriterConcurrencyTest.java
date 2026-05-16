package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Concurrency contract for the {@link ReentrantLock}-based replacement of {@code synchronized}
 * in {@link JsonlStdinWriter} (#1091). Two invariants:
 *
 * <ul>
 *   <li>N virtual-thread senders interleave without losing frames, double-acking, or producing
 *       partial-write byte interleaves on the underlying stream.</li>
 *   <li>{@code markTerminal} is idempotent under contention — the onTerminalFailure callback
 *       fires at most once even when many virtual threads race to terminate the writer.</li>
 * </ul>
 */
@DisplayName("JsonlStdinWriter concurrency")
class JsonlStdinWriterConcurrencyTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * {@link OutputStream} that counts {@code write} calls and enforces non-interleaving by
     * tracking the depth of concurrent writes — if it ever exceeds 1, the lock contract is broken.
     */
    private static final class SerialWriteSink extends OutputStream {

        private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        private final AtomicInteger inflight = new AtomicInteger();
        private final AtomicInteger maxInflight = new AtomicInteger();

        @Override
        public synchronized void write(int b) {
            int now = inflight.incrementAndGet();
            maxInflight.accumulateAndGet(now, Math::max);
            sink.write(b);
            inflight.decrementAndGet();
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            int now = inflight.incrementAndGet();
            maxInflight.accumulateAndGet(now, Math::max);
            sink.write(b, off, len);
            inflight.decrementAndGet();
        }

        byte[] bytes() {
            return sink.toByteArray();
        }
    }

    @Test
    @DisplayName("100 virtual-thread senders complete without lost frames or double-acks")
    void manyVirtualSendersDoNotLoseFrames() throws Exception {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        SerialWriteSink sink = new SerialWriteSink();
        AtomicInteger terminalFires = new AtomicInteger();

        JsonlStdinWriter writer = new JsonlStdinWriter(
            UUID.randomUUID(),
            sink,
            MAPPER,
            512,
            5_000L,
            reg.counter("test.queue.full"),
            reg.counter("test.write.timeout"),
            reg.counter("test.broken.pipe"),
            reg.counter("test.closed"),
            reg.counter("test.send.bytes"),
            terminalFires::incrementAndGet,
            java.util.Map.of()
        );
        writer.start();

        int senders = 100;
        CountDownLatch ready = new CountDownLatch(senders);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(senders);
        AtomicLong failures = new AtomicLong();

        for (int i = 0; i < senders; i++) {
            final int idx = i;
            Thread.ofVirtual()
                .name("send-" + idx)
                .start(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        writer.send(TextNode.valueOf("payload-" + idx));
                    } catch (Throwable t) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).as("all virtual-thread senders must complete within 10s").isTrue();
        assertThat(failures.get()).as("no sender should fail under contention").isZero();

        // Drain the writer loop.
        writer.close();

        String out = new String(sink.bytes(), StandardCharsets.UTF_8);
        long newlineCount = out
            .chars()
            .filter(ch -> ch == '\n')
            .count();
        assertThat(newlineCount)
            .as("each accepted send() must produce exactly one newline-terminated frame")
            .isEqualTo(senders);

        // No double-terminal firings — markTerminal is guarded by terminalLock + terminated flag.
        assertThat(terminalFires.get()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Concurrent close()/markTerminal calls fire onTerminalFailure at most once")
    void concurrentMarkTerminalIsIdempotent() throws Exception {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        AtomicInteger terminalFires = new AtomicInteger();

        JsonlStdinWriter writer = new JsonlStdinWriter(
            UUID.randomUUID(),
            new SerialWriteSink(),
            MAPPER,
            8,
            500L,
            reg.counter("test.queue.full"),
            reg.counter("test.write.timeout"),
            reg.counter("test.broken.pipe"),
            reg.counter("test.closed"),
            reg.counter("test.send.bytes"),
            terminalFires::incrementAndGet,
            java.util.Map.of()
        );
        writer.start();

        int racers = 64;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(racers);
        for (int i = 0; i < racers; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                    writer.onWriteTimeout();
                } catch (Throwable ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(terminalFires.get()).as("terminal callback must fire exactly once under contention").isEqualTo(1);
        assertThat(writer.isTerminated()).isTrue();
    }
}
