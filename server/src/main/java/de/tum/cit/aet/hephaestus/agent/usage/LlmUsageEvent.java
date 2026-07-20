package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
 * One row per LLM-consuming unit of work — the unified, append-only spend ledger (#1368).
 *
 * <p>Every source of LLM cost writes here at the moment it persists its own result:
 * detection / replay jobs ({@code agent_job} terminal write) and mentor turns
 * ({@code chat_message} finalise/interrupt). The per-source records ({@code agent_job.llm_*}
 * columns, {@code chat_message.metadata}) remain for per-job diagnostics and the wire
 * contract; THIS table is the single accounting source for the per-workspace rollup and the
 * monthly budget cap. Never update rows — the ledger is append-only.
 *
 * <p>{@code sourceId} is a soft reference (no FK) to the originating {@code agent_job.id} or
 * {@code chat_message.id}: accounting must survive source-row deletion. It carries a unique
 * constraint so a source can never be double-billed. Workspace deletion cascades — a purged
 * tenant leaves no ledger residue.
 */
@Entity
@Table(
    name = "llm_usage_event",
    indexes = { @Index(name = "idx_llm_usage_ws_time", columnList = "workspace_id, occurred_at") }
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class LlmUsageEvent {

    /**
     * Assigned by {@link LlmUsageRecorder} at insert. NOT initialised here: a non-null id would
     * make Spring Data treat every new row as detached and route the insert through
     * {@code merge()}, costing a pointless SELECT on both hot paths.
     */
    @Id
    @EqualsAndHashCode.Include
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    @ToString.Exclude
    private Workspace workspace;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40)
    private LlmUsageJobType jobType;

    /** Soft unique ref to the originating agent_job.id / chat_message.id (see class doc). */
    @NonNull
    @Column(name = "source_id", nullable = false, unique = true, updatable = false)
    private UUID sourceId;

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

    /**
     * USD cost of this unit of work. Nullable: unknown model pricing yields token counts
     * without a cost — rollups treat null as zero (visibility over false precision).
     */
    @Nullable
    @Column(name = "cost_usd", precision = 12, scale = 6)
    private BigDecimal costUsd;

    /** When the work finished (job terminal write / turn finalise) — the rollup time axis. */
    @NonNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /**
     * Resolved pricing outcome (#1368). {@code UNPRICED} stops "unknown price" from being a silent
     * $0 — it makes the month's budget verdict unverifiable rather than under-counted.
     */
    @NonNull
    @ColumnDefault("'UNPRICED'")
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_state", nullable = false, length = 16)
    private PricingState pricingState = PricingState.UNPRICED;

    @ColumnDefault("0")
    @Column(name = "reasoning_tokens", nullable = false)
    private long reasoningTokens;

    /** Which cap this usage counts against — instance backstop vs. workspace BYO self-cap (#1368). */
    @NonNull
    @ColumnDefault("'INSTANCE'")
    @Enumerated(EnumType.STRING)
    @Column(name = "funding_source", nullable = false, length = 16)
    private FundingSource fundingSource = FundingSource.INSTANCE;

    /** Provenance: the {@code llm_model_price} row applied to this event, if any. Soft ref (no FK). */
    @Nullable
    @Column(name = "applied_price_id")
    private Long appliedPriceId;

    /**
     * Provenance: the {@code workspace_llm_model} row applied to this event, if any (BYO path only —
     * the instance path uses {@link #appliedPriceId} instead). Soft ref (no FK); {@code workspace_llm_model}
     * carries no price-history table, so there is no analogous "price row id" to reference (#1368 slice 6).
     */
    @Nullable
    @Column(name = "applied_workspace_model_id")
    private Long appliedWorkspaceModelId;

    /**
     * Frozen per-1M-token rates actually applied to this event (#1368 slice 6). {@code appliedPriceId} /
     * {@code appliedWorkspaceModelId} alone are live soft refs — if the referenced row is later
     * repriced or deleted, its CURRENT rates no longer describe what a HISTORICAL event was actually
     * charged. These five columns make every dollar falsifiable independent of the catalog's present
     * state. Set on every PRICED event (both instance and BYO paths); left null for FREE (rate is
     * moot — cost is definitionally $0) and UNPRICED (no rate was ever resolved).
     */
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

    @Nullable
    @Column(name = "applied_per_1m_reasoning_usd", precision = 18, scale = 8)
    private BigDecimal appliedPer1mReasoningUsd;
}
