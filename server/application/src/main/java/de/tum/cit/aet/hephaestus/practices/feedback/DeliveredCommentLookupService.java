package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.spi.DeliveredIssueCommentLookup;
import de.tum.cit.aet.hephaestus.integration.core.spi.DeliveredPullRequestCommentLookup;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveredCommentLookupService implements DeliveredIssueCommentLookup, DeliveredPullRequestCommentLookup {
    private static final List<String> CONFIRMED_STATES =
            List.of(FeedbackCopyState.POSTED.name(), FeedbackCopyState.RETRACTED.name());
    private final FeedbackQueryRepository queries;

    @Override
    @WorkspaceAgnostic("Provider-ingest provenance for the same native issue across its monitoring workspaces")
    public List<DeliveredComment> findForIssue(long issueId, ItemKind kind) {
        return queries.issueReceipts(issueId, kind.value(), FeedbackCopyKind.SUMMARY.name(), CONFIRMED_STATES).stream()
                .map(DeliveredCommentLookupService::comment)
                .toList();
    }

    @Override
    public Map<Long, List<DeliveredComment>> findForRepository(long workspaceId, long repositoryId, ItemKind kind) {
        return queries
                .repositoryReceipts(
                        workspaceId, repositoryId, kind.value(), FeedbackCopyKind.SUMMARY.name(), CONFIRMED_STATES)
                .stream()
                .collect(Collectors.groupingBy(
                        FeedbackQueryRepository.IssueReceipt::getIssueId,
                        Collectors.mapping(DeliveredCommentLookupService::comment, Collectors.toList())));
    }

    @Override
    public CommentIds findForPullRequest(long workspaceId, long pullRequestId, ItemKind kind) {
        var regular = new HashSet<Long>();
        var inline = new HashSet<Long>();
        for (var receipt : queries.pullRequestReceipts(workspaceId, pullRequestId, kind.value(), CONFIRMED_STATES))
            (receipt.getInline() ? inline : regular).add(receipt.getNativeId());
        return new CommentIds(Set.copyOf(regular), Set.copyOf(inline));
    }

    private static DeliveredComment comment(FeedbackQueryRepository.IssueReceipt row) {
        return new DeliveredComment(row.getExternalRef(), row.getUrl());
    }
}
