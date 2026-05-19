package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.mentor.chat.exception.MentorRunnerException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.AttachedSandbox;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Wire-level contract for {@link MentorRunnerClient}. The fake sandbox below is a hand-rolled
 * implementation of {@link AttachedSandbox} that buffers sent frames and pushes replies through
 * a registered listener — letting us drive the JSON-RPC protocol synchronously without Docker.
 */
@DisplayName("MentorRunnerClient")
class MentorRunnerClientTest extends BaseUnitTest {

    private FakeSandbox sandbox;
    private MentorRunnerClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final CopyOnWriteArrayList<JsonNode> events = new CopyOnWriteArrayList<>();
    private final AtomicReference<MentorRunnerClient.FetchContextRequest> lastFetchContext = new AtomicReference<>();
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp() {
        sandbox = new FakeSandbox();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        client = new MentorRunnerClient(
            sandbox,
            mapper,
            events::add,
            req -> {
                lastFetchContext.set(req);
                return mapper.createObjectNode().put("ok", true);
            },
            scheduler
        );
        client.start();
    }

    @AfterEach
    void tearDown() {
        client.close();
        scheduler.shutdownNow();
    }

    @Test
    @DisplayName("hello() correlates request id and resolves with the result body")
    void helloRoundtrip() throws Exception {
        CompletableFuture<JsonNode> future = client.hello();
        // Drain the sent frame and respond with a matching id.
        JsonNode request = sandbox.takeFrame();
        long id = request.get("id").asLong();
        assertThat(request.get("method").asText()).isEqualTo("hello");

        sandbox.pushFrame(responseOf(id, mapper.createObjectNode().put("protocolVersion", 1)));

        JsonNode result = future.get(2, TimeUnit.SECONDS);
        assertThat(result.get("protocolVersion").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("Error response surfaces as MentorRunnerException with the code")
    void errorResponseBecomesException() throws Exception {
        CompletableFuture<JsonNode> future = client.openThread(UUID.randomUUID());
        JsonNode request = sandbox.takeFrame();
        long id = request.get("id").asLong();
        ObjectNode error = mapper.createObjectNode();
        ObjectNode errBody = error.putObject("error");
        errBody.put("code", -32001);
        errBody.put("message", "turn already in flight");
        error.put("jsonrpc", "2.0");
        error.put("id", id);
        sandbox.pushFrame(error);

        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(MentorRunnerException.class)
            .hasMessageContaining("-32001");
    }

    @Test
    @DisplayName("PI_ERROR / INVALID_STATE codes mark the exception as sandbox-poisoning")
    void poisoningErrorCodes() {
        MentorRunnerException pi = new MentorRunnerException(
            MentorRunnerException.CODE_PI_ERROR,
            "pi",
            mapper.nullNode()
        );
        MentorRunnerException invalid = new MentorRunnerException(
            MentorRunnerException.CODE_INVALID_STATE,
            "bad",
            mapper.nullNode()
        );
        MentorRunnerException other = new MentorRunnerException(-32001, "in flight", mapper.nullNode());

        assertThat(pi.poisonsSandbox()).isTrue();
        assertThat(invalid.poisonsSandbox()).isTrue();
        assertThat(other.poisonsSandbox()).isFalse();
    }

    @Test
    @DisplayName("Event notifications flow to the registered consumer")
    void eventFanOutToConsumer() {
        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "event");
        ObjectNode params = notification.putObject("params");
        params.put("threadId", UUID.randomUUID().toString());
        params.set("event", mapper.createObjectNode().put("type", "runner_ready").put("protocolVersion", 1));

        sandbox.pushFrame(notification);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("type").asText()).isEqualTo("runner_ready");
    }

    @Test
    @DisplayName("fetch_context callback writes back a result frame on stdin")
    void fetchContextRoundTrip() {
        ObjectNode callback = mapper.createObjectNode();
        callback.put("jsonrpc", "2.0");
        callback.put("id", 9999L);
        callback.put("method", "fetch_context");
        ObjectNode params = callback.putObject("params");
        params.put("threadId", UUID.randomUUID().toString());
        params.put("path", "workspace.json");

        sandbox.pushFrame(callback);

        // Server-side response sent back to runner.
        JsonNode response = sandbox.takeFrame();
        assertThat(response.get("id").asLong()).isEqualTo(9999L);
        assertThat(response.get("result").get("content").get("ok").asBoolean()).isTrue();
        assertThat(lastFetchContext.get().path()).isEqualTo("workspace.json");
    }

    @Test
    @DisplayName("fetch_context callback preserves string ids — the runner uses fc-<uuid>, not numerics")
    void fetchContextRoundTrip_preservesStringIds() {
        // Regression: an earlier impl coerced frame.get("id").asLong() → 0 for any non-numeric
        // id, then echoed back `id: 0` which the runner's pendingFetchContexts (keyed by the
        // original string) never matched. Result: every `fetch_context` LLM tool call hung
        // until the runner's 10s timeout fired.
        String callbackId = "fc-" + UUID.randomUUID();
        ObjectNode callback = mapper.createObjectNode();
        callback.put("jsonrpc", "2.0");
        callback.put("id", callbackId);
        callback.put("method", "fetch_context");
        ObjectNode params = callback.putObject("params");
        params.put("threadId", UUID.randomUUID().toString());
        params.put("path", "workspace.json");

        sandbox.pushFrame(callback);

        JsonNode response = sandbox.takeFrame();
        assertThat(response.get("id").isTextual()).as("id must remain a string").isTrue();
        assertThat(response.get("id").asText()).isEqualTo(callbackId);
        assertThat(response.get("result").get("content").get("ok").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("fetch_context error response preserves string id")
    void fetchContextErrorRoundTrip_preservesStringIds() {
        String callbackId = "fc-" + UUID.randomUUID();
        ObjectNode callback = mapper.createObjectNode();
        callback.put("jsonrpc", "2.0");
        callback.put("id", callbackId);
        callback.put("method", "fetch_context");
        callback.putObject("params"); // missing required fields → -32600

        sandbox.pushFrame(callback);

        JsonNode response = sandbox.takeFrame();
        assertThat(response.get("id").isTextual()).isTrue();
        assertThat(response.get("id").asText()).isEqualTo(callbackId);
        assertThat(response.get("error").get("code").asInt()).isEqualTo(-32600);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────────────

    private ObjectNode responseOf(long id, JsonNode result) {
        ObjectNode out = mapper.createObjectNode();
        out.put("jsonrpc", "2.0");
        out.put("id", id);
        out.set("result", result);
        return out;
    }

    /** Minimal AttachedSandbox stub: queue of sent frames + push API for the test driver. */
    static final class FakeSandbox implements AttachedSandbox {

        private final UUID sessionId = UUID.randomUUID();
        private final java.util.concurrent.LinkedBlockingDeque<JsonNode> sentFrames =
            new java.util.concurrent.LinkedBlockingDeque<>();
        private final CopyOnWriteArrayList<Consumer<JsonNode>> listeners = new CopyOnWriteArrayList<>();

        @Override
        public UUID sessionId() {
            return sessionId;
        }

        @Override
        public String userId() {
            return "u1";
        }

        @Override
        public String workspaceId() {
            return "w1";
        }

        @Override
        public void send(JsonNode frame) throws InteractiveSandboxException {
            sentFrames.add(frame);
        }

        @Override
        public Disposable subscribe(Consumer<JsonNode> listener) {
            listeners.add(listener);
            return () -> listeners.remove(listener);
        }

        @Override
        public Instant lastActivityAt() {
            return Instant.now();
        }

        @Override
        public Duration idleFor() {
            return Duration.ZERO;
        }

        @Override
        public void close(Duration graceTimeout) {
            listeners.clear();
        }

        JsonNode takeFrame() {
            try {
                JsonNode frame = sentFrames.pollFirst(2, TimeUnit.SECONDS);
                if (frame == null) {
                    throw new AssertionError("Expected a sent frame within 2s");
                }
                return frame;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for frame", e);
            }
        }

        void pushFrame(JsonNode frame) {
            for (Consumer<JsonNode> listener : listeners) {
                listener.accept(frame);
            }
        }
    }
}
