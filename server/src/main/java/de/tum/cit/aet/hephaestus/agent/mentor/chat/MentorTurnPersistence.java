package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import de.tum.cit.aet.hephaestus.agent.mentor.chat.exception.TurnAlreadyInFlightException;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.TranslatorState;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.cit.aet.hephaestus.agent.pricing.ModelPricingService;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.mentor.ChatMessage;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Per-turn persistence helper for mentor chat. Uses {@code REQUIRES_NEW} so a turn-internal
 * runtime exception cannot roll back the user/assistant rows.
 */
@Service
@RequiredArgsConstructor
public class MentorTurnPersistence {

    private static final Logger log = LoggerFactory.getLogger(MentorTurnPersistence.class);
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final ChatThreadRepository chatThreadRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ModelPricingService pricingService;
    private final de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationalDeliveryReconciler conversationalDeliveryReconciler;

    /**
     * Find the thread for {@code (workspaceId, threadId)} owned by {@code user}, creating a
     * new row if no thread exists yet. Foreign-owner reads are hidden as 404.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatThread ensureThread(long workspaceId, UUID threadId, User user, String firstPrompt) {
        return chatThreadRepository
            .findByIdAndWorkspaceId(threadId, workspaceId)
            .map(existing -> {
                if (existing.getUser() == null || !existing.getUser().getId().equals(user.getId())) {
                    throw new EntityNotFoundException("ChatThread", threadId.toString());
                }
                return existing;
            })
            .orElseGet(() -> createThread(workspaceId, threadId, user, firstPrompt));
    }

    private ChatThread createThread(long workspaceId, UUID threadId, User user, String firstPrompt) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", String.valueOf(workspaceId)));
        ChatThread thread = new ChatThread();
        thread.setId(threadId);
        thread.setUser(user);
        thread.setWorkspace(workspace);
        thread.setTitle(truncateTitle(firstPrompt));
        return chatThreadRepository.save(thread);
    }

    private static String truncateTitle(String prompt) {
        if (prompt == null) return null;
        String s = prompt.strip().replaceAll("\\s+", " ");
        if (s.length() <= 80) return s;
        // Cut on a code-point boundary so a 77th-char surrogate pair (e.g. an emoji) is not split into a lone
        // surrogate before the appended ellipsis.
        int cut = s.offsetByCodePoints(0, Math.min(77, s.codePointCount(0, s.length())));
        return s.substring(0, cut) + "…";
    }

    /**
     * Persist the user message + assistant placeholder in a single transaction. The DB unique
     * partial index on {@code (thread_id) WHERE status='in_flight'} converts a racy second
     * insert from a non-affinity replica into a {@link DataIntegrityViolationException}, which
     * we surface as {@link TurnAlreadyInFlightException}.
     *
     * <p>{@code userMessageId} is the client-supplied UUID (from the AI SDK UIMessage envelope).
     * Pass {@code null} to fall back to {@code UUID.randomUUID()}. Persisting the client id is
     * required for the webapp's optimistic UI and Slack event idempotency: duplicate inbound
     * deliveries reuse the same user id and collapse to the existing in-flight/finished turn.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TurnPersistenceCookie persistInFlight(
        ChatThread thread,
        String userText,
        UUID assistantMessageId,
        @Nullable UUID userMessageId
    ) {
        try {
            if (userMessageId != null && chatMessageRepository.existsById(userMessageId)) {
                throw new TurnAlreadyInFlightException(
                    thread.getId(),
                    new DataIntegrityViolationException("duplicate client user message id")
                );
            }
            ChatMessage userMessage = new ChatMessage();
            userMessage.setId(userMessageId != null ? userMessageId : UUID.randomUUID());
            userMessage.setThread(thread);
            userMessage.setRole(ChatMessage.Role.USER);
            // User messages are immutable once written; the column carries the same shape
            // (NOT NULL VARCHAR) so we set it explicitly to keep JPA + DB in sync.
            userMessage.setStatus(ChatMessage.Status.completed);
            userMessage.setParts(toTextParts(userText));
            ChatMessage savedUser = chatMessageRepository.save(userMessage);

            ChatMessage assistant = new ChatMessage();
            assistant.setId(assistantMessageId);
            assistant.setThread(thread);
            assistant.setRole(ChatMessage.Role.ASSISTANT);
            assistant.setParentMessage(savedUser);
            assistant.setParts(NODES.arrayNode());
            // The partial unique index `ux_chat_message_in_flight_v2` keys on the status column,
            // so a second concurrent turn for the same thread raises TurnAlreadyInFlightException.
            assistant.setStatus(ChatMessage.Status.in_flight);
            assistant.setMetadata(NODES.objectNode());
            chatMessageRepository.save(assistant);
            chatMessageRepository.flush();
            return new TurnPersistenceCookie(thread.getId(), savedUser.getId(), assistantMessageId, Instant.now());
        } catch (DataIntegrityViolationException ex) {
            // Spring translates ANY DB integrity violation (FK, NOT NULL, CHECK) to this
            // class — only narrow to TurnAlreadyInFlightException when Hibernate confirms
            // the underlying constraint is our partial-unique in-flight index or the
            // duplicate client-supplied user-message id used for transport idempotency.
            // A future CHECK regression on `parts` / `metadata` would otherwise
            // masquerade as a 409.
            if (isInFlightUniqueViolation(ex) || (userMessageId != null && isDuplicateMessageIdViolation(ex))) {
                throw new TurnAlreadyInFlightException(thread.getId(), ex);
            }
            throw ex;
        }
    }

    /**
     * Match the partial-unique in-flight index by name so a concurrent-turn 409 distinguishes
     * the expected race from other integrity violations.
     */
    private static boolean isInFlightUniqueViolation(DataIntegrityViolationException ex) {
        return hasConstraintName(ex, "ux_chat_message_in_flight_v2");
    }

    private static boolean isDuplicateMessageIdViolation(DataIntegrityViolationException ex) {
        return hasConstraintName(ex, "chat_message_pkey");
    }

    private static boolean hasConstraintName(DataIntegrityViolationException ex, String expectedName) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                return name != null && name.equalsIgnoreCase(expectedName);
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * Compute the {@code costUsd} that {@link #finalise} will write to {@code chat_message.metadata}
     * and return a copy of {@code finish} with that value injected into its {@code messageMetadata}.
     * Called by the orchestrator *before* the Finish chunk is sent on the wire so the client sees
     * the same cost that the DB persists. Returns {@code finish} unchanged when no cost is
     * computable.
     */
    public UIMessageChunk.Finish augmentFinishWithCost(UIMessageChunk.Finish finish, TranslatorState state) {
        Double cost = computeFinalCostUsd(state);
        if (cost == null) return finish;
        UIMessageChunk.MessageMetadata existing = finish.messageMetadata();
        UIMessageChunk.MessageMetadata.Usage usage = existing != null ? existing.usage() : null;
        String model = existing != null ? existing.model() : state.observedModel();
        return new UIMessageChunk.Finish(finish.finishReason(), new UIMessageChunk.MessageMetadata(model, usage, cost));
    }

    @Nullable
    private Double computeFinalCostUsd(TranslatorState state) {
        Double piCost = extractPiCostUsd(state.observedUsage());
        if (piCost != null) return piCost;
        UsageBreakdown breakdown = extractUsageFromState(state);
        if (breakdown.model() == null || (breakdown.inputTokens() <= 0 && breakdown.outputTokens() <= 0)) {
            return null;
        }
        return pricingService
            .computeCost(
                breakdown.model(),
                breakdown.inputTokens(),
                breakdown.outputTokens(),
                breakdown.cacheReadTokens(),
                breakdown.cacheWriteTokens()
            )
            .map(java.math.BigDecimal::doubleValue)
            .orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalise(TurnPersistenceCookie cookie, TranslatorState state, UIMessageChunk.Finish finish) {
        try {
            doFinalise(cookie, state, finish);
        } catch (OptimisticLockingFailureException stale) {
            // A concurrent writer (typically the in-flight reaper flipping the row to
            // `interrupted`) bumped the @Version after we loaded the snapshot. Their observation
            // is the source of truth — we leave the row alone. The wire's Finish chunk was
            // already sent by the orchestrator; the client just sees a row whose persisted
            // status diverges from the streamed terminal state, which the webapp's refresh
            // reconciles.
            log.info(
                "finalise lost optimistic-lock race for assistantMessageId={} — leaving prior observation in place",
                cookie.assistantMessageId()
            );
        }
    }

    private void doFinalise(TurnPersistenceCookie cookie, TranslatorState state, UIMessageChunk.Finish finish) {
        ChatMessage assistant = chatMessageRepository
            .findById(cookie.assistantMessageId())
            .orElseThrow(() -> new EntityNotFoundException("ChatMessage", cookie.assistantMessageId().toString()));
        assistant.setParts(state.partsSnapshot());
        assistant.setStatus(ChatMessage.Status.completed);
        ObjectNode meta = newOrCopyMeta(assistant);
        if (finish.finishReason() != null) {
            meta.put("finishReason", finish.finishReason().wire());
        }
        // Persisted shape MUST match the wire {@code UIMessageChunk.MessageMetadata} — webapp's
        // `MessageMetadata` is {model, usage:{input,output,cacheRead,cacheWrite,totalTokens}, costUsd}
        // and `useChat` rehydrates a thread by passing the GET response straight into the same
        // typed accessor. Flat keys would render usage/cost as undefined on every refresh.
        UsageBreakdown usage = extractUsageFromState(state);
        if (usage.model() != null) {
            meta.put("model", usage.model());
        }
        ObjectNode usageNode =
            meta.has("usage") && meta.get("usage").isObject()
                ? (ObjectNode) meta.get("usage")
                : meta.putObject("usage");
        usageNode.put("input", usage.inputTokens());
        usageNode.put("output", usage.outputTokens());
        usageNode.put("cacheRead", usage.cacheReadTokens());
        usageNode.put("cacheWrite", usage.cacheWriteTokens());
        // totalTokens: honour the provider-reported value carried on the wire Finish when present (it may
        // include cache tokens and so legitimately differ from input+output) — single source of truth,
        // mirroring how costUsd is reused below. Only when the wire carries no total do we derive
        // input+output as a fallback.
        Long wireTotalTokens = wireTotalTokens(finish);
        long totalTokens = wireTotalTokens != null ? wireTotalTokens : usage.inputTokens() + usage.outputTokens();
        if (totalTokens > 0) {
            usageNode.put("totalTokens", totalTokens);
        }
        // Cost: reuse the value already computed for and carried on the wire Finish (augmentFinishWithCost)
        // so the DB persists exactly what the client saw — single source of truth, no second derivation that
        // could drift. Only when the Finish carries no cost (cost was uncomputable) do we leave it null.
        Double wireCostUsd = finish.messageMetadata() != null ? finish.messageMetadata().costUsd() : null;
        if (wireCostUsd != null) {
            meta.put("costUsd", wireCostUsd);
        }
        meta.put("durationMs", Duration.between(cookie.startedAt(), Instant.now()).toMillis());
        assistant.setMetadata(meta);
        // saveAndFlush (not save): force the optimistic-lock check NOW, inside the try/catch, instead of at the
        // REQUIRES_NEW commit boundary where an OptimisticLockingFailureException would escape uncaught and turn
        // a benign reaper race into a logged turn failure.
        chatMessageRepository.saveAndFlush(assistant);

        // close the conversational-delivery loop for any PREPARED unit the mentor raised this turn. MUST run
        // AFTER the assistant saveAndFlush above - the CONVERSATION_TURN placement's chat_message_id FK
        // (ON DELETE SET NULL) references this just-flushed row. Runs in THIS (finalise) transaction. Best-effort.
        reconcileConversationalDelivery(assistant, state);

        byte[] sessionBytes = state.observedSessionJsonl();
        if (sessionBytes != null) {
            chatThreadRepository.updateSessionJsonl(cookie.threadId(), sessionBytes);
        }
    }

    /**
     * Reconcile the mentor's linked findings for this turn against the PREPARED conversational queue.
     * Derives the recipient + workspace from the assistant message's thread. Best-effort - a failure is logged
     * and swallowed so it can never fail the turn persistence the finalise transaction just did.
     */
    private void reconcileConversationalDelivery(ChatMessage assistant, TranslatorState state) {
        try {
            java.util.List<UUID> linkedFindingIds = state.linkedFindingIds();
            if (linkedFindingIds.isEmpty()) {
                return;
            }
            ChatThread thread = assistant.getThread();
            if (thread == null || thread.getWorkspace() == null || thread.getUser() == null) {
                return;
            }
            conversationalDeliveryReconciler.reconcile(
                thread.getWorkspace().getId(),
                thread.getUser().getId(),
                assistant.getId(),
                linkedFindingIds
            );
        } catch (RuntimeException e) {
            log.warn(
                "Conversational delivery reconciliation failed (turn persistence unaffected): assistantMessageId={}, error={}",
                assistant.getId(),
                e.toString()
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void interrupt(TurnPersistenceCookie cookie, TranslatorState state, Throwable cause) {
        try {
            doInterrupt(cookie, state, cause);
        } catch (OptimisticLockingFailureException stale) {
            // Another writer (a successful finalise or reaper sweep) bumped the row's version
            // after our snapshot. Their observation wins; we don't downgrade a `completed` row to
            // `interrupted` or stomp the reaper's `interrupted`-with-context.
            log.info(
                "interrupt lost optimistic-lock race for assistantMessageId={} — leaving prior observation in place",
                cookie.assistantMessageId()
            );
        }
    }

    private void doInterrupt(TurnPersistenceCookie cookie, TranslatorState state, Throwable cause) {
        chatMessageRepository
            .findById(cookie.assistantMessageId())
            .ifPresent(assistant -> {
                assistant.setParts(state.partsSnapshot());
                assistant.setStatus(ChatMessage.Status.interrupted);
                ObjectNode meta = newOrCopyMeta(assistant);
                meta.put("error", cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
                meta.put("durationMs", Duration.between(cookie.startedAt(), Instant.now()).toMillis());
                assistant.setMetadata(meta);
                // saveAndFlush (not save): surface OptimisticLockingFailureException inside the try/catch (the
                // no-session-bytes interrupt path would otherwise defer the version check to commit, escaping it).
                chatMessageRepository.saveAndFlush(assistant);
            });

        // If the runner shipped session_persisted before the interrupt (e.g. pi_error AFTER a
        // valid agent_end-adjacent flush), preserve those bytes so the next cold restart still
        // gets prompt-cache continuity. Same null-skip semantics as doFinalise.
        byte[] sessionBytes = state.observedSessionJsonl();
        if (sessionBytes != null) {
            chatThreadRepository.updateSessionJsonl(cookie.threadId(), sessionBytes);
        }
    }

    /** The provider-reported {@code totalTokens} carried on the wire Finish, or {@code null} when absent. */
    @Nullable
    private static Long wireTotalTokens(UIMessageChunk.Finish finish) {
        UIMessageChunk.MessageMetadata meta = finish.messageMetadata();
        UIMessageChunk.MessageMetadata.Usage usage = meta != null ? meta.usage() : null;
        Integer total = usage != null ? usage.totalTokens() : null;
        return total != null ? total.longValue() : null;
    }

    private static ObjectNode newOrCopyMeta(ChatMessage message) {
        JsonNode existing = message.getMetadata();
        if (existing != null && existing.isObject()) {
            return ((ObjectNode) existing).deepCopy();
        }
        return NODES.objectNode();
    }

    private static JsonNode toTextParts(String userText) {
        ObjectNode part = NODES.objectNode();
        part.put("type", "text");
        part.put("text", userText);
        return NODES.arrayNode().add(part);
    }

    /**
     * Pull tokens + model from the translator's accumulated usage snapshot. Pi's
     * {@code AssistantMessage.usage} is canonical camelCase per pi-ai types.ts (Usage).
     */
    private static UsageBreakdown extractUsageFromState(TranslatorState state) {
        String model = state.observedModel();
        JsonNode usage = state.observedUsage();
        if (usage == null) return new UsageBreakdown(model, 0, 0, 0, 0);
        return new UsageBreakdown(
            model,
            readLong(usage, "input"),
            readLong(usage, "output"),
            readLong(usage, "cacheRead"),
            readLong(usage, "cacheWrite")
        );
    }

    private static long readLong(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isIntegralNumber() || v.isFloatingPointNumber() ? v.asLong() : 0L;
    }

    /** ≈10× a worst-case frontier-model turn; guards bad Pi cost values from poisoning the histogram + audit row. */
    private static final double COST_USD_SANITY_CAP = 100.0d;

    /**
     * Pi's {@code Usage.cost.total} is computed on the agent host with the provider price table
     * baked in — preferred over our own pricing lookup so unknown-to-us models still get a real cost.
     */
    @Nullable
    private static Double extractPiCostUsd(@Nullable JsonNode usage) {
        if (usage == null || !usage.isObject()) return null;
        JsonNode cost = usage.path("cost");
        if (!cost.isObject()) return null;
        JsonNode total = cost.path("total");
        if (total.isNumber()) {
            double v = total.asDouble();
            // Reject NaN/Infinity AND obviously-bogus values (negative, absurdly large) — the
            // value flows into both `chat_message.metadata.costUsd` (audit) and the
            // `mentor.turn.cost.usd` distribution summary (SLO panel). One Pi-side regression
            // returning −50 or 1e9 would skew the histogram for the lifetime of the registry.
            if (!Double.isFinite(v) || v < 0d || v > COST_USD_SANITY_CAP) return null;
            return v;
        }
        return null;
    }

    /** Tracking record carried through the turn pipeline. */
    public record TurnPersistenceCookie(
        UUID threadId,
        UUID userMessageId,
        UUID assistantMessageId,
        Instant startedAt
    ) {}

    private record UsageBreakdown(
        @Nullable String model,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens
    ) {}
}
