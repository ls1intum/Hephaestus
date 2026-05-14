package de.tum.in.www1.hephaestus.agent.mentor.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.mentor.MentorReplayMessage;
import de.tum.in.www1.hephaestus.agent.mentor.chat.exception.TurnAlreadyInFlightException;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.TranslatorState;
import de.tum.in.www1.hephaestus.agent.mentor.pricing.ModelPricingService;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.mentor.ChatMessage;
import de.tum.in.www1.hephaestus.mentor.ChatMessageRepository;
import de.tum.in.www1.hephaestus.mentor.ChatThread;
import de.tum.in.www1.hephaestus.mentor.ChatThreadRepository;
import de.tum.in.www1.hephaestus.mentor.ChatThreadService;
import de.tum.in.www1.hephaestus.mentor.chat.wire.UIMessageChunk;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
     * 3-arg overload: server mints the user id. Tests and back-compat call sites.
     */
    public TurnPersistenceCookie persistInFlight(ChatThread thread, String userText, UUID assistantMessageId) {
        return persistInFlight(thread, userText, assistantMessageId, null);
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
            throw new TurnAlreadyInFlightException(thread.getId(), ex);
        }
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
        return new UIMessageChunk.Finish(
            finish.finishReason(),
            new UIMessageChunk.FinishMetadata(model, usage, cost)
        );
    }

    @Nullable
    private Double computeFinalCostUsd(TranslatorState state) {
        Double piCost = extractPiCostUsd(state.observedUsage());
        if (piCost != null) return piCost;
        UsageBreakdown breakdown = extractUsageFromState(state, /* rawAgentEnd */ null);
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
    public void finalise(
        TurnPersistenceCookie cookie,
        TranslatorState state,
        UIMessageChunk.Finish finish,
        @Nullable JsonNode rawAgentEnd
    ) {
        ChatMessage assistant = chatMessageRepository
            .findById(cookie.assistantMessageId())
            .orElseThrow(() -> new EntityNotFoundException("ChatMessage", cookie.assistantMessageId().toString()));
        assistant.setParts(state.partsSnapshot());
        ObjectNode meta = newOrCopyMeta(assistant);
        meta.put("status", "completed");
        if (finish.finishReason() != null) {
            meta.put("finishReason", finish.finishReason());
        }
        // Usage + model come from TranslatorState. The translator accumulates from
        // message_update.partial / message_end / agent_end.messages[] — whichever lands. Pi's
        // `agent_end` event does NOT carry top-level usage or model fields (verified against
        // pi-coding-agent/dist/core/extensions/types.d.ts AgentEndEvent: {type, messages[]}).
        UsageBreakdown usage = extractUsageFromState(state, rawAgentEnd);
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
     * {@code AssistantMessage.usage} is canonically camelCase ({@code input}, {@code output},
     * {@code cacheRead}, {@code cacheWrite}) per pi-ai dist/types.d.ts:124-137. We also accept
     * snake-case keys so synthetic events from the runner's protocol-only stub still pass through.
     * {@code rawAgentEnd} is kept as a back-stop in case a future Pi minor version starts
     * inlining usage on agent_end — today it carries only {@code messages[]}.
     */
    private static UsageBreakdown extractUsageFromState(TranslatorState state, @Nullable JsonNode rawAgentEnd) {
        String model = state.observedModel();
        JsonNode usage = state.observedUsage();
        if (usage != null) {
            long input = readLong(usage, "input", "input_tokens", "inputTokens");
            long output = readLong(usage, "output", "output_tokens", "outputTokens");
            long cacheRead = readLong(usage, "cacheRead", "cache_read_tokens", "cacheReadTokens");
            long cacheWrite = readLong(usage, "cacheWrite", "cache_write_tokens", "cacheWriteTokens");
            return new UsageBreakdown(model, input, output, cacheRead, cacheWrite);
        }
        if (rawAgentEnd != null && rawAgentEnd.path("usage").isObject()) {
            JsonNode fallback = rawAgentEnd.path("usage");
            return new UsageBreakdown(
                model,
                readLong(fallback, "input", "input_tokens", "inputTokens"),
                readLong(fallback, "output", "output_tokens", "outputTokens"),
                readLong(fallback, "cacheRead", "cache_read_tokens", "cacheReadTokens"),
                readLong(fallback, "cacheWrite", "cache_write_tokens", "cacheWriteTokens")
            );
        }
        return new UsageBreakdown(model, 0, 0, 0, 0);
    }

    private static long readLong(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.path(f);
            if (v.isIntegralNumber() || v.isFloatingPointNumber()) {
                return v.asLong();
            }
        }
        return 0L;
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
