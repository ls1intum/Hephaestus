package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.tum.cit.aet.hephaestus.agent.context.providers.mentor.MentorContextQueryRepository;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * The two halves of the tombstone decision, on one pull request and one issue that are tombstoned and
 * then handed back by an ordinary upsert.
 *
 * <p>Heph must not describe work that no longer exists upstream, and a review somebody asks for must be
 * refused on it by name — so the mentor's queries and the project inventory drop the tombstoned row, and
 * the request path answers {@link SignalStateReason#ARTIFACT_GONE}. Everything the drift tombstone
 * promises to be able to undo must still see it: the upsert that resurrects it, and the gate loader the
 * pending-signal resubmitters read, which reports a missing row as {@code ARTIFACT_GONE} and retires the
 * occasion for good.
 *
 * <p>Both kinds are exercised because {@code Issue} and {@code PullRequest} share one table and the
 * issue side additionally has to hold its {@code TYPE(i) = Issue} discriminator.
 *
 * <p>Runs against a live Postgres because most of the behaviour is a {@code WHERE} clause; the unit
 * tier mocks these repositories and would pass either way.
 */
class UpstreamDeletedWorkReadScopeIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final int PR_NUMBER = 42;
    private static final int ISSUE_NUMBER = 43;
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20);

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private MentorContextQueryRepository mentorContextQueryRepository;

    @Autowired
    private ManualReviewRequests manualReviewRequests;

    private Workspace workspace;
    private User author;
    private Repository repository;
    private long pullRequestId;
    private long issueId;

    @BeforeEach
    void seedMonitoredWork() {
        User owner = persistUser("read-scope-owner");
        workspace = createWorkspace("read-scope-ws", "Read Scope WS", "read-scope-org", AccountType.ORG, owner);
        author = persistUser("read-scope-author");

        repository = new Repository();
        repository.setNativeId(9301L);
        repository.setProvider(ensureGitHubProvider());
        repository.setName("widgets");
        repository.setNameWithOwner("read-scope-org/widgets");
        repository.setHtmlUrl("https://github.com/read-scope-org/widgets");
        repository.setDefaultBranch("main");
        repository = repositoryRepository.save(repository);

        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setWorkspace(workspace);
        monitor.setNameWithOwner(repository.getNameWithOwner());
        repositoryToMonitorRepository.save(monitor);

        upsertPullRequest();
        upsertIssue();
        pullRequestId = pullRequestRepository
                .findByRepositoryIdAndNumber(repository.getId(), PR_NUMBER)
                .orElseThrow()
                .getId();
        issueId = issueRepository
                .findByRepositoryIdAndNumber(repository.getId(), ISSUE_NUMBER)
                .orElseThrow()
                .getId();
    }

    @Test
    void aTombstonedPullRequestLeavesTheMentorsContextAndComesBackWhenUpstreamHandsItOver() {
        assertThat(mentorAuthoredPullRequestIds()).contains(pullRequestId);
        assertThat(pullRequestInventoryIds()).contains(pullRequestId);

        tombstonePullRequest();

        assertThat(mentorAuthoredPullRequestIds())
                .as("Heph would otherwise cite a pull request the developer cannot open")
                .doesNotContain(pullRequestId);
        assertThat(pullRequestInventoryIds()).doesNotContain(pullRequestId);

        // Upstream hands the pull request back — a sweep that tombstoned on a truncated listing heals here.
        upsertPullRequest();

        assertThat(mentorAuthoredPullRequestIds()).contains(pullRequestId);
        assertThat(pullRequestInventoryIds()).contains(pullRequestId);
    }

    @Test
    void aTombstonedIssueLeavesTheProjectInventoryAndComesBackWhenUpstreamHandsItOver() {
        assertThat(issueInventoryIds()).contains(issueId);

        tombstoneIssue();

        assertThat(issueInventoryIds()).doesNotContain(issueId);

        upsertIssue();

        assertThat(issueInventoryIds()).contains(issueId);
    }

    /**
     * The refusal is the reason itself, answered to the person who asked, rather than a status code
     * saying their button is broken: this workspace does monitor the pull request, so the question is
     * not one of standing.
     */
    @Test
    void askingForAReviewOfATombstonedPullRequestIsRefusedAsArtifactGone() {
        tombstonePullRequest();

        ManualReviewOutcome outcome =
                manualReviewRequests.requestPullRequestReview(workspace, gateLoadedPullRequest(), List.of(author));

        assertThat(outcome.status()).isEqualTo(ManualReviewOutcome.Status.REFUSED);
        assertThat(outcome.reason()).isEqualTo(SignalStateReason.ARTIFACT_GONE);

        upsertPullRequest();

        assertThat(manualReviewRequests
                        .requestPullRequestReview(workspace, gateLoadedPullRequest(), List.of(author))
                        .reason())
                .as("a resurrected pull request is back to whatever the gate makes of it")
                .isNotEqualTo(SignalStateReason.ARTIFACT_GONE);
    }

    @Test
    void askingForAReviewOfATombstonedIssueIsRefusedAsArtifactGone() {
        tombstoneIssue();

        ManualReviewOutcome outcome =
                manualReviewRequests.requestIssueReview(workspace, gateLoadedIssue(), List.of(author));

        assertThat(outcome.status()).isEqualTo(ManualReviewOutcome.Status.REFUSED);
        assertThat(outcome.reason()).isEqualTo(SignalStateReason.ARTIFACT_GONE);
    }

    /**
     * A pending signal re-offered while the row is tombstoned must not be retired: the resubmitters
     * read this loader and record {@code SignalStateReason.ARTIFACT_GONE} — a terminal
     * {@code LAPSED} — for anything it cannot find, and nothing would re-open the occasion once the
     * next ordinary sync brought the pull request back.
     */
    @Test
    void theGateLoaderAndTheUpsertLookupStillSeeATombstonedPullRequest() {
        tombstonePullRequest();

        assertThat(pullRequestRepository.findByIdWithAllForGate(pullRequestId))
                .get()
                .extracting(PullRequest::getId)
                .isEqualTo(pullRequestId);
        assertThat(pullRequestRepository.findByRepositoryIdAndNumber(repository.getId(), PR_NUMBER))
                .get()
                .extracting(PullRequest::getId)
                .isEqualTo(pullRequestId);
    }

    private void tombstonePullRequest() {
        assertThat(issueRepository.tombstonePullRequestsByRepositoryIdAndNumbers(
                        repository.getId(), List.of(PR_NUMBER), Instant.now()))
                .isEqualTo(1);
    }

    private void tombstoneIssue() {
        assertThat(issueRepository.tombstoneIssuesByRepositoryIdAndNumbers(
                        repository.getId(), List.of(ISSUE_NUMBER), Instant.now()))
                .isEqualTo(1);
    }

    private List<Long> mentorAuthoredPullRequestIds() {
        return mentorContextQueryRepository
                .findRecentAuthoredPullRequests(workspace.getId(), author.getId(), FIRST_PAGE)
                .stream()
                .map(PullRequest::getId)
                .toList();
    }

    private List<Long> pullRequestInventoryIds() {
        return pullRequestRepository.findPullRequestInventoryByRepositoryId(repository.getId(), FIRST_PAGE).stream()
                .map(PullRequest::getId)
                .toList();
    }

    private List<Long> issueInventoryIds() {
        return issueRepository.findIssueInventoryByRepositoryId(repository.getId(), FIRST_PAGE).stream()
                .map(Issue::getId)
                .toList();
    }

    /** The association graph a request needs: standing is judged on the author and the assignees. */
    private PullRequest gateLoadedPullRequest() {
        return pullRequestRepository.findByIdWithAllForGate(pullRequestId).orElseThrow();
    }

    private Issue gateLoadedIssue() {
        return issueRepository.findByIdWithRepositoryAndAssignees(issueId).orElseThrow();
    }

    private void upsertPullRequest() {
        Instant now = Instant.now();
        pullRequestRepository.upsertCore(
                9400L + PR_NUMBER,
                providerId(),
                PR_NUMBER,
                "A change worth reviewing",
                "Body",
                "OPEN",
                null,
                "https://github.com/" + repository.getNameWithOwner() + "/pull/" + PR_NUMBER,
                false,
                null,
                0,
                now,
                now,
                now,
                author.getId(),
                repository.getId(),
                null,
                null,
                false,
                false,
                1,
                10,
                5,
                3,
                null,
                null,
                null,
                "feature/branch",
                "main",
                "headsha",
                "basesha",
                null,
                null);
    }

    private void upsertIssue() {
        Instant now = Instant.now();
        issueRepository.upsertCore(
                9500L + ISSUE_NUMBER,
                providerId(),
                ISSUE_NUMBER,
                "Something worth discussing",
                "Body",
                "OPEN",
                null,
                "https://github.com/" + repository.getNameWithOwner() + "/issues/" + ISSUE_NUMBER,
                false,
                null,
                0,
                now,
                now,
                now,
                author.getId(),
                repository.getId(),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private Long providerId() {
        Long providerId = repository.getProvider().getId();
        assertNotNull(providerId);
        return providerId;
    }
}
