package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemMergeTimeQuery;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewTimingFact;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewTimingFacts;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewTimingFactsQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewTimingFactsQueryService implements ReviewTimingFactsQuery {
    private static final int PAGE_SIZE = 1000;
    private final JdbcClient jdbc;
    private final ItemMergeTimeQuery mergeTimes;

    @Override
    public List<ReviewTimingFact> window(long workspaceId, Instant from, Instant to) {
        if (!from.isBefore(to)) throw new IllegalArgumentException("Use a nonempty timing range.");
        var output = new ArrayList<ReviewTimingFact>();
        Cursor after = null;
        while (true) {
            var sql =
                    "SELECT id,created_at FROM review WHERE workspace_id=:ws AND created_at>=:from AND created_at<:to";
            var page = after == null
                    ? jdbc.sql(sql + " ORDER BY created_at,id::text LIMIT :limit")
                            .param("ws", workspaceId)
                            .param("from", Timestamp.from(from))
                            .param("to", Timestamp.from(to))
                            .param("limit", PAGE_SIZE)
                    : jdbc.sql(sql
                                    + " AND (created_at,id::text)>(:afterTime,:afterId) ORDER BY"
                                    + " created_at,id::text LIMIT :limit")
                            .param("ws", workspaceId)
                            .param("from", Timestamp.from(from))
                            .param("to", Timestamp.from(to))
                            .param("limit", PAGE_SIZE)
                            .param("afterTime", after.createdAt())
                            .param("afterId", after.id().toString());
            var rows = page.query((rs, n) -> new Cursor(
                            Objects.requireNonNull(rs.getObject(1, UUID.class)),
                            Objects.requireNonNull(instant(rs, "created_at"))))
                    .list();
            output.addAll(timelines(workspaceId, rows.stream().map(Cursor::id).toList()));
            if (rows.size() < PAGE_SIZE) return List.copyOf(output);
            after = rows.getLast();
        }
    }

    @Override
    public ReviewTimingFacts facts(long workspaceId, List<UUID> reviewIds, Instant from, Instant to) {
        if (!from.isBefore(to)) throw new IllegalArgumentException("Use a nonempty timing range.");
        double asked = 0;
        double finishedAsked = 0;
        long afterMerge = 0;
        long startedByMerge = 0;
        for (var fact : timelines(workspaceId, reviewIds)) {
            var delay = asked(fact);
            if (delay != null) {
                if (fact.firstAutomaticPostedAt() != null) asked += delay;
                if (fact.finished()) finishedAsked += delay;
            }
            var completed = fact.completedAt();
            var merged = fact.mergedAt();
            if (fact.finished() && completed != null && merged != null && merged.isBefore(completed)) {
                afterMerge++;
                if (fact.startedByMerge()) startedByMerge++;
            }
        }
        long withheld = jdbc.sql("SELECT COUNT(*) FROM feedback WHERE workspace_id=:ws AND stop_reason='ITEM_MERGED'"
                        + " AND created_at>=:from AND created_at<:to")
                .param("ws", workspaceId)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((rs, n) -> rs.getLong(1))
                .single();
        return new ReviewTimingFacts(asked, finishedAsked, afterMerge, startedByMerge, withheld);
    }

    @Override
    public List<ReviewTimingFact> timelines(long workspaceId, List<UUID> reviewIds) {
        var ids = reviewIds.stream()
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        var output = new ArrayList<ReviewTimingFact>();
        for (int start = 0; start < ids.size(); start += PAGE_SIZE) {
            var rows = jdbc.sql("""
                SELECT r.id,r.origin,r.created_at,r.completed_at,r.public_review_at,r.status,
                       i.id AS item_id,i.kind,i.source_id,i.parent_id,i.path,
                       CASE WHEN r.origin='EVENT' AND e.found_by IN ('WEBHOOK','SYNC') THEN e.occurred_at END AS event_at,
                       r.origin='EVENT' AND e.name='scm.pull_request.merged' AS started_by_merge,
                       (SELECT MIN(c.posted_at) FROM feedback f JOIN feedback_copy c
                          ON c.feedback_id=f.id AND c.workspace_id=f.workspace_id
                        WHERE f.workspace_id=r.workspace_id AND f.review_id=r.id AND f.destination='ON_WORK'
                          AND NOT EXISTS(SELECT 1 FROM feedback_decision d WHERE d.workspace_id=f.workspace_id
                            AND d.feedback_id=f.id AND d.action='APPROVE')) AS first_posted_at,
                       (SELECT MIN(f.delivered_at) FROM feedback f WHERE f.workspace_id=r.workspace_id
                          AND f.review_id=r.id AND f.destination IN ('PRACTICE_PAGE','CONVERSATION')) AS first_read_at
                FROM review r JOIN item i ON i.id=r.item_id AND i.workspace_id=r.workspace_id
                LEFT JOIN item_event e ON e.id=r.event_id AND e.workspace_id=r.workspace_id AND e.item_id=r.item_id
                WHERE r.workspace_id=:ws AND r.id=ANY(:ids) ORDER BY r.id::text
                """)
                    .param("ws", workspaceId)
                    .param(
                            "ids",
                            new SqlArrayValue(
                                    "uuid",
                                    ids.subList(start, Math.min(start + PAGE_SIZE, ids.size()))
                                            .toArray()))
                    .query((rs, n) -> row(workspaceId, rs))
                    .list();
            var merged = mergeTimes.mergedAt(
                    workspaceId, rows.stream().map(Row::item).distinct().toList());
            for (var row : rows) {
                var fact = row.fact();
                output.add(new ReviewTimingFact(
                        fact.reviewId(),
                        fact.origin(),
                        fact.createdAt(),
                        fact.eventAt(),
                        fact.completedAt(),
                        fact.publicCompletedAt(),
                        fact.firstAutomaticPostedAt(),
                        fact.firstReadAt(),
                        merged.get(row.item().itemId()),
                        fact.finished(),
                        fact.startedByMerge()));
            }
        }
        return List.copyOf(output);
    }

    private static Row row(long workspaceId, ResultSet rs) throws SQLException {
        var completed = instant(rs, "completed_at");
        return new Row(
                new ItemRef(
                        workspaceId,
                        rs.getLong("item_id"),
                        new ItemKind(rs.getString("kind")),
                        rs.getObject("source_id", Long.class),
                        rs.getObject("parent_id", Long.class),
                        rs.getString("path")),
                new ReviewTimingFact(
                        Objects.requireNonNull(rs.getObject("id", UUID.class)),
                        ReviewOrigin.valueOf(rs.getString("origin")),
                        Objects.requireNonNull(instant(rs, "created_at")),
                        instant(rs, "event_at"),
                        completed,
                        instant(rs, "public_review_at"),
                        instant(rs, "first_posted_at"),
                        instant(rs, "first_read_at"),
                        null,
                        completed != null
                                && (rs.getString("status").equals("COMPLETE")
                                        || rs.getString("status").equals("PARTIAL")),
                        rs.getBoolean("started_by_merge")));
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static @Nullable Double asked(ReviewTimingFact fact) {
        if (fact.origin() != ReviewOrigin.EVENT) return 0.0;
        var event = fact.eventAt();
        if (event == null) return null;
        var delay = Duration.between(event, fact.createdAt());
        return delay.isNegative() ? 0.0 : delay.getSeconds() + delay.getNano() / 1_000_000_000.0;
    }

    private record Row(ItemRef item, ReviewTimingFact fact) {}

    private record Cursor(UUID id, Instant createdAt) {}
}
