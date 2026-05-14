package de.tum.in.www1.hephaestus.agent.mentor.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.mentor.MentorReplayMessage;
import de.tum.in.www1.hephaestus.agent.mentor.chat.exception.TurnAlreadyInFlightException;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.TranslatorState;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.in.www1.hephaestus.agent.pricing.ModelPricingService;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.mentor.ChatMessage;
import de.tum.in.www1.hephaestus.mentor.ChatMessageRepository;
import de.tum.in.www1.hephaestus.mentor.ChatThread;
import de.tum.in.www1.hephaestus.mentor.ChatThreadRepository;
import de.tum.in.www1.hephaestus.mentor.ChatThreadService;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    private final ChatThreadService chatThreadService;
    private final WorkspaceRepository workspaceRepository;
    private final ModelPricingService pricingService;

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
        return s.length() > 80 ? s.substring(0, 77) + "…" : s;
    }

    /**
     * Persist the user message + assistant placeholder in a single transaction. The DB unique
     * partial index on {@code (thread_id) WHERE status='in_flight'} converts a racy second
     * insert from a non-affinity replica into a {@link DataIntegrityViolationException}, which
     * we surface as {@link TurnAlreadyInFlightException}.
     *
     * <p>{@code userMessageId} is the client-supplied UUID (from the AI SDK UIMessage envelope).
     * Pass {@code null} to fall back to {@code UUID.randomUUID()}. Persisting the client id is
     * required for the webapp's optimistic UI: vote, regenerate, and reconciliation on refresh
     * all key by the message id the client already minted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TurnPersistenceCookie persistInFlight(
        ChatThread thread,
        String userText,
        UUID assistantMessageId,
        @Nullable UUID userMessageId
    ) {
        try {
            ChatMessage userMessage = new ChatMessage();
            userMessage.setId(userMessageId != null ? userMessageId : UUID.randomUUID());
            userMessage.setThread(thread);
            userMessage.setRole(ChatMessage.Role.USER);
            userMessage.setParts(toTextParts(userText));
            ChatMessage savedUser = chatMessageRepository.save(userMessage);

            ChatMessage assistant = new ChatMessage();
            assistant.setId(assistantMessageId);
            assistant.setThread(thread);
            assistant.setRole(ChatMessage.Role.ASSISTANT);
            assistant.setParentMessage(savedUser);
            assistant.setParts(NODES.arrayNode());
            ObjectNode meta = NODES.objectNode();
            meta.put("status", "in_flight");
            assistant.setMetadata(meta);
            chatMessageRepository.save(assistant);
            chatMessageRepository.flush();
            return new TurnPersistenceCookie(thread.getId(), savedUser.getId(), assistantMessageId, Instant.now());
        } catch (DataIntegrityViolationException ex) {
            // Spring translates ANY DB integrity violation (FK, NOT NULL, CHECK) to this
            // class — only narrow to TurnAlreadyInFlightException when Hibernate confirms
            // the underlying constraint is our partial-unique in-flight index. A future
            // CHECK regression on `parts` / `metadata` would otherwise masquerade as a 409.
            if (isInFlightUniqueViolation(ex)) {
                throw new TurnAlreadyInFlightException(thread.getId(), ex);
            }
            throw ex;
        }
    }

    /** {@code ux_chat_message_in_flight} is the Liquibase-managed partial unique on assistant rows. */
    private static boolean isInFlightUniqueViolation(DataIntegrityViolationException ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                return name != null && name.equalsIgnoreCase("ux_chat_message_in_flight");
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
        UIMessageChunk.FinishMetadata existing = finish.messageMetadata();
        UIMessageChunk.FinishMetadata.Usage usage = existing != null ? existing.usage() : null;
        String model = existing != null ? existing.model() : state.observedModel();
        return new UIMessageChunk.Finish(finish.finishReason(), new UIMessageChunk.FinishMetadata(model, usage, cost));
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
            // `interrupted`) bumped the @Version after we loaded the snapshot. Their verdict
            // is the source of truth — we leave the row alone. The wire's Finish chunk was
            // already sent by the orchestrator; the client just sees a row whose persisted
            // status diverges from the streamed terminal state, which the webapp's refresh
            // reconciles.
            log.info(
                "finalise lost optimistic-lock race for assistantMessageId={} — leaving prior verdict in place",
                cookie.assistantMessageId()
            );
        }
    }

    private void doFinalise(TurnPersistenceCookie cookie, TranslatorState state, UIMessageChunk.Finish finish) {
        ChatMessage assistant = chatMessageRepository
            .findById(cookie.assistantMessageId())
            .orElseThrow(() -> new EntityNotFoundException("ChatMessage", cookie.assistantMessageId().toString()));
        assistant.setParts(state.partsSnapshot());
        ObjectNode meta = newOrCopyMeta(assistant);
        meta.put("status", "completed");
        if (finish.finishReason() != null) {
            meta.put("finishReason", finish.finishReason().wire());
        }
        // Usage + model come from TranslatorState — the translator accumulates from
        // message_update.partial / message_end / agent_end.messages[].
        UsageBreakdown usage = extractUsageFromState(state);
        meta.put("inputTokens", usage.inputTokens());
        meta.put("outputTokens", usage.outputTokens());
        meta.put("cacheReadTokens", usage.cacheReadTokens());
        meta.put("cacheWriteTokens", usage.cacheWriteTokens());
        if (usage.model() != null) {
            meta.put("model", usage.model());
        }
        // Cost: prefer Pi's own `usage.cost.total` (computed against the provider's price table on
        // the agent host); fall back to ModelPricingService if Pi didn't ship one. If both are
        // absent the field is left null — downstream UI tolerates absent cost. Recorded even when
        // tokens are zero (e.g. a steered abort) so we don't drop a real model name on the floor.
        Double piCostUsd = extractPiCostUsd(state.observedUsage());
        if (piCostUsd != null) {
            meta.put("costUsd", piCostUsd);
        } else if (usage.model() != null && (usage.inputTokens() > 0 || usage.outputTokens() > 0)) {
            pricingService
                .computeCost(
                    usage.model(),
                    usage.inputTokens(),
                    usage.outputTokens(),
                    usage.cacheReadTokens(),
                    usage.cacheWriteTokens()
                )
                .ifPresent(cost -> meta.put("costUsd", cost.doubleValue()));
        }
        meta.put("durationMs", Duration.between(cookie.startedAt(), Instant.now()).toMillis());
        assistant.setMetadata(meta);
        chatMessageRepository.save(assistant);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void interrupt(TurnPersistenceCookie cookie, TranslatorState state, Throwable cause) {
        try {
            doInterrupt(cookie, state, cause);
        } catch (OptimisticLockingFailureException stale) {
            // Another writer (a successful finalise or reaper sweep) bumped the row's version
            // after our snapshot. Their verdict wins; we don't downgrade a `completed` row to
            // `interrupted` or stomp the reaper's `interrupted`-with-context.
            log.info(
                "interrupt lost optimistic-lock race for assistantMessageId={} — leaving prior verdict in place",
                cookie.assistantMessageId()
            );
        }
    }

    private void doInterrupt(TurnPersistenceCookie cookie, TranslatorState state, Throwable cause) {
        chatMessageRepository
            .findById(cookie.assistantMessageId())
            .ifPresent(assistant -> {
                assistant.setParts(state.partsSnapshot());
                ObjectNode meta = newOrCopyMeta(assistant);
                meta.put("status", "interrupted");
                meta.put("error", cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
                meta.put("durationMs", Duration.between(cookie.startedAt(), Instant.now()).toMillis());
                assistant.setMetadata(meta);
                chatMessageRepository.save(assistant);
            });
    }

    /**
     * Build the runner replay window from the most recent messages on this thread.
     *
     * <p>Wrapped in a read-only transaction so {@code effectiveParts} can lazily materialise the
     * legacy {@code chat_message_part} collection (during the #1074 dual-write window) without
     * triggering {@code LazyInitializationException}: the per-message lazy access happens inside
     * the still-open Hibernate session.
     */
    @Transactional(readOnly = true)
    public List<MentorReplayMessage> buildReplay(UUID threadId) {
        List<ChatMessage> recent = chatThreadService.recentMessagesForReplay(threadId);
        List<MentorReplayMessage> out = new ArrayList<>(recent.size());
        for (ChatMessage msg : recent) {
            JsonNode parts = chatThreadService.effectiveParts(msg);
            out.add(new MentorReplayMessage(msg.getRole().getValue(), parts, msg.getCreatedAt()));
        }
        return out;
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

    /**
     * Pi's {@code Usage.cost: {input, output, cacheRead, cacheWrite, total}} is computed on the
     * agent host (provider price table baked in). We prefer this over our own pricing lookup so
     * a model we don't have a price row for still gets a real cost.
     */
    @Nullable
    private static Double extractPiCostUsd(@Nullable JsonNode usage) {
        if (usage == null || !usage.isObject()) return null;
        JsonNode cost = usage.path("cost");
        if (!cost.isObject()) return null;
        JsonNode total = cost.path("total");
        if (total.isNumber()) {
            double v = total.asDouble();
            return Double.isFinite(v) ? v : null;
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
