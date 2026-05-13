package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxException;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsonlStdinWriter")
class JsonlStdinWriterTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record TestCounters(
        Counter queueFull,
        Counter writeTimeout,
        Counter brokenPipe,
        Counter closed,
        Counter sendBytes
    ) {
        static TestCounters of(SimpleMeterRegistry reg) {
            return new TestCounters(
                reg.counter("test.queue.full"),
                reg.counter("test.write.timeout"),
                reg.counter("test.broken.pipe"),
                reg.counter("test.closed"),
                reg.counter("test.send.bytes")
            );
        }
    }

    @Test
    @DisplayName("send() writes JSON + newline and resolves quickly under normal conditions")
    void sendsAndAcks() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        TestCounters c = TestCounters.of(reg);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        AtomicInteger terminalFires = new AtomicInteger();

        JsonlStdinWriter writer = new JsonlStdinWriter(
            UUID.randomUUID(),
            sink,
            MAPPER,
            16,
            2_000L,
            c.queueFull(),
            c.writeTimeout(),
            c.brokenPipe(),
            c.closed(),
            c.sendBytes(),
            terminalFires::incrementAndGet,
            java.util.Map.of()
        );
        writer.start();

        writer.send(TextNode.valueOf("hello"));
        writer.send(IntNode.valueOf(42));

        // Wait for the writer thread to flush
        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() ->
                assertThat(sink.toString(StandardCharsets.UTF_8)).contains("\"hello\"\n").contains("42\n")
            );
        assertThat(c.sendBytes().count()).isGreaterThan(0);
        assertThat(terminalFires).hasValue(0);

        writer.close();
    }

    @Test
    @DisplayName("queue-full rejects send() with QUEUE_FULL counter incremented")
    void queueFullRejects() throws Exception {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        TestCounters c = TestCounters.of(reg);
        // Stream that signals the moment it enters the FIRST write (so we know the writer thread
        // has popped envelope #1 off the queue) and then blocks until released.
        CountDownLatch writerEnteredWrite = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OutputStream blocking = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                writerEnteredWrite.countDown();
                try {
                    release.await();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", ie);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                write(0);
            }
        };

        AtomicInteger terminalFires = new AtomicInteger();
        JsonlStdinWriter writer = new JsonlStdinWriter(
            UUID.randomUUID(),
            blocking,
            MAPPER,
            2,
            500L,
            c.queueFull(),
            c.writeTimeout(),
            c.brokenPipe(),
            c.closed(),
            c.sendBytes(),
            terminalFires::incrementAndGet,
            java.util.Map.of()
        );
        writer.start();

        // Fire one async send and wait deterministically for the writer to begin the write.
        Thread firstSender = new Thread(() -> {
            try {
                writer.send(TextNode.valueOf("first"));
            } catch (Exception ignored) {}
        });
        firstSender.start();
        assertThat(writerEnteredWrite.await(2, TimeUnit.SECONDS))
            .as("writer thread must enter the blocking write before we test queue-full")
            .isTrue();

        // The writer is now blocked inside write(). Queue capacity = 2. Two further async senders
        // will fill the queue and park on their acks. The fourth synchronous send must be rejected
        // — either at offer-time (queue_full) or after the 500 ms write timeout.
        Thread secondSender = new Thread(() -> {
            try {
                writer.send(TextNode.valueOf("second"));
            } catch (Exception ignored) {}
        });
        Thread thirdSender = new Thread(() -> {
            try {
                writer.send(TextNode.valueOf("third"));
            } catch (Exception ignored) {}
        });
        secondSender.start();
        thirdSender.start();

        // Poll until either rejection mechanism fires — bounded, deterministic.
        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                try {
                    writer.send(TextNode.valueOf("fourth"));
                } catch (InteractiveSandboxException expected) {
                    // Either queue_full (no slot) or write_timeout (slot, but ack never came).
                    assertThat(c.queueFull().count() + c.writeTimeout().count()).isGreaterThan(0.0);
                    return;
                }
                throw new AssertionError("Expected InteractiveSandboxException on overflow / timeout");
            });

        release.countDown();
        writer.close();
        firstSender.interrupt();
        secondSender.interrupt();
        thirdSender.interrupt();
    }

    @Test
    @DisplayName("send() after close throws CLOSED")
    void closedRejects() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        TestCounters c = TestCounters.of(reg);
        AtomicInteger terminalFires = new AtomicInteger();
        JsonlStdinWriter writer = new JsonlStdinWriter(
            UUID.randomUUID(),
            new ByteArrayOutputStream(),
            MAPPER,
            8,
            500L,
            c.queueFull(),
            c.writeTimeout(),
            c.brokenPipe(),
            c.closed(),
            c.sendBytes(),
            terminalFires::incrementAndGet,
            java.util.Map.of()
        );
        writer.start();
        writer.close();
        assertThatThrownBy(() -> writer.send(TextNode.valueOf("x")))
            .isInstanceOf(InteractiveSandboxException.class)
            .hasMessageContaining("Session is closed");
        assertThat(c.closed().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("writeStalled returns true once an in-flight write has aged past the threshold")
    void writeStalledDetection() throws Exception {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        TestCounters c = TestCounters.of(reg);
        CountDownLatch release = new CountDownLatch(1);
        OutputStream blocking = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                try {
                    release.await();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", ie);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                write(0);
            }
        };
        AtomicInteger terminalFires = new AtomicInteger();
        JsonlStdinWriter writer = new JsonlStdinWriter(
            UUID.randomUUID(),
            blocking,
            MAPPER,
            8,
            50L, // very tight threshold
            c.queueFull(),
            c.writeTimeout(),
            c.brokenPipe(),
            c.closed(),
            c.sendBytes(),
            terminalFires::incrementAndGet,
            java.util.Map.of()
        );
        writer.start();
        Thread t = new Thread(() -> {
            try {
                writer.send(TextNode.valueOf("stalled"));
            } catch (Exception ignored) {}
        });
        t.start();
        TimeUnit.MILLISECONDS.sleep(150);
        long now = System.nanoTime();
        assertThat(writer.writeStalled(now)).isTrue();
        // Trigger handleTimeout — marks writer terminal and fires the callback
        writer.onWriteTimeout();
        assertThat(writer.isTerminated()).isTrue();
        assertThat(terminalFires).hasValueGreaterThanOrEqualTo(1);
        release.countDown();
        t.interrupt();
    }

    @Test
    @DisplayName("encodes JsonNode to one line + newline (no embedded \\n surprises)")
    void encodesAsSingleLine() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        TestCounters c = TestCounters.of(reg);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        JsonlStdinWriter writer = new JsonlStdinWriter(
            UUID.randomUUID(),
            sink,
            MAPPER,
            8,
            2_000L,
            c.queueFull(),
            c.writeTimeout(),
            c.brokenPipe(),
            c.closed(),
            c.sendBytes(),
            () -> {},
            java.util.Map.of()
        );
        writer.start();
        JsonNode node = MAPPER.createObjectNode().put("payload", "a\nb"); // contains a real LF in the string value
        writer.send(node);
        // Jackson escapes the real LF inside the string as backslash-n, then the writer appends ONE LF.
        // Final wire bytes: {"payload":"a\nb"}<LF>
        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> {
                String actual = sink.toString(StandardCharsets.UTF_8);
                assertThat(actual).contains("\"payload\":\"a\\nb\"}");
                assertThat(actual).endsWith("\n");
            });
        // Exactly one real LF (the frame terminator); the embedded LF was escaped, not emitted raw.
        String result = sink.toString(StandardCharsets.UTF_8);
        long newlineCount = result
            .chars()
            .filter(ch -> ch == '\n')
            .count();
        assertThat(newlineCount).isEqualTo(1L);
        writer.close();
    }
}
