package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.catalog.ModelKind;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.jspecify.annotations.Nullable;

/**
 * The model calls that one practice's script made with one kind of model in one attempt,
 * added up by the LLM proxy as each call returns. {@link AgentJobRepository#accumulatePracticeScriptUsage}
 * is the only writer. The attempt is part of the key, so a late call of an earlier attempt can never
 * add to a later one.
 *
 * <p>The {@code CHAT} rows only count toward the attempt's practice script token cap: those calls also go into
 * the job's own counters, and the review's ledger row bills them. The rows of each other kind add up to
 * one ledger row when the attempt ends ({@link AgentJobRepository#sumPracticeScriptUsageByKind}). The
 * practice in the key only splits that ledger row between practices in the usage report.
 */
@Entity
@Table(
        name = "agent_job_practice_script_usage",
        indexes = {
            // The primary key leads with the attempt, so it cannot serve the job cascade.
            @Index(name = "idx_agent_job_practice_script_usage_job", columnList = "job_id"),
            @Index(name = "idx_agent_job_practice_script_usage_workspace", columnList = "workspace_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentJobPracticeScriptUsage {

    @EmbeddedId
    private Id id;

    @MapsId("jobId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", foreignKey = @ForeignKey(name = "fk_agent_job_practice_script_usage_job"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AgentJob job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "workspace_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_agent_job_practice_script_usage_workspace"))
    private Workspace workspace;

    /**
     * The data handling tier of the model that served the calls, as the attempt froze it. Null when the attempt
     * froze no tier for that model.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "data_handling_tier", length = 24)
    private @Nullable DataHandlingTier dataHandlingTier;

    @Column(name = "calls", nullable = false)
    private int calls;

    @ColumnDefault("0")
    @Column(name = "reported_calls", nullable = false)
    private int reportedCalls;

    /** The prompt tokens without the cache reads and writes, as the review's own counters keep them. */
    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @ColumnDefault("0")
    @Column(name = "cache_read_tokens", nullable = false)
    private long cacheReadTokens;

    @ColumnDefault("0")
    @Column(name = "cache_write_tokens", nullable = false)
    private long cacheWriteTokens;

    /** What Cohere reranking calls billed instead of tokens. */
    @ColumnDefault("0")
    @Column(name = "search_units", nullable = false)
    private long searchUnits;

    public ModelKind modelKind() {
        return id.getModelKind();
    }

    /** The ledger source that bills calls of this kind; the review's own row bills the chat model. */
    public static LlmUsageSourceType ledgerSourceType(ModelKind kind) {
        return switch (kind) {
            case CHAT -> LlmUsageSourceType.AGENT_JOB;
            case DECISION -> LlmUsageSourceType.PRACTICE_SCRIPT_DECISION;
            case EMBEDDING -> LlmUsageSourceType.PRACTICE_SCRIPT_EMBEDDING;
            case RERANKING -> LlmUsageSourceType.PRACTICE_SCRIPT_RERANKING;
        };
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Column(name = "job_id", nullable = false)
        private UUID jobId;

        @Column(name = "attempt", nullable = false)
        private int attempt;

        @Enumerated(EnumType.STRING)
        @Column(name = "model_kind", nullable = false, length = 16)
        private ModelKind modelKind;

        /** The practice whose practice script made the calls, as the runner named it on each call. */
        @Column(name = "practice_slug", nullable = false, length = 64)
        private String practiceSlug;
    }
}
