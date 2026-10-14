package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.time.TimeBuckets;
import de.tum.cit.aet.hephaestus.core.time.TimeRange;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.dto.GroupRefDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.ReviewFeedbackCountsDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.OutcomeVectorDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewBucketDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewEvidenceOutcomeDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewOverviewDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewPracticeCountsDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewResultCountsDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewStatusCountsDTO;
import de.tum.cit.aet.hephaestus.practices.spi.TimeBucketSize;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewOverviewService {
    private final JdbcClient jdbc;
    private final PracticeRepository practices;

    public TimeRange allTime(long ws, @Nullable Instant to, Clock clock) {
        var now = clock.instant();
        var end = to == null || to.isAfter(now) ? now : to;
        var first = jdbc.sql("SELECT min(created_at) FROM review WHERE workspace_id=:ws")
                .param("ws", ws)
                .query(OffsetDateTime.class)
                .optional()
                .map(OffsetDateTime::toInstant)
                .filter(t -> t.isBefore(end))
                .orElse(end);
        return new TimeRange(first, end);
    }

    public ReviewOverviewDTO overview(long ws, TimeBuckets buckets) {
        var range = buckets.range();
        var result = outcomes(ws, range, null);
        var feedback = feedback(ws, range, null);
        var status = counts(ws, range, "review", "created_at", "status");
        var reviewCounts = new ReviewStatusCountsDTO(
                get(status, "QUEUED"),
                get(status, "RUNNING"),
                get(status, "COMPLETE"),
                get(status, "PARTIAL"),
                get(status, "FAILED"),
                get(status, "CANCELLED"));
        var byBucket = IntStream.range(0, buckets.starts().size())
                .mapToObj(i -> {
                    var from = buckets.starts().get(i).isBefore(range.from())
                            ? range.from()
                            : buckets.starts().get(i);
                    var to = i + 1 == buckets.starts().size()
                            ? range.to()
                            : buckets.starts().get(i + 1);
                    return new ReviewBucketDTO(
                            buckets.starts().get(i),
                            total(ws, "review", "created_at", from, to),
                            total(ws, "result", "recorded_at", from, to),
                            total(ws, "feedback", "created_at", from, to));
                })
                .toList();
        var byPractice = practices.findByWorkspaceId(ws).stream()
                .map(p -> {
                    var g = p.getGroup();
                    return new ReviewPracticeCountsDTO(
                            p.getSlug(),
                            Objects.requireNonNull(p.getCurrentVersion().getName()),
                            g == null ? null : new GroupRefDTO(g.getSlug(), g.getName(), g.getIcon(), g.getColor()),
                            outcomes(ws, range, p.getId()),
                            incorrect(ws, range, p.getId()),
                            feedback(ws, range, p.getId()));
                })
                .filter(p -> p.results().met()
                                + p.results().notMet()
                                + p.results().doesNotApply()
                                + p.results().couldNotDetermine()
                                + p.feedback().delivered()
                                + p.feedback().ready()
                                + p.feedback().withheld()
                                + p.feedback().failed()
                                + p.feedback().awaitingApproval()
                                + p.feedback().unconfirmed()
                                + p.feedback().rejected()
                                + p.feedback().replaced()
                        > 0)
                .toList();
        return new ReviewOverviewDTO(
                range.from(),
                range.to(),
                TimeBucketSize.valueOf(buckets.size().name()),
                reviewCounts,
                result,
                incorrect(ws, range, null),
                feedback,
                byBucket,
                byPractice);
    }

    private ReviewResultCountsDTO outcomes(long ws, TimeRange range, @Nullable Long practice) {
        var params = new LinkedHashMap<String, Object>();
        params.put("ws", ws);
        params.put("from", range.from());
        params.put("to", range.to());
        if (practice != null) params.put("practice", practice);
        var c = jdbc
                .sql("SELECT outcome,count(*) FROM result WHERE workspace_id=:ws AND recorded_at>=:from AND"
                        + " recorded_at<:to"
                        + (practice == null ? "" : " AND practice_id=:practice") + " GROUP BY outcome")
                .params(params)
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getInt(2)))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new ReviewResultCountsDTO(
                get(c, "MET"), get(c, "NOT_MET"), get(c, "DOES_NOT_APPLY"), get(c, "COULD_NOT_DETERMINE"));
    }

    private ReviewFeedbackCountsDTO feedback(long ws, TimeRange range, @Nullable Long practice) {
        var params = new LinkedHashMap<String, Object>();
        params.put("ws", ws);
        params.put("from", range.from());
        params.put("to", range.to());
        if (practice != null) params.put("practice", practice);
        var c = jdbc
                .sql("SELECT f.state,count(*) FROM feedback f WHERE f.workspace_id=:ws AND f.created_at>=:from AND"
                        + " f.created_at<:to"
                        + (practice == null
                                ? ""
                                : " AND EXISTS(SELECT 1 FROM feedback_basis b JOIN result r ON"
                                        + " r.id=b.result_id AND r.workspace_id=b.workspace_id WHERE"
                                        + " b.workspace_id=f.workspace_id AND b.feedback_id=f.id AND"
                                        + " r.practice_id=:practice)")
                        + " GROUP BY f.state")
                .params(params)
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getInt(2)))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new ReviewFeedbackCountsDTO(
                get(c, "AWAITING_APPROVAL"),
                get(c, "READY"),
                get(c, "DELIVERED"),
                get(c, "UNCONFIRMED"),
                get(c, "WITHHELD"),
                get(c, "REJECTED"),
                get(c, "REPLACED"),
                get(c, "FAILED"));
    }

    private int incorrect(long ws, TimeRange range, @Nullable Long practice) {
        var params = new LinkedHashMap<String, Object>();
        params.put("ws", ws);
        params.put("from", range.from());
        params.put("to", range.to());
        if (practice != null) params.put("practice", practice);
        return jdbc.sql("SELECT count(*) FROM result r WHERE r.workspace_id=:ws AND r.recorded_at>=:from AND"
                        + " r.recorded_at<:to"
                        + (practice == null ? "" : " AND r.practice_id=:practice")
                        + " AND EXISTS(SELECT 1 FROM result_mark m WHERE m.workspace_id=r.workspace_id AND"
                        + " m.result_id=r.id AND m.kind='INCORRECT' AND m.removed_at IS NULL)")
                .params(params)
                .query((rs, n) -> rs.getInt(1))
                .single();
    }

    private Map<String, Integer> counts(long ws, TimeRange range, String table, String time, String field) {
        return jdbc
                .sql("SELECT " + field + ",count(*) FROM " + table + " WHERE workspace_id=:ws AND " + time
                        + ">=:from AND " + time + "<:to GROUP BY " + field)
                .param("ws", ws)
                .param("from", range.from())
                .param("to", range.to())
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getInt(2)))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private int total(long ws, String table, String time, Instant from, Instant to) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE workspace_id=:ws AND " + time + ">=:from AND " + time
                        + "<:to")
                .param("ws", ws)
                .param("from", from)
                .param("to", to)
                .query((rs, n) -> rs.getInt(1))
                .single();
    }

    private static int get(Map<String, Integer> counts, String key) {
        return counts.getOrDefault(key, 0);
    }

    public List<ReviewEvidenceOutcomeDTO> evidenceOutcomes(long ws) {
        var range = new TimeRange(Instant.now().minus(Duration.ofDays(30)), Instant.now());
        return practices.findByWorkspaceId(ws).stream()
                .map(p -> {
                    var c = outcomes(ws, range, p.getId());
                    var statuses = jdbc
                            .sql("SELECT rp.status,count(*) FROM review_practice rp JOIN review q ON"
                                    + " q.id=rp.review_id AND q.workspace_id=rp.workspace_id WHERE"
                                    + " rp.workspace_id=:ws AND rp.practice_id=:practice AND q.created_at>=:from"
                                    + " GROUP BY rp.status")
                            .param("ws", ws)
                            .param("practice", p.getId())
                            .param("from", range.from())
                            .query((rs, n) -> Map.entry(rs.getString(1), rs.getInt(2)))
                            .list()
                            .stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    return new ReviewEvidenceOutcomeDTO(
                            p.getSlug(),
                            Objects.requireNonNull(p.getCurrentVersion().getName()),
                            new OutcomeVectorDTO(c.met(), c.notMet(), c.doesNotApply(), c.couldNotDetermine()),
                            get(statuses, "WITHHELD"),
                            get(statuses, "NOT_REACHED"));
                })
                .toList();
    }
}
