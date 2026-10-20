package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * The one measurement of one review job attempt: the runner's own report as it wrote it, and every call the LLM
 * proxy observed for the attempt, added up. The {@code review_job} counters are zeroed when the row is handed to
 * the next attempt; this row is not, so a call that returns after its attempt was cancelled or handed on still
 * lands on the attempt that authenticated it.
 *
 * <p>The attempt's ledger rows are a projection of this row. {@link ReviewAttemptUsageService} is the only writer,
 * and it takes the job row before this one, on every path. The row holds ids and counts only, like {@code
 * review_job_script_usage}: the review it belongs to is read from the job row when a projection needs it. It goes
 * with its job (a cascading foreign key) and is removed when an erasure selects its job.
 */
@Entity
@Table(
        name = "review_job_attempt_usage",
        indexes = {@Index(name = "idx_review_job_attempt_usage_workspace", columnList = "workspace_id")},
        // The charges reference this key, so a charge always belongs to the owner's workspace.
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_review_job_attempt_usage_workspace",
                    columnNames = {"job_id", "attempt", "workspace_id"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewAttemptUsage implements Persistable<ReviewAttemptUsage.Id> {

    @EmbeddedId
    private Id id;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    /**
     * When the attempt's spend is dated in the ledger. The first settlement sets it, and no later one moves it, so
     * a late call cannot move the attempt into another month.
     */
    @Column(name = "occurred_at")
    private @Nullable Instant occurredAt;

    /** {@code null} until the attempt returned a runner report. The report is kept as written, the first time. */
    @Column(name = "runner_calls")
    private @Nullable Integer runnerCalls;

    @Column(name = "runner_input_tokens")
    private @Nullable Integer runnerInputTokens;

    @Column(name = "runner_output_tokens")
    private @Nullable Integer runnerOutputTokens;

    @Column(name = "runner_reasoning_tokens")
    private @Nullable Integer runnerReasoningTokens;

    @Column(name = "runner_cache_read_tokens")
    private @Nullable Integer runnerCacheReadTokens;

    @Column(name = "runner_cache_write_tokens")
    private @Nullable Integer runnerCacheWriteTokens;

    /** Calls whose provider reported usage, including a reported zero, like the {@code review_job} counter. */
    @ColumnDefault("0")
    @Column(name = "proxy_calls", nullable = false)
    private int proxyCalls;

    @ColumnDefault("0")
    @Column(name = "proxy_input_tokens", nullable = false)
    private long proxyInputTokens;

    @ColumnDefault("0")
    @Column(name = "proxy_output_tokens", nullable = false)
    private long proxyOutputTokens;

    @ColumnDefault("0")
    @Column(name = "proxy_reasoning_tokens", nullable = false)
    private long proxyReasoningTokens;

    @ColumnDefault("0")
    @Column(name = "proxy_cache_read_tokens", nullable = false)
    private long proxyCacheReadTokens;

    @ColumnDefault("0")
    @Column(name = "proxy_cache_write_tokens", nullable = false)
    private long proxyCacheWriteTokens;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean unsaved;

    /**
     * The measurement of an attempt that no proxied call registered, such as one that started before this table
     * existed. It starts from the proxy counts the attempt's row holds while the caller holds that row and the row
     * is still on this attempt; {@link ReviewAttemptUsageService} checks both.
     */
    static ReviewAttemptUsage startedFrom(ReviewJob job, @Nullable ReviewJobLlmUsage proxyCounts) {
        ReviewAttemptUsage usage = new ReviewAttemptUsage();
        usage.id = new Id(job.getId(), job.getAttempt());
        usage.workspaceId = job.getWorkspaceId();
        if (proxyCounts != null) {
            usage.proxyCalls = Math.max(0, proxyCounts.totalCalls());
            usage.proxyInputTokens = Math.max(0, proxyCounts.inputTokens());
            usage.proxyOutputTokens = Math.max(0, proxyCounts.outputTokens());
            usage.proxyReasoningTokens = Math.max(0, proxyCounts.reasoningTokens());
            usage.proxyCacheReadTokens = Math.max(0, proxyCounts.cacheReadTokens());
            usage.proxyCacheWriteTokens = Math.max(0, proxyCounts.cacheWriteTokens());
        }
        usage.unsaved = true;
        return usage;
    }

    /** The runner's report as it wrote it, or {@code null} when the attempt returned none. */
    public AgentResult.@Nullable LlmUsage runnerUsage() {
        if (runnerCalls == null) return null;
        return new AgentResult.LlmUsage(
                null,
                runnerInputTokens,
                runnerOutputTokens,
                runnerReasoningTokens,
                runnerCacheReadTokens,
                runnerCacheWriteTokens,
                null,
                runnerCalls);
    }

    /** Every proxied call of the attempt, wherever the attempt was when the call returned. */
    public ReviewJobLlmUsage proxyUsage() {
        return new ReviewJobLlmUsage(
                proxyCalls,
                proxyInputTokens,
                proxyOutputTokens,
                proxyReasoningTokens,
                proxyCacheReadTokens,
                proxyCacheWriteTokens);
    }

    /** Keeps the first report: a repeated terminal receipt cannot replace the measurement it settled. */
    void recordRunnerUsage(AgentResult.@Nullable LlmUsage usage) {
        if (usage == null || runnerCalls != null) return;
        runnerCalls = Math.max(0, usage.totalCalls());
        runnerInputTokens = usage.inputTokens();
        runnerOutputTokens = usage.outputTokens();
        runnerReasoningTokens = usage.reasoningTokens();
        runnerCacheReadTokens = usage.cacheReadTokens();
        runnerCacheWriteTokens = usage.cacheWriteTokens();
    }

    void addProxyCall(ReviewJobLlmUsageDelta call) {
        proxyCalls = proxyCalls == Integer.MAX_VALUE ? proxyCalls : proxyCalls + 1;
        proxyInputTokens = saturatedAdd(proxyInputTokens, call.inputTokens());
        proxyOutputTokens = saturatedAdd(proxyOutputTokens, call.outputTokens());
        proxyReasoningTokens = saturatedAdd(proxyReasoningTokens, call.reasoningTokens());
        proxyCacheReadTokens = saturatedAdd(proxyCacheReadTokens, call.cacheReadTokens());
        proxyCacheWriteTokens = saturatedAdd(proxyCacheWriteTokens, call.cacheWriteTokens());
    }

    /** Dates the attempt the first time it is settled; afterwards it returns that date. */
    Instant occurredAt(Instant now) {
        if (occurredAt == null) occurredAt = now;
        return occurredAt;
    }

    boolean isSettled() {
        return occurredAt != null;
    }

    private static long saturatedAdd(long total, long tokens) {
        long added = Math.max(0, tokens);
        return total > Long.MAX_VALUE - added ? Long.MAX_VALUE : total + added;
    }

    @Override
    public boolean isNew() {
        return unsaved;
    }

    @PostLoad
    @PostPersist
    void markSaved() {
        unsaved = false;
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
    }
}
