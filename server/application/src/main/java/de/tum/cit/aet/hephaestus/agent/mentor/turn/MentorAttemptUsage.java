package de.tum.cit.aet.hephaestus.agent.mentor.turn;

import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.UsagePricingState;
import de.tum.cit.aet.hephaestus.mentor.MentorTurnUsage;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;

/**
 * The one measurement of one Heph turn attempt: the model and rates its admission froze, every call the LLM proxy
 * observed for it, and the date its ledger row keeps. The {@code mentor_turn} counters are reset when the turn starts
 * its next attempt; this row is not, so a call that returns after its attempt ended still lands on the attempt that
 * authenticated it.
 *
 * <p>Admission creates it ({@link MentorAttemptUsageService#admit}) before the proxy can route a call of the attempt.
 * {@link MentorAttemptUsageService} is the only writer, and it takes the turn row before this one on every path. The
 * row holds ids, counts and rates only; it goes with its turn and is removed when an erasure selects the turn.
 */
@Entity
@Table(
        name = "mentor_turn_attempt_usage",
        // A child cannot name another workspace's turn: the tenant key is part of the reference.
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_mentor_turn_attempt_usage_tenant",
                    columnNames = {"turn_id", "attempt", "workspace_id"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MentorAttemptUsage {

    @EmbeddedId
    private Id id;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @Column(name = "model", nullable = false, length = 128)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_source", nullable = false, length = 16)
    private UsageFundingSource fundingSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_state", nullable = false, length = 16)
    private UsagePricingState pricingState;

    @Column(name = "applied_price_id")
    private @Nullable Long appliedPriceId;

    @Column(name = "applied_workspace_model_id")
    private @Nullable Long appliedWorkspaceModelId;

    @Column(name = "applied_per_1m_input_usd", precision = 18, scale = 8)
    private @Nullable BigDecimal appliedPer1mInputUsd;

    @Column(name = "applied_per_1m_output_usd", precision = 18, scale = 8)
    private @Nullable BigDecimal appliedPer1mOutputUsd;

    @Column(name = "applied_per_1m_cache_read_usd", precision = 18, scale = 8)
    private @Nullable BigDecimal appliedPer1mCacheReadUsd;

    @Column(name = "applied_per_1m_cache_write_usd", precision = 18, scale = 8)
    private @Nullable BigDecimal appliedPer1mCacheWriteUsd;

    /**
     * When the attempt's spend is dated in the ledger. The transaction that ends the attempt or takes it back sets it
     * ({@link MentorAttemptUsageRepository#dateOnce}), also when the attempt made no call; no returned call and no later
     * billing moves it. An erasure dates an attempt that still runs at the moment it forgets it.
     */
    @Column(name = "occurred_at")
    private @Nullable Instant occurredAt;

    /** Calls whose provider reported usage, including a reported zero. */
    @ColumnDefault("0")
    @Column(name = "calls", nullable = false)
    private int calls;

    /** Prompt tokens that were neither cache reads nor cache writes, like the turn's own counter. */
    @ColumnDefault("0")
    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @ColumnDefault("0")
    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @ColumnDefault("0")
    @Column(name = "cache_read_tokens", nullable = false)
    private long cacheReadTokens;

    @ColumnDefault("0")
    @Column(name = "cache_write_tokens", nullable = false)
    private long cacheWriteTokens;

    @ColumnDefault("0")
    @Column(name = "reasoning_tokens", nullable = false)
    private long reasoningTokens;

    /** The price the attempt was admitted with, as the ledger takes it. */
    public LlmPriceSnapshot price() {
        return new LlmPriceSnapshot(
                fundingSource,
                pricingState,
                appliedPriceId,
                appliedWorkspaceModelId,
                appliedPer1mInputUsd,
                appliedPer1mOutputUsd,
                appliedPer1mCacheReadUsd,
                appliedPer1mCacheWriteUsd);
    }

    /** Every call the proxy observed for the attempt, wherever the attempt was when the call returned. */
    public MentorTurnUsage usage() {
        return new MentorTurnUsage(
                inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens, reasoningTokens, calls);
    }

    void add(MentorTurnUsage call) {
        calls = calls > Integer.MAX_VALUE - call.calls() ? Integer.MAX_VALUE : calls + Math.max(0, call.calls());
        inputTokens = saturatedAdd(inputTokens, call.inputTokens());
        outputTokens = saturatedAdd(outputTokens, call.outputTokens());
        cacheReadTokens = saturatedAdd(cacheReadTokens, call.cacheReadTokens());
        cacheWriteTokens = saturatedAdd(cacheWriteTokens, call.cacheWriteTokens());
        reasoningTokens = saturatedAdd(reasoningTokens, call.reasoningTokens());
    }

    /** Dates an attempt that an erasure forgets while it still runs; a dated attempt keeps its date. */
    Instant occurredAt(Instant now) {
        if (occurredAt == null) occurredAt = now;
        return occurredAt;
    }

    /** Whether the attempt has the date of its end. Says nothing about whether its ledger row was written yet. */
    boolean isDated() {
        return occurredAt != null;
    }

    private static long saturatedAdd(long total, long tokens) {
        long added = Math.max(0, tokens);
        return total > Long.MAX_VALUE - added ? Long.MAX_VALUE : total + added;
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Column(name = "turn_id", nullable = false)
        private UUID turnId;

        @Column(name = "attempt", nullable = false)
        private int attempt;
    }
}
