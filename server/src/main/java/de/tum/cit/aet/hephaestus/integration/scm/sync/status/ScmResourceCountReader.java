package de.tum.cit.aet.hephaestus.integration.scm.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceCount;
import de.tum.cit.aet.hephaestus.integration.scm.domain.commit.CommitRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.RepositoryItemCountProjection;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Reads the per-entity-class mirror counts behind an SCM repository's sync-observability row, for every
 * repository of a connection at once.
 *
 * <p>Shared by both SCM providers rather than written twice: GitHub and GitLab mirror into the same
 * {@code integration.scm.domain} tables, so the counts are the same query either way — only the
 * per-class freshness watermarks differ, and those stay with the provider that persists them.
 *
 * <p><b>Cost contract.</b> Six grouped queries for the whole connection, regardless of repository count.
 * The read model this feeds renders every resource of every connected integration on one page load, so
 * a per-repository query would be an N+1 by construction — the batching is the point, not an
 * optimization.
 */
@Component
public class ScmResourceCountReader {

    private final IssueRepository issueRepository;
    private final IssueCommentRepository issueCommentRepository;
    private final PullRequestReviewRepository reviewRepository;
    private final PullRequestReviewCommentRepository reviewCommentRepository;
    private final CommitRepository commitRepository;

    public ScmResourceCountReader(
        IssueRepository issueRepository,
        IssueCommentRepository issueCommentRepository,
        PullRequestReviewRepository reviewRepository,
        PullRequestReviewCommentRepository reviewCommentRepository,
        CommitRepository commitRepository
    ) {
        this.issueRepository = issueRepository;
        this.issueCommentRepository = issueCommentRepository;
        this.reviewRepository = reviewRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.commitRepository = commitRepository;
    }

    /**
     * Per-repository, per-class counts for {@code repositoryIds}.
     *
     * @return a lookup keyed by repository id; a repository with no rows at all is absent from the map,
     *         so callers must treat "absent" as {@link ScmResourceCounts#empty()}
     */
    public Map<Long, ScmResourceCounts> countsByRepositoryId(Collection<Long> repositoryIds) {
        if (repositoryIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(repositoryIds);

        Map<Long, Long> issues = toMap(issueRepository.countIssuesGroupedByRepositoryIds(ids));
        Map<Long, Long> pullRequests = toMap(issueRepository.countPullRequestsGroupedByRepositoryIds(ids));
        Map<Long, Long> issueComments = toMap(issueCommentRepository.countGroupedByRepositoryIds(ids));
        Map<Long, Long> reviews = toMap(reviewRepository.countGroupedByRepositoryIds(ids));
        Map<Long, Long> reviewComments = toMap(reviewCommentRepository.countGroupedByRepositoryIds(ids));
        Map<Long, Long> commits = toMap(commitRepository.countGroupedByRepositoryIds(ids));

        return ids
            .stream()
            .collect(
                Collectors.toMap(Function.identity(), id ->
                    new ScmResourceCounts(
                        issues.getOrDefault(id, 0L),
                        pullRequests.getOrDefault(id, 0L),
                        issueComments.getOrDefault(id, 0L),
                        reviews.getOrDefault(id, 0L),
                        reviewComments.getOrDefault(id, 0L),
                        commits.getOrDefault(id, 0L)
                    )
                )
            );
    }

    private static Map<Long, Long> toMap(List<RepositoryItemCountProjection> rows) {
        return rows
            .stream()
            .collect(
                Collectors.toMap(
                    RepositoryItemCountProjection::getRepositoryId,
                    RepositoryItemCountProjection::getItemCount,
                    (a, b) -> a
                )
            );
    }

    /**
     * One repository's mirrored row counts, per entity class.
     *
     * @param issues         pure issues (pull requests excluded)
     * @param pullRequests   pull / merge requests
     * @param issueComments  comments on issues and pull requests
     * @param reviews        pull-request review decisions
     * @param reviewComments inline review comments
     * @param commits        commits
     */
    public record ScmResourceCounts(
        long issues,
        long pullRequests,
        long issueComments,
        long reviews,
        long reviewComments,
        long commits
    ) {
        private static final ScmResourceCounts EMPTY = new ScmResourceCounts(0, 0, 0, 0, 0, 0);

        public static ScmResourceCounts empty() {
            return EMPTY;
        }

        /** The headline {@code itemCount}: issues + pull requests, matching what the sync calls an "item". */
        public long headlineItemCount() {
            return issues + pullRequests;
        }

        /**
         * Renders to the SPI breakdown, in the order the classes are synced (issues, then pull requests,
         * then the nested classes that ride along with them, then commits) — which is also the order in
         * which a stalled class shows up as an unexpectedly old sibling watermark.
         *
         * <p>Each {@code *SyncedAt} is the watermark for that class alone. Pass {@code null} for any
         * class whose freshness the provider does not persist: absent is honest, whereas substituting a
         * sibling's timestamp would claim exactly the freshness this breakdown exists to disprove.
         */
        public List<SyncResourceCount> toSyncResourceCounts(
            @Nullable Instant issuesSyncedAt,
            @Nullable Instant pullRequestsSyncedAt
        ) {
            List<SyncResourceCount> counts = new ArrayList<>(6);
            counts.add(new SyncResourceCount(SyncResourceCount.KEY_ISSUES, "Issues", issues, issuesSyncedAt));
            counts.add(
                new SyncResourceCount(
                    SyncResourceCount.KEY_PULL_REQUESTS,
                    "Pull requests",
                    pullRequests,
                    pullRequestsSyncedAt
                )
            );
            // Comments, reviews and review comments are fetched nested inside the issue and PR pages —
            // no independent watermark column exists for them, so they report none.
            counts.add(new SyncResourceCount(SyncResourceCount.KEY_ISSUE_COMMENTS, "Comments", issueComments, null));
            counts.add(new SyncResourceCount(SyncResourceCount.KEY_REVIEWS, "Reviews", reviews, null));
            counts.add(
                new SyncResourceCount(SyncResourceCount.KEY_REVIEW_COMMENTS, "Review comments", reviewComments, null)
            );
            counts.add(new SyncResourceCount(SyncResourceCount.KEY_COMMITS, "Commits", commits, null));
            return List.copyOf(counts);
        }
    }
}
