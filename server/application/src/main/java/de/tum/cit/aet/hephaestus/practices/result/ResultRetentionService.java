package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanReportService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@WorkspaceAgnostic("Selects expired product proof globally and erases each workspace in its own transaction")
public class ResultRetentionService {
    private final JdbcClient jdbc;
    private final ReviewRepository reviews;
    private final ResultRepository results;
    private final ScanReportService reports;
    private final PlatformTransactionManager transactions;
    private final Clock clock;

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "result-retention", lockAtMostFor = "PT30M")
    public void purge() {
        var cutoff = clock.instant().minus(Duration.ofDays(400));
        var workspaces = jdbc.sql(
                        "SELECT DISTINCT workspace_id FROM review WHERE kind='SCAN' AND completed_at<=:cutoff AND"
                            + " COALESCE((report->>'expired')::boolean,false)=false UNION SELECT DISTINCT workspace_id"
                            + " FROM result WHERE ended_at<=:cutoff ORDER BY workspace_id")
                .param("cutoff", Timestamp.from(cutoff))
                .query((rs, n) -> rs.getLong(1))
                .list();
        for (var workspace : workspaces) purgeWorkspace(workspace, cutoff);
    }

    public void purgeWorkspace(long workspaceId, Instant cutoff) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var scans = jdbc.sql(
                            "SELECT id FROM review WHERE workspace_id=:ws AND kind='SCAN' AND completed_at<=:cutoff"
                                    + " AND COALESCE((report->>'expired')::boolean,false)=false ORDER BY id::text")
                    .param("ws", workspaceId)
                    .param("cutoff", Timestamp.from(cutoff))
                    .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                    .list();
            for (var scan : scans) reports.expire(workspaceId, scan);
            var ids = jdbc.sql("""
                           SELECT r.id FROM result r WHERE r.workspace_id=:ws AND r.ended_at<=:cutoff
                           AND NOT EXISTS(SELECT 1 FROM feedback_basis b WHERE b.workspace_id=r.workspace_id AND b.result_id=r.id)
                           AND NOT EXISTS(SELECT 1 FROM review_item i JOIN review v ON v.id=i.review_id AND v.workspace_id=i.workspace_id WHERE i.workspace_id=r.workspace_id AND i.result_id=r.id AND v.kind='SCAN' AND (v.completed_at IS NULL OR v.completed_at>:cutoff))
                AND NOT EXISTS(SELECT 1 FROM review_item member JOIN review scan ON scan.id=member.review_id AND scan.workspace_id=member.workspace_id CROSS JOIN LATERAL jsonb_array_elements(COALESCE(member.detail->'problemComparison','[]'::jsonb)) comparison JOIN problem prior ON (prior.id=(comparison->>'problemId')::uuid OR prior.id=(comparison->>'previousProblemId')::uuid) AND prior.workspace_id=member.workspace_id WHERE prior.result_id=r.id AND prior.workspace_id=r.workspace_id AND scan.kind='SCAN' AND (scan.completed_at IS NULL OR scan.completed_at>:cutoff))
                           ORDER BY r.id::text
                """)
                    .param("ws", workspaceId)
                    .param("cutoff", Timestamp.from(cutoff))
                    .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                    .list();
            if (ids.isEmpty()) return;
            var parents = results.findByWorkspaceIdAndIdIn(workspaceId, ids).stream()
                    .map(r -> r.getReviewId())
                    .distinct()
                    .sorted(Comparator.comparing(UUID::toString))
                    .toList();
            for (var parent : parents)
                if (reviews.lockByIdAndWorkspaceId(parent, workspaceId).isEmpty())
                    throw new IllegalStateException("The retained review is missing.");
            for (var id : ids)
                if (results.lockByIdAndWorkspaceId(id, workspaceId).isEmpty())
                    throw new IllegalStateException("The retained result is missing.");
            reports.eraseResultReferences(workspaceId, ids);
            jdbc.sql("DELETE FROM result r WHERE r.workspace_id=:ws AND r.id IN (:ids) AND NOT EXISTS(SELECT 1"
                            + " FROM feedback_basis b WHERE b.workspace_id=r.workspace_id AND b.result_id=r.id)")
                    .param("ws", workspaceId)
                    .param("ids", ids)
                    .update();
        });
    }
}
