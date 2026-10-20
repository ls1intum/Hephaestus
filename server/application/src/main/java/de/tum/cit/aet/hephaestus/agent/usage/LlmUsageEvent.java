package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.integration.core.spi.UsagePricingState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Records the LLM usage of review jobs and mentor turns for spending totals and budget caps.
 * Source identity, model, funding source, rates and date stay frozen; an operator can price an unpriced row. A
 * review job attempt's rows project its measurement: a call that returns after the attempt ended replaces their
 * measurement ({@link LlmUsageRecorder#project}). A mentor turn's row is never replaced.
 *
 * <p>{@code sourceId} is a soft reference (no FK) to the originating {@code review_job.id} or
 * {@code chat_message.id}: accounting must survive source-row deletion. Source kind, id, and
 * attempt form the idempotency key, so retries are billed once each without collapsing distinct
 * provider calls.
 */
@Entity
@Table(
        name = "llm_usage_event",
        indexes = {
            @Index(name = "idx_llm_usage_ws_time", columnList = "workspace_id, occurred_at"),
            // The retention sweep filters on age alone, which the composite above cannot serve: its
            // leading column is absent from that predicate.
            @Index(name = "idx_llm_usage_occurred_at", columnList = "occurred_at")
        },
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "ux_llm_usage_event_source_attempt",
                    columnNames = {"source_type", "source_id", "source_attempt"}),
            @UniqueConstraint(
                    name = "uk_llm_usage_event_workspace",
                    columnNames = {"id", "workspace_id"})
        })
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class LlmUsageEvent {

    @Id
    @EqualsAndHashCode.Include
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @NonNull
    @Column(name = "job_type", nullable = false, length = 40)
    private String jobType;

    @NonNull
    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32, updatable = false)
    private UsageSourceType sourceType;

    @ColumnDefault("0")
    @Column(name = "source_attempt", nullable = false, updatable = false)
    private int sourceAttempt;

    @Nullable
    @Column(name = "review_id", updatable = false)
    private UUID reviewId;

    @Nullable
    @Column(name = "model", length = 128)
    private String model;

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

    @ColumnDefault("1")
    @Column(name = "total_calls", nullable = false)
    private int totalCalls = 1;

    // Unknown prices remain null and are excluded from sums; they are not zero-cost usage.
    @Nullable
    @Column(name = "cost_usd", precision = 18, scale = 6)
    private BigDecimal costUsd;

    @NonNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @NonNull
    @ColumnDefault("'UNPRICED'")
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_state", nullable = false, length = 16)
    private UsagePricingState pricingState = UsagePricingState.UNPRICED;

    @ColumnDefault("0")
    @Column(name = "reasoning_tokens", nullable = false)
    private long reasoningTokens;

    @NonNull
    @ColumnDefault("'INSTANCE'")
    @Enumerated(EnumType.STRING)
    @Column(name = "funding_source", nullable = false, length = 16)
    private UsageFundingSource fundingSource = UsageFundingSource.INSTANCE;

    @Nullable
    @Column(name = "applied_price_id")
    private Long appliedPriceId;

    @Nullable
    @Column(name = "applied_workspace_model_id")
    private Long appliedWorkspaceModelId;

    // Freeze applied rates so edits or deletion of price records cannot change historical charges.
    @Nullable
    @Column(name = "applied_per_1m_input_usd", precision = 18, scale = 8)
    private BigDecimal appliedPer1mInputUsd;

    @Nullable
    @Column(name = "applied_per_1m_output_usd", precision = 18, scale = 8)
    private BigDecimal appliedPer1mOutputUsd;

    @Nullable
    @Column(name = "applied_per_1m_cache_read_usd", precision = 18, scale = 8)
    private BigDecimal appliedPer1mCacheReadUsd;

    @Nullable
    @Column(name = "applied_per_1m_cache_write_usd", precision = 18, scale = 8)
    private BigDecimal appliedPer1mCacheWriteUsd;

    // Null provenance records no source-selection decision; it must not imply verified measurement.
    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "usage_provenance", length = 16)
    private UsageProvenance usageProvenance;

    // Rerank search units remain unpriced without a rate, unless the model declares no metered cost.
    @ColumnDefault("0")
    @Column(name = "search_units", nullable = false)
    private long searchUnits;

    @ColumnDefault("0")
    @Column(name = "unreported_calls", nullable = false)
    private int unreportedCalls;

    @NonNull
    @ColumnDefault("'UNKNOWN'")
    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_verification", nullable = false, length = 16)
    private MeasurementVerification measurementVerification = MeasurementVerification.UNKNOWN;
}
