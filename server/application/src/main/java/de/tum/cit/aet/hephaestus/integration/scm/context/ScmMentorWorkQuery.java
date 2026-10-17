package de.tum.cit.aet.hephaestus.integration.scm.context;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.MentorUserCounts;
import de.tum.cit.aet.hephaestus.integration.core.spi.MentorWorkQuery;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceActorSelector;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Account identity translation stays on the connected SCM instance. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScmMentorWorkQuery implements MentorWorkQuery {
    private final AccountIdentityQuery identities;
    private final GitProviderRegistry providers;
    private final WorkspaceActorSelector selector;
    private final MentorContextQueryRepository queries;

    public List<Long> actorIds(long workspaceId, long accountId) {
        var connected = selector.connectedProviderId(workspaceId);
        if (connected.isEmpty()) return List.of();
        return identities.activeLinksForAccount(accountId).stream()
                .filter(link -> link.providerId().equals(connected.get()))
                .map(link -> providers.findActorId(link.providerId(), link.providerUserKey()))
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    @Override
    public MentorUserCounts fetchUserCounts(
            Long workspaceId, Long accountId, Instant twoWeeksAgo, Instant weekAgo, Instant now) {
        return queries.fetchUserCounts(workspaceId, actorIds(workspaceId, accountId), twoWeeksAgo, weekAgo, now);
    }

    @Override
    public List<AssignedIssue> findAssignedOpenIssues(Long workspaceId, Long accountId) {
        return queries.findAssignedOpenIssues(workspaceId, actorIds(workspaceId, accountId));
    }

    @Override
    public List<RequestedReview> findPendingReviewRequestPrs(Long workspaceId, Long accountId) {
        return queries.findPendingReviewRequestPrs(workspaceId, actorIds(workspaceId, accountId));
    }
}
