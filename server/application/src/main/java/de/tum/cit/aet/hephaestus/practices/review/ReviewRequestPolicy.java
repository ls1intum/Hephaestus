package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequestReason;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewRequestPolicy {
    private final WorkspaceRepository workspaces;
    private final JdbcClient jdbc;
    private final PracticeReviewProperties properties;
    private final PracticeReviewCoverageService coverage;
    private final Clock clock;

    @Transactional
    public Optional<ReviewRequestReason> refusal(long workspaceId, long itemId, long accountId) {
        var workspace = workspaces.findByIdForUpdate(workspaceId).orElseThrow();
        if (workspace.getStatus() != Workspace.WorkspaceStatus.ACTIVE)
            return Optional.of(ReviewRequestReason.WORKSPACE_INACTIVE);
        if (!Boolean.TRUE.equals(workspace.getFeatures().getPracticesEnabled()))
            return Optional.of(ReviewRequestReason.PRACTICES_DISABLED);
        if (!coverage.eligibleAccountIds(workspaceId).contains(accountId))
            return Optional.of(ReviewRequestReason.MEMBER_AI_DECLINED);
        var now = clock.instant();
        int quota = properties.maxRequestsPerRequesterPerHour();
        if (quota > 0
                && jdbc.sql("SELECT COUNT(*) FROM review WHERE workspace_id=:ws AND"
                                        + " requested_by_id=:account AND origin='REQUEST' AND created_at>=:since")
                                .param("ws", workspaceId)
                                .param("account", accountId)
                                .param("since", now.minus(Duration.ofHours(1)))
                                .query((rs, n) -> rs.getLong(1))
                                .single()
                        >= quota) return Optional.of(ReviewRequestReason.REQUESTER_QUOTA_EXHAUSTED);
        int cooldown = workspace.getReviewSettings().resolveCooldownMinutes(properties.cooldownMinutes());
        if (cooldown > 0
                && jdbc.sql("SELECT EXISTS(SELECT 1 FROM review WHERE workspace_id=:ws AND item_id=:item AND"
                                + " origin='REQUEST' AND created_at>=:since)")
                        .param("ws", workspaceId)
                        .param("item", itemId)
                        .param("since", now.minus(Duration.ofMinutes(cooldown)))
                        .query(Boolean.class)
                        .single()) return Optional.of(ReviewRequestReason.REQUEST_COOLDOWN_ACTIVE);
        if (jdbc.sql("SELECT EXISTS(SELECT 1 FROM review WHERE workspace_id=:ws AND item_id=:item AND status IN"
                        + " ('QUEUED','RUNNING'))")
                .param("ws", workspaceId)
                .param("item", itemId)
                .query(Boolean.class)
                .single()) return Optional.of(ReviewRequestReason.CONCURRENT_DUPLICATE);
        return Optional.empty();
    }
}
