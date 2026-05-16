package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Server-side filter contract for {@link FrameSubscription}: the
 * {@code AttachedSandbox.subscribe(Cursor, Predicate, Consumer)} predicate is applied INSIDE the
 * subscription dispatch (before queue enqueue), so frames the filter rejects:
 *
 * <ul>
 *   <li>do not consume that subscriber's queue capacity,</li>
 *   <li>do not fire the dropped-frame counter (rejection is silent),</li>
 *   <li>do not surface to other subscribers' listeners (each filter is independent).</li>
 * </ul>
 *
 * <p>Exercises {@link FrameSubscription} directly to keep the test scope tight (Docker isn't
 * needed). The production adapter wires the same code path.
 */
@DisplayName("FrameSubscription server-side predicate filtering")
class AttachedSandboxFilterTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Frames matching the filter are dispatched; others are silently dropped (no counter fire)")
    void filterPassesAndSuppresses() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        CopyOnWriteArrayList<JsonNode> seen = new CopyOnWriteArrayList<>();

        UUID threadA = UUID.randomUUID();
        UUID threadB = UUID.randomUUID();

        // Subscriber A only accepts frames tagged with threadA.
        Predicate<JsonNode> onlyA = frame -> {
            JsonNode params = frame.get("params");
            if (params == null) return true;
            JsonNode tid = params.get("threadId");
            return tid == null || tid.isNull() || tid.asText().equals(threadA.toString());
        };
        FrameSubscription subA = new FrameSubscription(
            seen::add,
            onlyA,
            16,
            reg.counter("test.dropped"),
            reg.counter("test.error"),
            () -> {}
        );
        subA.start();

        try {
            subA.offer(notification(threadA, "hello-A"));
            subA.offer(notification(threadB, "hello-B")); // suppressed by filter
            subA.offer(notification(null, "broadcast")); // crosses filter

            await()
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(seen).hasSize(2));

            assertThat(seen.get(0).get("params").get("text").asText()).isEqualTo("hello-A");
            assertThat(seen.get(1).get("params").get("text").asText()).isEqualTo("broadcast");
            // Rejected frames are silently filtered; the drop counter is reserved for queue-
            // overflow events. Otherwise a busy filter would dominate the drop metric.
            assertThat(reg.counter("test.dropped").count())
                .as("rejected frames must NOT increment the drop counter")
                .isZero();
        } finally {
            subA.dispose();
        }
    }

    @Test
    @DisplayName("Two subscribers with disjoint filters see disjoint frames on the SAME source stream")
    void twoSubscribersFilterIndependently() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        CopyOnWriteArrayList<JsonNode> seenA = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<JsonNode> seenB = new CopyOnWriteArrayList<>();

        UUID threadA = UUID.randomUUID();
        UUID threadB = UUID.randomUUID();

        FrameSubscription subA = new FrameSubscription(
            seenA::add,
            byThread(threadA),
            16,
            reg.counter("test.dropped"),
            reg.counter("test.error"),
            () -> {}
        );
        FrameSubscription subB = new FrameSubscription(
            seenB::add,
            byThread(threadB),
            16,
            reg.counter("test.dropped"),
            reg.counter("test.error"),
            () -> {}
        );
        subA.start();
        subB.start();

        try {
            JsonNode frameA = notification(threadA, "to-A");
            JsonNode frameB = notification(threadB, "to-B");
            JsonNode broadcast = notification(null, "to-all");
            // Production: a single pump fan-outs each frame to every subscription.
            for (FrameSubscription sub : java.util.List.of(subA, subB)) {
                sub.offer(frameA);
                sub.offer(frameB);
                sub.offer(broadcast);
            }

            await()
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    assertThat(seenA).hasSize(2); // frameA + broadcast
                    assertThat(seenB).hasSize(2); // frameB + broadcast
                });
            assertThat(seenA)
                .as("A must not observe B's frame")
                .noneMatch(f -> f.get("params").get("text").asText().equals("to-B"));
            assertThat(seenB)
                .as("B must not observe A's frame")
                .noneMatch(f -> f.get("params").get("text").asText().equals("to-A"));
        } finally {
            subA.dispose();
            subB.dispose();
        }
    }

    @Test
    @DisplayName("A throwing filter does not poison the subscription; rejected frames are counted as errors")
    void throwingFilterDegradesGracefully() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        CopyOnWriteArrayList<JsonNode> seen = new CopyOnWriteArrayList<>();
        FrameSubscription sub = new FrameSubscription(
            seen::add,
            frame -> {
                throw new RuntimeException("synthetic filter failure");
            },
            16,
            reg.counter("test.dropped"),
            reg.counter("test.error"),
            () -> {}
        );
        sub.start();
        try {
            sub.offer(notification(UUID.randomUUID(), "ignored"));
            // Give the dispatcher a moment to settle — the throw is in offer(), not dispatch,
            // so there's nothing to await on; assert immediately.
            assertThat(seen).isEmpty();
            assertThat(reg.counter("test.error").count())
                .as("throwing filters count as subscriber errors so ops can see them")
                .isEqualTo(1.0);
        } finally {
            sub.dispose();
        }
    }

    private static JsonNode notification(UUID threadId, String text) {
        ObjectNode frame = MAPPER.createObjectNode();
        frame.put("jsonrpc", "2.0");
        frame.put("method", "event");
        ObjectNode params = frame.putObject("params");
        if (threadId == null) {
            params.putNull("threadId");
        } else {
            params.put("threadId", threadId.toString());
        }
        params.put("text", text);
        return frame;
    }

    private static Predicate<JsonNode> byThread(UUID threadId) {
        String expected = threadId.toString();
        return frame -> {
            JsonNode params = frame.get("params");
            if (params == null) return true;
            JsonNode tid = params.get("threadId");
            return tid == null || tid.isNull() || tid.asText().equals(expected);
        };
    }
}
