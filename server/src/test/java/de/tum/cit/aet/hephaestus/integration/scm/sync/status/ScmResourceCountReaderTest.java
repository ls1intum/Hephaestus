package de.tum.cit.aet.hephaestus.integration.scm.sync.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceCount;
import de.tum.cit.aet.hephaestus.integration.scm.domain.commit.CommitRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.RepositoryItemCountProjection;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.sync.status.ScmResourceCountReader.ScmResourceCounts;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ScmResourceCountReaderTest extends BaseUnitTest {

    private static final long REPO_A = 900L;
    private static final long REPO_B = 901L;
    private static final long REPO_C = 902L;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private IssueCommentRepository issueCommentRepository;

    @Mock
    private PullRequestReviewRepository reviewRepository;

    @Mock
    private PullRequestReviewCommentRepository reviewCommentRepository;

    @Mock
    private CommitRepository commitRepository;

    private ScmResourceCountReader reader;

    @BeforeEach
    void setUp() {
        reader = new ScmResourceCountReader(
            issueRepository,
            issueCommentRepository,
            reviewRepository,
            reviewCommentRepository,
            commitRepository
        );
    }

    /** Test double for the Spring Data projection the grouped count queries return. */
    private static RepositoryItemCountProjection row(long repositoryId, long itemCount) {
        return new RepositoryItemCountProjection() {
            @Override
            public Long getRepositoryId() {
                return repositoryId;
            }

            @Override
            public Long getItemCount() {
                return itemCount;
            }
        };
    }

    @Nested
    class CountsByRepositoryId {

        @Test
        void emptyInput_returnsEmptyMapAndQueriesNothing() {
            Map<Long, ScmResourceCounts> counts = reader.countsByRepositoryId(List.of());

            assertThat(counts).isEmpty();
            // The short circuit is the point: an `IN ()` over an empty id list is a pointless round trip
            // per class, and some drivers reject it outright.
            verifyNoInteractions(
                issueRepository,
                issueCommentRepository,
                reviewRepository,
                reviewCommentRepository,
                commitRepository
            );
        }

        @Test
        void allSixQueries_mapOntoTheRightRepositoryAndClass() {
            // Distinct numbers everywhere so a class-to-class or repo-to-repo mix-up cannot pass by
            // coincidence — six same-shaped queries are easy to cross-wire.
            when(issueRepository.countIssuesGroupedByRepositoryIds(anyCollection())).thenReturn(
                List.of(row(REPO_A, 11), row(REPO_B, 21))
            );
            when(issueRepository.countPullRequestsGroupedByRepositoryIds(anyCollection())).thenReturn(
                List.of(row(REPO_A, 12), row(REPO_B, 22))
            );
            when(issueCommentRepository.countGroupedByRepositoryIds(anyCollection())).thenReturn(
                List.of(row(REPO_A, 13), row(REPO_B, 23))
            );
            when(reviewRepository.countGroupedByRepositoryIds(anyCollection())).thenReturn(
                List.of(row(REPO_A, 14), row(REPO_B, 24))
            );
            when(reviewCommentRepository.countGroupedByRepositoryIds(anyCollection())).thenReturn(
                List.of(row(REPO_A, 15), row(REPO_B, 25))
            );
            when(commitRepository.countGroupedByRepositoryIds(anyCollection())).thenReturn(
                List.of(row(REPO_A, 16), row(REPO_B, 26))
            );

            Map<Long, ScmResourceCounts> counts = reader.countsByRepositoryId(List.of(REPO_A, REPO_B));

            assertThat(counts).containsOnlyKeys(REPO_A, REPO_B);
            assertThat(counts.get(REPO_A)).isEqualTo(new ScmResourceCounts(11, 12, 13, 14, 15, 16));
            assertThat(counts.get(REPO_B)).isEqualTo(new ScmResourceCounts(21, 22, 23, 24, 25, 26));
        }

        @Test
        void manyRepositories_issuesExactlySixQueriesRegardlessOfCount() {
            when(issueRepository.countIssuesGroupedByRepositoryIds(anyCollection())).thenReturn(List.of());
            when(issueRepository.countPullRequestsGroupedByRepositoryIds(anyCollection())).thenReturn(List.of());
            when(issueCommentRepository.countGroupedByRepositoryIds(anyCollection())).thenReturn(List.of());
            when(reviewRepository.countGroupedByRepositoryIds(anyCollection())).thenReturn(List.of());
            when(reviewCommentRepository.countGroupedByRepositoryIds(anyCollection())).thenReturn(List.of());
            when(commitRepository.countGroupedByRepositoryIds(anyCollection())).thenReturn(List.of());

            reader.countsByRepositoryId(List.of(REPO_A, REPO_B, REPO_C));

            // The batching contract: query count is constant in the number of repositories. Anything that
            // scales with N here is an N+1 on a page that renders every resource of every connection.
            verify(issueRepository, times(1)).countIssuesGroupedByRepositoryIds(anyCollection());
            verify(issueRepository, times(1)).countPullRequestsGroupedByRepositoryIds(anyCollection());
            verify(issueCommentRepository, times(1)).countGroupedByRepositoryIds(anyCollection());
            verify(reviewRepository, times(1)).countGroupedByRepositoryIds(anyCollection());
            verify(reviewCommentRepository, times(1)).countGroupedByRepositoryIds(anyCollection());
            verify(commitRepository, times(1)).countGroupedByRepositoryIds(anyCollection());
        }

        @Test
        void repositoryWithNoRowsAnywhere_isPresentWithZeroCounts() {
            // REPO_B is requested but appears in no query result — a freshly monitored repo.
            when(issueRepository.countIssuesGroupedByRepositoryIds(anyCollection())).thenReturn(
                List.of(row(REPO_A, 5))
            );
            when(issueRepository.countPullRequestsGroupedByRepositoryIds(anyCollection())).thenReturn(
                List.of(row(REPO_A, 2))
            );
            when(issueCommentRepository.countGroupedByRepositoryIds(anyCollection())).thenReturn(List.of());
            when(reviewRepository.countGroupedByRepositoryIds(anyCollection())).thenReturn(List.of());
            when(reviewCommentRepository.countGroupedByRepositoryIds(anyCollection())).thenReturn(List.of());
            when(commitRepository.countGroupedByRepositoryIds(anyCollection())).thenReturn(List.of());

            Map<Long, ScmResourceCounts> counts = reader.countsByRepositoryId(List.of(REPO_A, REPO_B));

            // Every requested id comes back, so callers never have to tell "absent" from "zero" — a map
            // that silently drops empty repos would make them vanish from the read model entirely.
            assertThat(counts).containsOnlyKeys(REPO_A, REPO_B);
            assertThat(counts.get(REPO_B)).isEqualTo(ScmResourceCounts.empty());
            assertThat(counts.get(REPO_B).headlineItemCount()).isZero();
        }
    }

    @Nested
    class Counts {

        @Test
        void headlineItemCount_isIssuesPlusPullRequestsOnly() {
            ScmResourceCounts counts = new ScmResourceCounts(30, 12, 700, 400, 900, 5000);

            // Comments, reviews and commits dwarf the issue/PR numbers: if any of them leaked into the
            // headline, the row would report a wildly inflated "items synced".
            assertThat(counts.headlineItemCount()).isEqualTo(42L);
        }

        @Test
        void toSyncResourceCounts_reportsSixClassesWithPerClassWatermarks() {
            Instant issuesSyncedAt = Instant.parse("2026-07-10T03:00:00Z");
            Instant pullRequestsSyncedAt = Instant.parse("2026-07-14T03:00:00Z");
            ScmResourceCounts counts = new ScmResourceCounts(30, 12, 13, 14, 15, 16);

            List<SyncResourceCount> result = counts.toSyncResourceCounts(issuesSyncedAt, pullRequestsSyncedAt);

            assertThat(result)
                .extracting(SyncResourceCount::key, SyncResourceCount::count, SyncResourceCount::lastSyncedAt)
                .containsExactly(
                    tuple(SyncResourceCount.KEY_ISSUES, 30L, issuesSyncedAt),
                    tuple(SyncResourceCount.KEY_PULL_REQUESTS, 12L, pullRequestsSyncedAt),
                    // The nested classes have no watermark column of their own. Reporting null ("not
                    // tracked") rather than a sibling's timestamp is the whole point: borrowing would
                    // assert exactly the freshness this breakdown exists to disprove.
                    tuple(SyncResourceCount.KEY_ISSUE_COMMENTS, 13L, null),
                    tuple(SyncResourceCount.KEY_REVIEWS, 14L, null),
                    tuple(SyncResourceCount.KEY_REVIEW_COMMENTS, 15L, null),
                    tuple(SyncResourceCount.KEY_COMMITS, 16L, null)
                );
            assertThat(result).allSatisfy(count -> assertThat(count.label()).isNotBlank());
        }

        @Test
        void toSyncResourceCounts_withoutWatermarks_leavesEveryLastSyncedAtNull() {
            ScmResourceCounts counts = new ScmResourceCounts(9, 3, 0, 0, 0, 0);

            List<SyncResourceCount> result = counts.toSyncResourceCounts(null, null);

            // The GitLab case: no per-class watermark is persisted at all, so no class may claim one.
            assertThat(result)
                .hasSize(6)
                .allSatisfy(count -> assertThat(count.lastSyncedAt()).isNull());
        }
    }
}
