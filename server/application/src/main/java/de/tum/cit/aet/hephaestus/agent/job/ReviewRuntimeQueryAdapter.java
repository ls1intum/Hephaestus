package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.llm.usage.UsagePricingState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewQueueJob;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewQueueSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewTimingFactsQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewTimingSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewTimingStage;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewTimingSums;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewUsageAttempt;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewUsageSnapshot;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Publishes runtime rows and retained ledger spend without reading product persistence. */
@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewRuntimeQueryAdapter implements ReviewRuntimeQuery {
    private final ReviewJobRepository jobs;
    private final ReviewAssignmentQuery assignments;
    private final JdbcClient jdbc;
    private final ReviewTimingFactsQuery timingFacts;

    @Override
    public Optional<ReviewRuntimeSnapshot> runtime(long workspaceId, UUID reviewId) {
        var rows = jobs.findByReviewIdAndWorkspaceIdOrderByAvailableAtAsc(reviewId, workspaceId);
        return rows.isEmpty()
                ? Optional.empty()
                : Optional.of(new ReviewRuntimeSnapshot(rows.stream()
                        .map(job -> new ReviewJobSnapshot(
                                job.getId(),
                                job.getStatus(),
                                job.getAttempt(),
                                job.getProcessingStatus(),
                                job.getAvailableAt(),
                                job.getStartedAt(),
                                job.getCompletedAt(),
                                job.getExitCode(),
                                job.getErrorMessage(),
                                job.getTraceId()))
                        .toList()));
    }

    @Override
    public Set<UUID> activeJobIds(long workspaceId, UUID reviewId) {
        return jobs.findByReviewIdAndWorkspaceIdAndStatusIn(
                        reviewId, workspaceId, Set.of(ReviewJobStatus.QUEUED, ReviewJobStatus.RUNNING))
                .map(job -> Set.of(job.getId()))
                .orElseGet(Set::of);
    }

    @Override
    public Set<UUID> unsettledJobIds(long workspaceId, UUID reviewId) {
        return Set.copyOf(
                jdbc.sql("SELECT id FROM review_job WHERE workspace_id=:ws AND review_id=:review AND status NOT IN"
                                + " ('QUEUED','RUNNING') AND processing_status IN ('NONE','PENDING')")
                        .param("ws", workspaceId)
                        .param("review", reviewId)
                        .query(UUID.class)
                        .list());
    }

    @Override
    public ReviewUsageSnapshot usage(long workspaceId, UUID reviewId) {
        var attempts = jdbc.sql("""
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
        boolean priced =
                attempts.stream().allMatch(a -> a.costUsd() != null && a.pricingState() != UsagePricingState.UNPRICED);
        BigDecimal cost = priced
                ? attempts.stream()
                        .map(a -> Objects.requireNonNull(a.costUsd()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;
        return new ReviewUsageSnapshot(
                cost,
                priced,
                attempts.stream().mapToLong(ReviewUsageAttempt::calls).sum(),
                attempts.stream().mapToLong(ReviewUsageAttempt::inputTokens).sum(),
                attempts.stream().mapToLong(ReviewUsageAttempt::outputTokens).sum(),
                attempts);
    }

    @Override
    public ReviewQueueSnapshot queue(long workspaceId, Instant asOf, int headLimit) {
        if (headLimit < 0 || headLimit > 100) throw new IllegalArgumentException("Invalid queue head limit");
        var totals = jdbc.sql("""
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
        long workers = jdbc.sql("SELECT COUNT(*) FROM worker_registry WHERE last_heartbeat>=:live")
                .param("live", asOf.minus(AgentProperties.WORKER_LEASE_TTL))
                .query(Long.class)
                .single();
        var head = jdbc
                .sql("SELECT id FROM review_job WHERE workspace_id=:ws AND status IN ('QUEUED','RUNNING') ORDER BY"
                        + " available_at,id LIMIT :limit")
                .param("ws", workspaceId)
                .param("limit", headLimit)
                .query(UUID.class)
                .list()
                .stream()
                .map(nullableId -> {
                    var id = Objects.requireNonNull(nullableId);
                    var job = jobs.findByIdAndWorkspaceId(id, workspaceId).orElseThrow();
                    var people = job.getInput().assignments().stream()
                            .flatMap(assignmentId -> assignments.find(workspaceId, assignmentId).stream())
                            .map(a -> a.developerId())
                            .distinct()
                            .toList();
                    Long developer = people.size() == 1 ? people.getFirst() : null;
                    return new ReviewQueueJob(
                            id,
                            job.getReviewId(),
                            job.getInput().itemId(),
                            developer,
                            job.getStatus(),
                            ReviewJob.HOLD_REASON_BUDGET.equals(job.getHoldReason()),
                            job.getAvailableAt());
                })
                .toList();
        return new ReviewQueueSnapshot(
                asOf,
                totals.running(),
                workers,
                totals.waiting(),
                totals.oldest(),
                totals.held(),
                head,
                totals.hour(),
                totals.day());
    }

    @Override
    public ReviewTimingSnapshot timing(long workspaceId, Instant from, Instant to, Instant longWaitThreshold) {
        if (!from.isBefore(to)) throw new IllegalArgumentException("The timing window is empty");
        var product = timingFacts;
        var timelines = product.window(workspaceId, from, to);
        var reviewIds = timelines.stream().map(fact -> fact.reviewId()).toList();
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
        var byReview = timelines.stream().collect(Collectors.toMap(fact -> fact.reviewId(), Function.identity()));
        var samples = new HashMap<String, List<Double>>();
        double queue = 0,
                preparation = 0,
                model = 0,
                unsplit = 0,
                finishedQueue = 0,
                finishedPreparation = 0,
                finishedModel = 0,
                finishedUnsplit = 0;
        long longWait = 0;
        for (var row : rows) {
            if (row.status() == ReviewJobStatus.QUEUED
                    && !ReviewJob.HOLD_REASON_BUDGET.equals(row.holdReason())
                    && row.availableAt().isBefore(longWaitThreshold)) longWait++;
            var start = row.startedAt();
            if (start == null) continue;
            sample(samples, "QUEUE", seconds(row.availableAt(), start));
            var fact = Objects.requireNonNull(byReview.get(row.reviewId()));
            var publicAt = fact.publicCompletedAt();
            var end = row.completedAt();
            var execution = row.executionStartedAt();
            var measuredEnd = end == null ? publicAt : end;
            if (measuredEnd != null) {
                if (execution == null) sample(samples, "RUN_UNSPLIT", seconds(start, measuredEnd));
                else {
                    sample(samples, "PREPARATION", seconds(start, execution));
                    sample(samples, "MODEL", seconds(execution, measuredEnd));
                }
            }
            if (fact.firstAutomaticPostedAt() != null
                    && publicAt != null
                    && row.availableAt().isBefore(publicAt)) {
                queue += interval(row.availableAt(), start, publicAt);
                if (execution == null) unsplit += interval(start, end, publicAt);
                else {
                    preparation += interval(start, execution, publicAt);
                    model += interval(execution, end, publicAt);
                }
            }
            if (fact.finished() && end != null) {
                finishedQueue += seconds(row.availableAt(), start);
                if (execution == null) finishedUnsplit += seconds(start, end);
                else {
                    finishedPreparation += seconds(start, execution);
                    finishedModel += seconds(execution, end);
                }
            }
        }
        Double posted = null, finishedPosted = null;
        for (var fact : timelines) {
            Instant basis = fact.origin() == ReviewOrigin.EVENT ? fact.eventAt() : fact.createdAt();
            if (basis != null) {
                sample(samples, "ASKED", seconds(basis, fact.createdAt()));
                if (fact.firstAutomaticPostedAt() != null)
                    sample(samples, "TOTAL", seconds(basis, fact.firstAutomaticPostedAt()));
                if (fact.finished() && fact.completedAt() != null)
                    sample(samples, "FINISHED", seconds(basis, fact.completedAt()));
            }
            var publicAt = fact.publicCompletedAt();
            if (publicAt != null && fact.firstAutomaticPostedAt() != null) {
                double delay = seconds(publicAt, fact.firstAutomaticPostedAt());
                sample(samples, "POSTED", delay);
                posted = (posted == null ? 0d : posted) + delay;
                if (fact.finished()) finishedPosted = (finishedPosted == null ? 0d : finishedPosted) + delay;
            }
            if (publicAt != null && fact.firstReadAt() != null)
                sample(samples, "READ", seconds(publicAt, fact.firstReadAt()));
        }
        var stages = new HashMap<String, ReviewTimingStage>();
        for (var entry : samples.entrySet()) {
            var sorted = entry.getValue().stream().sorted().toList();
            stages.put(
                    entry.getKey(), new ReviewTimingStage(sorted.size(), quantile(sorted, 0.5), quantile(sorted, 0.9)));
        }
        var facts = product.facts(workspaceId, reviewIds, from, to);
        return new ReviewTimingSnapshot(
                stages,
                new ReviewTimingSums(facts.asked(), queue, preparation, model, unsplit, posted),
                new ReviewTimingSums(
                        facts.finishedAsked(),
                        finishedQueue,
                        finishedPreparation,
                        finishedModel,
                        finishedUnsplit,
                        finishedPosted),
                longWait,
                facts.finishedAfterMerge(),
                facts.finishedAfterMergeStartedByMerge(),
                facts.feedbackWithheldAsMerged());
    }

    private static void sample(Map<String, List<Double>> samples, String name, double value) {
        samples.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
    }

    private static double interval(Instant start, @Nullable Instant end, Instant cutoff) {
        if (!start.isBefore(cutoff)) return 0;
        return seconds(start, end == null || end.isAfter(cutoff) ? cutoff : end);
    }

    private record TimingJob(
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

    private static double seconds(Instant from, Instant to) {
        var elapsed = Duration.between(from, to);
        return Math.max(0, elapsed.getSeconds() + elapsed.getNano() / 1_000_000_000d);
    }

    private static double quantile(List<Double> sorted, double p) {
        double index = (sorted.size() - 1) * p;
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * (index - lower);
    }

    private record QueueCounts(
            long running, long waiting, @Nullable Instant oldest, long held, long hour, long day) {}
}
