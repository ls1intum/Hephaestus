package de.tum.in.www1.hephaestus.agent.mentor.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.mentor.MentorReplayMessage;
import de.tum.in.www1.hephaestus.agent.mentor.chat.exception.MentorRunnerException;
import de.tum.in.www1.hephaestus.agent.mentor.chat.exception.MentorRunnerTimeoutException;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.AttachedSandbox;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import reactor.core.Disposable;

/**
 * JSON-RPC 2.0 wrapper around an {@link AttachedSandbox}. Owns id correlation, response
 * routing, event fan-out, and runner-callback dispatch. Created per attached session.
 */
public final class MentorRunnerClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MentorRunnerClient.class);

    public static final int PROTOCOL_VERSION = 1;
    public static final Duration DEFAULT_CONTROL_TIMEOUT = Duration.ofSeconds(10);
    /**
     * Deadline for the {@code prompt} JSON-RPC <em>ack</em> — not a turn duration. The runner
     * replies {@code {accepted:true}} synchronously after binding the thread and arming its
     * watchdog (see {@code pi-mentor-runner.mjs#handlePrompt} → {@code sendResult(id, {accepted:true})}),
     * then fires {@code runtime.session.prompt(...)} fire-and-forget. Turn streaming and
     * cancellation are observed via subscribed events; the runner's 120s + 30s watchdog owns
     * the actual turn deadline. This timeout exists only to surface a runner hang during
     * {@code bindThread}/{@code switchSession} (typical: tens of milliseconds; pathological:
     * multiple seconds on a large session JSONL). The generous 165s upper bound leaves slack
     * for a cold-start cohort awaiting {@code ensureRuntime} behind a single mutex; in
     * practice the future resolves in &lt;1s on warm runners.
     */
    public static final Duration DEFAULT_PROMPT_TIMEOUT = Duration.ofSeconds(165);

    private final AttachedSandbox sandbox;
    private final ObjectMapper objectMapper;
    private final Consumer<JsonNode> onEvent;
    private final Function<FetchContextRequest, JsonNode> fetchContextHandler;
    private final ScheduledExecutorService timeoutScheduler;

    /**
     * Thread this client is bound to. The underlying sandbox is shared by
     * {@code (userId, workspaceId)} so multiple clients (one per concurrent thread of the
     * same user-workspace) subscribe to the SAME frame stream. Without this filter, every
     * client would see every thread's events — chat sessions would cross-talk into each
     * other's browser tabs and translator state. {@code null} means "accept anything"
     * (legacy tests that pre-date multi-session use this constructor; production always
     * passes the thread id).
     */
    @Nullable
    private final UUID boundThreadId;

    private final AtomicLong idGen = new AtomicLong();
    private final ConcurrentHashMap<Long, PendingCall> pending = new ConcurrentHashMap<>();

    @Nullable
    private Disposable subscription;

    public MentorRunnerClient(
        AttachedSandbox sandbox,
        ObjectMapper objectMapper,
        Consumer<JsonNode> onEvent,
        Function<FetchContextRequest, JsonNode> fetchContextHandler,
        ScheduledExecutorService timeoutScheduler
    ) {
        this(sandbox, objectMapper, onEvent, fetchContextHandler, timeoutScheduler, null);
    }

    public MentorRunnerClient(
        AttachedSandbox sandbox,
        ObjectMapper objectMapper,
        Consumer<JsonNode> onEvent,
        Function<FetchContextRequest, JsonNode> fetchContextHandler,
        ScheduledExecutorService timeoutScheduler,
        @Nullable UUID boundThreadId
    ) {
        this.sandbox = Objects.requireNonNull(sandbox, "sandbox");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.onEvent = Objects.requireNonNull(onEvent, "onEvent");
        this.fetchContextHandler = Objects.requireNonNull(fetchContextHandler, "fetchContextHandler");
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler");
        this.boundThreadId = boundThreadId;
    }

    /** Subscribe to the underlying sandbox; safe to call once per client instance. */
    public synchronized void start() {
        if (subscription != null) {
            return;
        }
        this.subscription = sandbox.subscribe(this::onFrame);
    }

    public CompletableFuture<JsonNode> hello() {
        return call("hello", objectMapper.createObjectNode(), DEFAULT_CONTROL_TIMEOUT);
    }

    public CompletableFuture<JsonNode> openThread(UUID threadId) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("threadId", threadId.toString());
        return call("open_thread", params, DEFAULT_CONTROL_TIMEOUT);
    }

    public CompletableFuture<JsonNode> replayContext(UUID threadId, List<MentorReplayMessage> messages) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("threadId", threadId.toString());
        ArrayNode array = params.putArray("messages");
        for (MentorReplayMessage msg : messages) {
            ObjectNode entry = array.addObject();
            entry.put("role", msg.role());
            // The runner consumes `text` directly when feeding the LLM (see
            // pi-mentor-runner.mjs#handleReplayContext). We flatten on the Java side so the
            // runner stays thin and so the AI SDK `parts` shape — which carries non-text chunks
            // (tools, reasoning) — never leaks into the LLM prompt. `parts` is still shipped
            // verbatim for forward-compat / future structured replay.
            entry.put("text", flattenParts(msg.parts()));
            entry.set("parts", msg.parts());
            entry.put("createdAt", msg.createdAt().toString());
        }
        return call("replay_context", params, DEFAULT_CONTROL_TIMEOUT);
    }

    /**
     * Flatten an AI-SDK UIMessage {@code parts} array to a single plain-text string. Concatenates
     * every {@code type=="text"} part's {@code text} field; non-text parts (tool calls, reasoning,
     * data-finding) are intentionally dropped — the runner ships them only as a recap, and the
     * LLM should reason against what the human and assistant SAID, not the tool internals.
     */
    static String flattenParts(JsonNode parts) {
        if (parts == null || !parts.isArray() || parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part == null || !part.isObject()) continue;
            JsonNode type = part.path("type");
            JsonNode text = part.path("text");
            if (type.isTextual() && "text".equals(type.asText()) && text.isTextual()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(text.asText());
            }
        }
        return sb.toString();
    }

    public CompletableFuture<JsonNode> prompt(UUID threadId, String text) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("threadId", threadId.toString());
        params.put("text", text);
        return call("prompt", params, DEFAULT_PROMPT_TIMEOUT);
    }

    public CompletableFuture<JsonNode> abort(UUID threadId) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("threadId", threadId.toString());
        return call("abort", params, DEFAULT_CONTROL_TIMEOUT);
    }

    public CompletableFuture<JsonNode> closeThread(UUID threadId) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("threadId", threadId.toString());
        return call("close_thread", params, DEFAULT_CONTROL_TIMEOUT);
    }

    public CompletableFuture<JsonNode> shutdown() {
        return call("shutdown", objectMapper.createObjectNode(), DEFAULT_CONTROL_TIMEOUT);
    }

    @Override
    public synchronized void close() {
        // synchronized so close() races with start() are deterministic — both touch
        // `subscription`. Pending-call drains via the lock-free ConcurrentHashMap so contention
        // here is bounded to the dispose() path.
        if (subscription != null) {
            try {
                subscription.dispose();
            } catch (Exception ignored) {
                // Idempotent dispose; nothing actionable.
            }
            subscription = null;
        }
        pending.forEach((id, p) -> {
            p.timeoutTask.cancel(false);
            p.future.completeExceptionally(new InteractiveSandboxException("Runner client closed"));
        });
        pending.clear();
    }

    private CompletableFuture<JsonNode> call(String method, ObjectNode params, Duration timeout) {
        long id = idGen.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(
            () -> {
                PendingCall removed = pending.remove(id);
                if (removed != null) {
                    removed.future.completeExceptionally(
                        new MentorRunnerTimeoutException("Runner did not respond to " + method + " within " + timeout)
                    );
                }
            },
            timeout.toMillis(),
            TimeUnit.MILLISECONDS
        );
        pending.put(id, new PendingCall(future, timeoutTask, method));

        ObjectNode frame = objectMapper.createObjectNode();
        frame.put("jsonrpc", "2.0");
        frame.put("id", id);
        frame.put("method", method);
        frame.set("params", params);
        try {
            sandbox.send(frame);
        } catch (InteractiveSandboxException e) {
            pending.remove(id);
            timeoutTask.cancel(false);
            future.completeExceptionally(e);
        }
        return future;
    }

    private void onFrame(JsonNode frame) {
        if (!frame.isObject()) {
            log.debug("Discarding non-object runner frame: {}", frame);
            return;
        }
        if (frame.has("method")) {
            String method = frame.get("method").asText();
            if ("event".equals(method)) {
                handleEvent(frame);
            } else if ("fetch_context".equals(method)) {
                handleFetchContext(frame);
            } else {
                log.debug("Unknown runner-side method '{}' — ignoring", method);
            }
            return;
        }
        if (frame.has("id")) {
            handleResponse(frame);
        } else {
            log.debug("Runner frame has neither method nor id; ignoring: {}", frame);
        }
    }

    private void handleResponse(JsonNode frame) {
        long id = frame.get("id").asLong();
        PendingCall call = pending.remove(id);
        if (call == null) {
            return;
        }
        call.timeoutTask.cancel(false);
        if (frame.has("error") && !frame.get("error").isNull()) {
            JsonNode error = frame.get("error");
            int code = error.has("code") ? error.get("code").asInt() : -32000;
            String message = error.has("message") ? error.get("message").asText() : "runner error";
            call.future.completeExceptionally(new MentorRunnerException(code, message, error));
        } else {
            JsonNode result = frame.has("result") ? frame.get("result") : objectMapper.nullNode();
            call.future.complete(result);
        }
    }

    private void handleEvent(JsonNode frame) {
        JsonNode params = frame.get("params");
        if (params == null || !params.has("event")) {
            log.debug("Runner event frame missing params.event — ignoring");
            return;
        }
        // Per-thread fan-out: the sandbox is shared by (userId, workspaceId), so a second
        // chat tab in the same workspace subscribes to the same frame stream. Drop any frame
        // whose threadId doesn't match the one this client is bound to — without the filter,
        // tab-A's translator sees tab-B's text deltas and ships them down tab-A's wire.
        // Notification-type frames (`runner_ready`) ship with `threadId: null` and pass
        // through here for ALL clients; the translator drops them by event-type.
        if (boundThreadId != null && params.has("threadId") && !params.get("threadId").isNull()) {
            String frameThreadId = params.get("threadId").asText();
            if (!boundThreadId.toString().equals(frameThreadId)) {
                return;
            }
        }
        try {
            onEvent.accept(params.get("event"));
        } catch (RuntimeException e) {
            log.warn("Runner event handler threw: {}", e.getMessage(), e);
        }
    }

    private void handleFetchContext(JsonNode frame) {
        // Runner-originated callbacks carry a string id (`fc-<uuid>`); Java-originated calls use
        // numeric ids from our own AtomicLong. We MUST echo the runner's id back unchanged — the
        // runner indexes `pendingFetchContexts` by string key, so any coercion (asLong → 0) silently
        // breaks correlation and stalls the LLM tool call until the 10s timeout fires.
        JsonNode idNode = frame.get("id");
        JsonNode params = frame.get("params");
        if (params == null) {
            sendCallbackError(idNode, -32600, "missing params");
            return;
        }
        String threadIdStr = params.has("threadId") ? params.get("threadId").asText() : null;
        String path = params.has("path") ? params.get("path").asText() : null;
        if (threadIdStr == null || path == null) {
            sendCallbackError(idNode, -32600, "missing threadId or path");
            return;
        }
        UUID threadId;
        try {
            threadId = UUID.fromString(threadIdStr);
        } catch (IllegalArgumentException ex) {
            sendCallbackError(idNode, -32600, "invalid threadId");
            return;
        }
        try {
            JsonNode result = fetchContextHandler.apply(new FetchContextRequest(threadId, path));
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", idNode != null ? idNode : objectMapper.nullNode());
            ObjectNode resultEnvelope = response.putObject("result");
            resultEnvelope.set("content", result != null ? result : objectMapper.nullNode());
            sandbox.send(response);
        } catch (RuntimeException e) {
            log.warn("fetch_context callback failed: {}", e.getMessage(), e);
            sendCallbackError(idNode, -32000, e.getMessage() != null ? e.getMessage() : "fetch_context failed");
        }
    }

    private void sendCallbackError(@Nullable JsonNode id, int code, String message) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id != null ? id : objectMapper.nullNode());
            ObjectNode error = response.putObject("error");
            error.put("code", code);
            error.put("message", message);
            sandbox.send(response);
        } catch (InteractiveSandboxException e) {
            log.warn("Failed to send fetch_context error response: {}", e.getMessage(), e);
        }
    }

    /** Server-callback request from the runner. */
    public record FetchContextRequest(UUID threadId, String path) {}

    private record PendingCall(CompletableFuture<JsonNode> future, ScheduledFuture<?> timeoutTask, String method) {}
}
