package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsagePricingState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewUsageAttempt;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Owns scoped runtime rows and ledger aggregates; product facts come through the product ports. */
@Repository
@ConditionalOnCaptureRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewRuntimeRepository {
    private final JdbcClient jdbc;

    public Set<UUID> unsettledJobIds(long workspaceId, UUID reviewId) {
        return Set.copyOf(
                jdbc.sql("SELECT id FROM review_job WHERE workspace_id=:ws AND review_id=:review AND status NOT IN"
                                + " ('QUEUED','RUNNING') AND processing_status IN ('NONE','PENDING')")
                        .param("ws", workspaceId)
                        .param("review", reviewId)
                        .query(UUID.class)
                        .list());
    }

    public List<ReviewUsageAttempt> usage(long workspaceId, UUID reviewId) {
        return jdbc.sql("""
            SELECT source_id,source_attempt,SUM(total_calls) calls,SUM(input_tokens) input_tokens,
              SUM(output_tokens) output_tokens,SUM(reasoning_tokens) reasoning_tokens,
              SUM(cache_read_tokens) cache_read_tokens,SUM(cache_write_tokens) cache_write_tokens,
              CASE WHEN bool_and(cost_usd IS NOT NULL AND pricing_state<>'UNPRICED') THEN SUM(cost_usd) END cost_usd,
              CASE WHEN bool_or(cost_usd IS NULL OR pricing_state='UNPRICED') THEN 'UNPRICED'
                   WHEN bool_and(pricing_state='NO_CHARGE') THEN 'NO_CHARGE' ELSE 'PRICED' END pricing_state
            FROM llm_usage_event WHERE workspace_id=:ws AND review_id=:review
            GROUP BY source_id,source_attempt ORDER BY source_id,source_attempt
            """)
                .param("ws", workspaceId)
                .param("review", reviewId)
                .query((rs, n) -> new ReviewUsageAttempt(
                        Objects.requireNonNull(rs.getObject("source_id", UUID.class)),
                        rs.getInt("source_attempt"),
                        rs.getLong("calls"),
                        rs.getLong("input_tokens"),
                        rs.getLong("output_tokens"),
                        rs.getLong("reasoning_tokens"),
                        rs.getLong("cache_read_tokens"),
                        rs.getLong("cache_write_tokens"),
                        rs.getBigDecimal("cost_usd"),
                        UsagePricingState.valueOf(Objects.requireNonNull(rs.getString("pricing_state")))))
                .list();
    }

    public QueueCounts queueCounts(long workspaceId, Instant asOf) {
        return jdbc.sql("""
            SELECT COUNT(*) FILTER (WHERE status='RUNNING') running,
              COUNT(*) FILTER (WHERE status='QUEUED' AND hold_reason IS NULL) waiting,
              MIN(available_at) FILTER (WHERE status='QUEUED' AND hold_reason IS NULL) oldest,
              COUNT(*) FILTER (WHERE status='QUEUED' AND hold_reason='BUDGET') held,
              COUNT(*) FILTER (WHERE completed_at>=:hour AND completed_at<=:at) hour,
              COUNT(*) FILTER (WHERE completed_at>=:day AND completed_at<=:at) day
            FROM review_job WHERE workspace_id=:ws
            """)
                .param("ws", workspaceId)
                .param("at", asOf)
                .param("hour", asOf.minusSeconds(3600))
                .param("day", asOf.minusSeconds(86400))
                .query((rs, n) -> new QueueCounts(
                        rs.getLong("running"),
                        rs.getLong("waiting"),
                        rs.getTimestamp("oldest") == null
                                ? null
                                : Objects.requireNonNull(rs.getTimestamp("oldest"))
                                        .toInstant(),
                        rs.getLong("held"),
                        rs.getLong("hour"),
                        rs.getLong("day")))
                .single();
    }

    @WorkspaceAgnostic("Counts the shared worker pool; the registry is not workspace-owned")
    public long liveWorkers(Instant asOf) {
        return jdbc.sql("SELECT COUNT(*) FROM worker_registry WHERE last_heartbeat>=:live")
                .param("live", asOf.minus(AgentProperties.WORKER_LEASE_TTL))
                .query(Long.class)
                .single();
    }

    public List<UUID> queueHead(long workspaceId, int headLimit) {
        return jdbc.sql("SELECT id FROM review_job WHERE workspace_id=:ws AND status IN ('QUEUED','RUNNING') ORDER BY"
                        + " available_at,id LIMIT :limit")
                .param("ws", workspaceId)
                .param("limit", headLimit)
                .query((rs, n) -> Objects.requireNonNull(rs.getObject("id", UUID.class)))
                .list();
    }

    public List<TimingJob> timingJobs(long workspaceId, List<UUID> reviewIds) {
        var rows = new ArrayList<TimingJob>();
        for (int offset = 0; offset < reviewIds.size(); offset += 1000) {
            rows.addAll(jdbc.sql("SELECT"
                            + " review_id,status,available_at,started_at,execution_started_at,completed_at,hold_reason"
                            + " FROM review_job WHERE workspace_id=:ws AND review_id=ANY(:ids) ORDER BY"
                            + " available_at,id")
                    .param("ws", workspaceId)
                    .param(
                            "ids",
                            new SqlArrayValue(
                                    "uuid",
                                    reviewIds
                                            .subList(offset, Math.min(offset + 1000, reviewIds.size()))
                                            .toArray()))
                    .query((rs, n) -> new TimingJob(
                            Objects.requireNonNull(rs.getObject("review_id", UUID.class)),
                            ReviewJobStatus.valueOf(Objects.requireNonNull(rs.getString("status"))),
                            Objects.requireNonNull(instant(rs, "available_at")),
                            instant(rs, "started_at"),
                            instant(rs, "execution_started_at"),
                            instant(rs, "completed_at"),
                            rs.getString("hold_reason")))
                    .list());
        }
        return rows;
    }

    public Spend spent(long workspaceId, UUID reviewId) {
        return jdbc.sql("SELECT COALESCE(SUM(cost_usd),0) cost,COALESCE(bool_and(cost_usd IS NOT NULL AND"
                        + " pricing_state<>'UNPRICED'),true) priced FROM llm_usage_event WHERE workspace_id=:ws"
                        + " AND review_id=:review")
                .param("ws", workspaceId)
                .param("review", reviewId)
                .query((rs, n) -> new Spend(Objects.requireNonNull(rs.getBigDecimal("cost")), rs.getBoolean("priced")))
                .single();
    }

    public record TimingJob(
            UUID reviewId,
            ReviewJobStatus status,
            Instant availableAt,
            @Nullable Instant startedAt,
            @Nullable Instant executionStartedAt,
            @Nullable Instant completedAt,
            @Nullable String holdReason) {}

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public record QueueCounts(
            long running, long waiting, @Nullable Instant oldest, long held, long hour, long day) {}

    public record Spend(BigDecimal cost, boolean priced) {}
}
