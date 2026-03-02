package de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab.dto.GitLabMergeRequestEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for GitLabMergeRequestMessageHandler.
 * <p>
 * Tests the full webhook handling flow: JSON fixtures -> DTO -> handler -> processor -> DB.
 * <p>
 * <b>Fixture values (merge_request.open.json — MR IID #5):</b>
 * <ul>
 *   <li>Native ID: 999555 (stored as nativeId; synthetic auto-generated PK for id)</li>
 *   <li>IID: 5</li>
 *   <li>Title: "Add awesome feature"</li>
 *   <li>State: opened -> OPEN</li>
 *   <li>Source branch: feature/awesome-feature</li>
 *   <li>Target branch: main</li>
 *   <li>Author: testuser (native ID 12345)</li>
 *   <li>Approver: reviewer1 (native ID 11111)</li>
 *   <li>Provider: GITLAB</li>
 * </ul>
 * <p>
 * Note: Does NOT use @Transactional (see GitLabIssueMessageHandlerIntegrationTest for rationale).
 */
@Tag("integration")
@DisplayName("GitLab Merge Request Message Handler")
@TestPropertySource(
    properties = {
        "hephaestus.gitlab.enabled=true",
        "hephaestus.gitlab.default-server-url=https://gitlab.com",
        "hephaestus.gitlab.connect-timeout=30s",
        "hephaestus.gitlab.read-timeout=60s",
        "hephaestus.gitlab.rate-limit-delay=200ms",
        "hephaestus.gitlab.sync-page-delay=5m",
    }
)
class GitLabMergeRequestMessageHandlerIntegrationTest extends BaseIntegrationTest {

    // Native IDs from GitLab fixtures (positive, raw values)
    private static final long NATIVE_MR_ID = 999555L;
    private static final int MR_IID = 5;
    private static final long NATIVE_AUTHOR_ID = 12345L;
    private static final long NATIVE_APPROVER_ID = 11111L;

    // Fixture values
    private static final String FIXTURE_MR_TITLE = "Add awesome feature";
    private static final String FIXTURE_MR_BODY = "This MR adds an awesome feature";
    private static final String FIXTURE_MR_HTML_URL = "https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5";
    private static final String FIXTURE_AUTHOR_LOGIN = "testuser";
    private static final String FIXTURE_APPROVER_LOGIN = "reviewer1";
    private static final String FIXTURE_SOURCE_BRANCH = "feature/awesome-feature";
    private static final String FIXTURE_TARGET_BRANCH = "main";

    // Repository/org setup
    private static final String FIXTURE_ORG_LOGIN = "gitlab-org";
    private static final String FIXTURE_REPO_FULL_NAME = "gitlab-org/gitlab";

    @Autowired
    private GitLabMergeRequestMessageHandler handler;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private PullRequestReviewRepository reviewRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private GitLabMergeRequestTestEventListener eventListener;

    private Repository savedRepo;
    private GitProvider savedProvider;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    // ==================== Event Type ====================

    @Nested
    @DisplayName("Event Type")
    class EventType {

        @Test
        @DisplayName("returns MERGE_REQUEST as event type")
        void returnsCorrectEventType() {
            assertThat(handler.getEventType()).isEqualTo(GitLabEventType.MERGE_REQUEST);
        }
    }

    // ==================== Basic Lifecycle ====================

    @Nested
    @DisplayName("Basic Lifecycle Events")
    class BasicLifecycleEvents {

        @Test
        @DisplayName("persists pull request with all fields on 'open' event")
        void openMergeRequest_createsPullRequest() throws Exception {
            GitLabMergeRequestEventDTO event = loadPayload("merge_request.open");

            handler.handleEvent(event);

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR_IID)
                    .orElseThrow();

                // Core fields
                assertThat(pr.getNativeId()).isEqualTo(NATIVE_MR_ID);
                assertThat(pr.getNumber()).isEqualTo(MR_IID);
                assertThat(pr.getTitle()).isEqualTo(FIXTURE_MR_TITLE);
                assertThat(pr.getBody()).isEqualTo(FIXTURE_MR_BODY);
                assertThat(pr.getState()).isEqualTo(Issue.State.OPEN);
                assertThat(pr.getHtmlUrl()).isEqualTo(FIXTURE_MR_HTML_URL);

                // Branch info
                assertThat(pr.getHeadRefName()).isEqualTo(FIXTURE_SOURCE_BRANCH);
                assertThat(pr.getBaseRefName()).isEqualTo(FIXTURE_TARGET_BRANCH);

                // Provider
                assertThat(pr.getProvider().getType()).isEqualTo(GitProviderType.GITLAB);

                // Timestamps
                assertThat(pr.getCreatedAt()).isNotNull();
                assertThat(pr.getUpdatedAt()).isNotNull();

                // Repository
                assertThat(pr.getRepository()).isNotNull();
                assertThat(pr.getRepository().getId()).isEqualTo(savedRepo.getId());

                // Author
                assertThat(pr.getAuthor()).isNotNull();
                assertThat(pr.getAuthor().getNativeId()).isEqualTo(NATIVE_AUTHOR_ID);
                assertThat(pr.getAuthor().getLogin()).isEqualTo(FIXTURE_AUTHOR_LOGIN);

                // PR-specific
                assertThat(pr.isPullRequest()).isTrue();
            });

            // Domain event
            assertThat(eventListener.getCreatedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("closes merge request on 'close' event")
        void closeMergeRequest_setsStateToClosed() throws Exception {
            // Create first
            handler.handleEvent(loadPayload("merge_request.open"));
            eventListener.clear();

            // Close
            handler.handleEvent(loadPayload("merge_request.close"));

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR_IID)
                    .orElseThrow();
                assertThat(pr.getState()).isEqualTo(Issue.State.CLOSED);
                assertThat(pr.getClosedAt()).isNotNull();
            });

            assertThat(eventListener.getClosedEvents()).hasSize(1);
            assertThat(eventListener.getClosedEvents().get(0).wasMerged()).isFalse();
        }

        @Test
        @DisplayName("merges merge request on 'merge' event")
        void mergeMergeRequest_setsStateToMerged() throws Exception {
            // Create first
            handler.handleEvent(loadPayload("merge_request.open"));
            eventListener.clear();

            // Merge
            handler.handleEvent(loadPayload("merge_request.merge"));

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR_IID)
                    .orElseThrow();
                assertThat(pr.getState()).isEqualTo(Issue.State.MERGED);
                assertThat(pr.isMerged()).isTrue();
                assertThat(pr.getMergedAt()).isNotNull();
            });

            assertThat(eventListener.getClosedEvents()).hasSize(1);
            assertThat(eventListener.getClosedEvents().get(0).wasMerged()).isTrue();
            assertThat(eventListener.getMergedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("reopens merge request on 'reopen' event")
        void reopenMergeRequest_setsStateToOpen() throws Exception {
            // Create and close
            handler.handleEvent(loadPayload("merge_request.open"));
            handler.handleEvent(loadPayload("merge_request.close"));
            eventListener.clear();

            // Reopen
            handler.handleEvent(loadPayload("merge_request.reopen"));

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR_IID)
                    .orElseThrow();
                assertThat(pr.getState()).isEqualTo(Issue.State.OPEN);
            });

            assertThat(eventListener.getReopenedEvents()).hasSize(1);
        }
    }

    // ==================== Approval Events ====================

    @Nested
    @DisplayName("Approval Events")
    class ApprovalEvents {

        @Test
        @DisplayName("creates review on 'approved' event")
        void approveMergeRequest_createsReview() throws Exception {
            // Create first
            handler.handleEvent(loadPayload("merge_request.open"));
            eventListener.clear();

            // Approve
            handler.handleEvent(loadPayload("merge_request.approved"));

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR_IID)
                    .orElseThrow();

                List<PullRequestReview> reviews = reviewRepository
                    .findAll()
                    .stream()
                    .filter(r -> r.getPullRequest() != null && r.getPullRequest().getId().equals(pr.getId()))
                    .toList();

                assertThat(reviews).hasSize(1);
                PullRequestReview review = reviews.get(0);
                assertThat(review.getState()).isEqualTo(PullRequestReview.State.APPROVED);
                // Deterministic negative ID: -(mrNativeId << 32 | userNativeId & 0xFFFFFFFFL)
                assertThat(review.getId()).isNegative();

                long expectedId = GitLabMergeRequestProcessor.generateApprovalReviewId(
                    NATIVE_MR_ID,
                    NATIVE_APPROVER_ID
                );
                assertThat(review.getId()).isEqualTo(expectedId);
            });

            assertThat(eventListener.getReviewSubmittedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("deletes review on 'unapproved' event")
        void unapproveMergeRequest_deletesReview() throws Exception {
            // Create and approve
            handler.handleEvent(loadPayload("merge_request.open"));
            handler.handleEvent(loadPayload("merge_request.approved"));
            eventListener.clear();

            // Unapprove
            handler.handleEvent(loadPayload("merge_request.unapproved"));

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR_IID)
                    .orElseThrow();

                long reviewId = GitLabMergeRequestProcessor.generateApprovalReviewId(NATIVE_MR_ID, NATIVE_APPROVER_ID);
                assertThat(reviewRepository.findById(reviewId)).isEmpty();
            });

            assertThat(eventListener.getReviewDismissedEvents()).hasSize(1);
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("handles missing repository gracefully")
        void shouldHandleMissingRepositoryGracefully() throws Exception {
            repositoryRepository.deleteAll();

            GitLabMergeRequestEventDTO event = loadPayload("merge_request.open");

            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();
            assertThat(pullRequestRepository.count()).isZero();
        }

        @Test
        @DisplayName("is idempotent — processing same event twice")
        void idempotency_processSameEventTwice() throws Exception {
            GitLabMergeRequestEventDTO event = loadPayload("merge_request.open");

            handler.handleEvent(event);
            long countAfterFirst = pullRequestRepository.count();

            handler.handleEvent(event);

            assertThat(pullRequestRepository.count()).isEqualTo(countAfterFirst);
        }

        @Test
        @DisplayName("full lifecycle: open -> approved -> merge")
        void fullLifecycle_openApproveAndMerge() throws Exception {
            // Open
            handler.handleEvent(loadPayload("merge_request.open"));
            assertThat(eventListener.getCreatedEvents()).hasSize(1);

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR_IID)
                    .orElseThrow();
                assertThat(pr.getState()).isEqualTo(Issue.State.OPEN);
            });

            // Approve
            handler.handleEvent(loadPayload("merge_request.approved"));
            assertThat(eventListener.getReviewSubmittedEvents()).hasSize(1);

            transactionTemplate.executeWithoutResult(status -> {
                long reviewId = GitLabMergeRequestProcessor.generateApprovalReviewId(NATIVE_MR_ID, NATIVE_APPROVER_ID);
                assertThat(reviewRepository.findById(reviewId)).isPresent();
            });

            // Merge
            handler.handleEvent(loadPayload("merge_request.merge"));

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR_IID)
                    .orElseThrow();
                assertThat(pr.getState()).isEqualTo(Issue.State.MERGED);
                assertThat(pr.isMerged()).isTrue();

                // Review should still exist after merge
                long reviewId = GitLabMergeRequestProcessor.generateApprovalReviewId(NATIVE_MR_ID, NATIVE_APPROVER_ID);
                assertThat(reviewRepository.findById(reviewId)).isPresent();
            });

            // Verify domain events
            assertThat(eventListener.getCreatedEvents()).hasSize(1);
            assertThat(eventListener.getClosedEvents()).hasSize(1);
            assertThat(eventListener.getClosedEvents().get(0).wasMerged()).isTrue();
            assertThat(eventListener.getMergedEvents()).hasSize(1);
            assertThat(eventListener.getReviewSubmittedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("IID namespace isolation — Issue #5 and MR #5 coexist in same repository")
        void iidNamespaceIsolation_issueAndMrCoexist() throws Exception {
            // Create an Issue with number=5 in the same repository
            transactionTemplate.executeWithoutResult(status -> {
                issueRepository.upsertCore(
                    /* nativeId */ 888888L,
                    /* providerId */ savedProvider.getId(),
                    /* number */ MR_IID, // Same number as the MR
                    /* title */ "Issue with same IID",
                    /* body */ "This is an issue with the same IID as the MR",
                    /* state */ "OPEN",
                    /* stateReason */ null,
                    /* htmlUrl */ "https://gitlab.com/gitlab-org/gitlab/-/issues/5",
                    /* isLocked */ false,
                    /* closedAt */ null,
                    /* commentsCount */ 0,
                    /* lastSyncAt */ Instant.now(),
                    /* createdAt */ Instant.now(),
                    /* updatedAt */ Instant.now(),
                    /* authorId */ null,
                    /* repositoryId */ savedRepo.getId(),
                    /* milestoneId */ null,
                    /* issueTypeId */ null,
                    /* parentIssueId */ null,
                    /* subIssuesTotal */ null,
                    /* subIssuesCompleted */ null,
                    /* subIssuesPercentCompleted */ null
                );
            });

            // Now create MR with iid=5
            handler.handleEvent(loadPayload("merge_request.open"));

            // Both should exist independently
            transactionTemplate.executeWithoutResult(status -> {
                // Issue #5 exists as Issue type
                Issue issue = issueRepository.findByRepositoryIdAndNumber(savedRepo.getId(), MR_IID).orElse(null);
                assertThat(issue).isNotNull();
                assertThat(issue.getTitle()).isEqualTo("Issue with same IID");
                assertThat(issue.isPullRequest()).isFalse();

                // MR #5 exists as PullRequest type
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR_IID)
                    .orElse(null);
                assertThat(pr).isNotNull();
                assertThat(pr.getTitle()).isEqualTo(FIXTURE_MR_TITLE);
                assertThat(pr.isPullRequest()).isTrue();
            });
        }
    }

    // ==================== Domain Events ====================

    @Nested
    @DisplayName("Domain Events")
    class DomainEvents {

        @Test
        @DisplayName("publishes correct domain events for each action")
        void domainEvents_publishedCorrectly() throws Exception {
            // Open -> PullRequestCreated
            handler.handleEvent(loadPayload("merge_request.open"));
            assertThat(eventListener.getCreatedEvents()).hasSize(1);

            // Close -> PullRequestClosed(wasMerged=false)
            handler.handleEvent(loadPayload("merge_request.close"));
            assertThat(eventListener.getClosedEvents()).hasSize(1);
            assertThat(eventListener.getClosedEvents().get(0).wasMerged()).isFalse();

            eventListener.clear();

            // Reopen -> PullRequestReopened
            handler.handleEvent(loadPayload("merge_request.reopen"));
            assertThat(eventListener.getReopenedEvents()).hasSize(1);

            eventListener.clear();

            // Approve -> ReviewSubmitted
            handler.handleEvent(loadPayload("merge_request.approved"));
            assertThat(eventListener.getReviewSubmittedEvents()).hasSize(1);

            eventListener.clear();

            // Merge -> PullRequestClosed(wasMerged=true) + PullRequestMerged
            handler.handleEvent(loadPayload("merge_request.merge"));
            assertThat(eventListener.getClosedEvents()).hasSize(1);
            assertThat(eventListener.getClosedEvents().get(0).wasMerged()).isTrue();
            assertThat(eventListener.getMergedEvents()).hasSize(1);
        }
    }

    // ==================== Author Resolution ====================

    @Nested
    @DisplayName("Entity Resolution")
    class EntityResolution {

        @Test
        @DisplayName("creates author with native ID and GITLAB provider")
        void shouldCreateAuthorWithCorrectFields() throws Exception {
            assertThat(userRepository.count()).isZero();

            handler.handleEvent(loadPayload("merge_request.open"));

            transactionTemplate.executeWithoutResult(status -> {
                var author = userRepository
                    .findByNativeIdAndProviderId(NATIVE_AUTHOR_ID, savedProvider.getId())
                    .orElseThrow();
                assertThat(author.getLogin()).isEqualTo(FIXTURE_AUTHOR_LOGIN);
                assertThat(author.getProvider().getType()).isEqualTo(GitProviderType.GITLAB);
            });
        }
    }

    // ==================== Helpers ====================

    private GitLabMergeRequestEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("gitlab/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitLabMergeRequestEventDTO.class);
    }

    private void setupTestData() {
        savedProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, "https://gitlab.com")));

        Organization org = new Organization();
        org.setNativeId(1L);
        org.setLogin(FIXTURE_ORG_LOGIN);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("GitLab Org");
        org.setAvatarUrl("");
        org.setHtmlUrl("https://gitlab.com/gitlab-org");
        org.setProvider(savedProvider);
        org = organizationRepository.save(org);

        Repository repo = new Repository();
        repo.setNativeId(278964L);
        repo.setName("gitlab");
        repo.setNameWithOwner(FIXTURE_REPO_FULL_NAME);
        repo.setHtmlUrl("https://gitlab.com/gitlab-org/gitlab");
        repo.setVisibility(Repository.Visibility.PUBLIC);
        repo.setDefaultBranch("main");
        repo.setCreatedAt(Instant.now());
        repo.setUpdatedAt(Instant.now());
        repo.setPushedAt(Instant.now());
        repo.setOrganization(org);
        repo.setProvider(savedProvider);
        savedRepo = repositoryRepository.save(repo);

        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug("gitlab-org-test");
        workspace.setDisplayName("GitLab Org Test");
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        workspace.setIsPubliclyViewable(true);
        workspace.setOrganization(org);
        workspace.setAccountLogin(FIXTURE_ORG_LOGIN);
        workspace.setAccountType(AccountType.ORG);
        workspaceRepository.save(workspace);
    }

    // ==================== Test Event Listener ====================

    @Component
    static class GitLabMergeRequestTestEventListener {

        private final List<DomainEvent.PullRequestCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.PullRequestClosed> closedEvents = new ArrayList<>();
        private final List<DomainEvent.PullRequestReopened> reopenedEvents = new ArrayList<>();
        private final List<DomainEvent.PullRequestMerged> mergedEvents = new ArrayList<>();
        private final List<DomainEvent.ReviewSubmitted> reviewSubmittedEvents = new ArrayList<>();
        private final List<DomainEvent.ReviewDismissed> reviewDismissedEvents = new ArrayList<>();

        @EventListener
        public void onCreated(DomainEvent.PullRequestCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onClosed(DomainEvent.PullRequestClosed event) {
            closedEvents.add(event);
        }

        @EventListener
        public void onReopened(DomainEvent.PullRequestReopened event) {
            reopenedEvents.add(event);
        }

        @EventListener
        public void onMerged(DomainEvent.PullRequestMerged event) {
            mergedEvents.add(event);
        }

        @EventListener
        public void onReviewSubmitted(DomainEvent.ReviewSubmitted event) {
            reviewSubmittedEvents.add(event);
        }

        @EventListener
        public void onReviewDismissed(DomainEvent.ReviewDismissed event) {
            reviewDismissedEvents.add(event);
        }

        public List<DomainEvent.PullRequestCreated> getCreatedEvents() {
            return new ArrayList<>(createdEvents);
        }

        public List<DomainEvent.PullRequestClosed> getClosedEvents() {
            return new ArrayList<>(closedEvents);
        }

        public List<DomainEvent.PullRequestReopened> getReopenedEvents() {
            return new ArrayList<>(reopenedEvents);
        }

        public List<DomainEvent.PullRequestMerged> getMergedEvents() {
            return new ArrayList<>(mergedEvents);
        }

        public List<DomainEvent.ReviewSubmitted> getReviewSubmittedEvents() {
            return new ArrayList<>(reviewSubmittedEvents);
        }

        public List<DomainEvent.ReviewDismissed> getReviewDismissedEvents() {
            return new ArrayList<>(reviewDismissedEvents);
        }

        public void clear() {
            createdEvents.clear();
            closedEvents.clear();
            reopenedEvents.clear();
            mergedEvents.clear();
            reviewSubmittedEvents.clear();
            reviewDismissedEvents.clear();
        }
    }
}
