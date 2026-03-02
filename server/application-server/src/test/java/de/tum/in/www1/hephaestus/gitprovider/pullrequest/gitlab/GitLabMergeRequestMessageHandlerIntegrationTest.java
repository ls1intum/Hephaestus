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
 * <b>Fixture data comes from real GitLab exports (gitlab.lrz.de).</b>
 * The fixtures represent 3 distinct merge requests:
 * <ul>
 *   <li>MR !3 (open/close/reopen): "Test MR for close/reopen" — author ga84xah (18024)</li>
 *   <li>MR !2 (merge/update): "Implement OAuth authentication" — author ga84xah (18024)</li>
 *   <li>MR !4 (approved/unapproved): "Draft: Work in progress feature" — approver bot (83343)</li>
 * </ul>
 * <p>
 * Note: Does NOT use @Transactional (see GitLabIssueMessageHandlerIntegrationTest for rationale).
 */
@Tag("integration")
@DisplayName("GitLab Merge Request Message Handler")
@TestPropertySource(
    properties = {
        "hephaestus.gitlab.enabled=true",
        "hephaestus.gitlab.default-server-url=https://gitlab.lrz.de",
        "hephaestus.gitlab.connect-timeout=30s",
        "hephaestus.gitlab.read-timeout=60s",
        "hephaestus.gitlab.rate-limit-delay=200ms",
        "hephaestus.gitlab.sync-page-delay=5m",
    }
)
class GitLabMergeRequestMessageHandlerIntegrationTest extends BaseIntegrationTest {

    // ==================== Common Constants ====================

    private static final long NATIVE_AUTHOR_ID = 18024L;
    private static final String FIXTURE_AUTHOR_LOGIN = "ga84xah";
    private static final String FIXTURE_ORG_LOGIN = "hephaestustest";
    private static final String FIXTURE_REPO_FULL_NAME = "hephaestustest/demo-repository";

    // ==================== MR !3 (open/close/reopen) ====================

    private static final long NATIVE_MR3_ID = 334053L;
    private static final int MR3_IID = 3;
    private static final String MR3_TITLE = "Test MR for close/reopen";
    private static final String MR3_HTML_URL =
        "https://gitlab.lrz.de/hephaestustest/demo-repository/-/merge_requests/3";
    private static final String MR3_SOURCE_BRANCH = "feature/test-close-reopen";
    private static final String MR3_TARGET_BRANCH = "main";

    // ==================== MR !2 (merge/update) ====================

    private static final long NATIVE_MR2_ID = 334047L;
    private static final int MR2_IID = 2;
    private static final String MR2_TITLE = "Implement OAuth authentication";

    // ==================== MR !4 (approved/unapproved) ====================

    private static final long NATIVE_MR4_ID = 334054L;
    private static final int MR4_IID = 4;
    private static final long NATIVE_APPROVER_ID = 83343L;

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
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR3_IID)
                    .orElseThrow();

                // Core fields
                assertThat(pr.getNativeId()).isEqualTo(NATIVE_MR3_ID);
                assertThat(pr.getNumber()).isEqualTo(MR3_IID);
                assertThat(pr.getTitle()).isEqualTo(MR3_TITLE);
                assertThat(pr.getBody()).isNull();
                assertThat(pr.getState()).isEqualTo(Issue.State.OPEN);
                assertThat(pr.getHtmlUrl()).isEqualTo(MR3_HTML_URL);

                // Branch info
                assertThat(pr.getHeadRefName()).isEqualTo(MR3_SOURCE_BRANCH);
                assertThat(pr.getBaseRefName()).isEqualTo(MR3_TARGET_BRANCH);

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
            // Create MR !3 first
            handler.handleEvent(loadPayload("merge_request.open"));
            eventListener.clear();

            // Close MR !3
            handler.handleEvent(loadPayload("merge_request.close"));

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR3_IID)
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
            // Create MR !2 via update event first
            handler.handleEvent(loadPayload("merge_request.update"));
            eventListener.clear();

            // Merge MR !2
            handler.handleEvent(loadPayload("merge_request.merge"));

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR2_IID)
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
            // Create MR !3 and close it
            handler.handleEvent(loadPayload("merge_request.open"));
            handler.handleEvent(loadPayload("merge_request.close"));
            eventListener.clear();

            // Reopen MR !3
            handler.handleEvent(loadPayload("merge_request.reopen"));

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR3_IID)
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
            // Approved event creates MR !4 via internal process() call
            handler.handleEvent(loadPayload("merge_request.approved"));

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR4_IID)
                    .orElseThrow();

                List<PullRequestReview> reviews = reviewRepository
                    .findAll()
                    .stream()
                    .filter(r -> r.getPullRequest() != null && r.getPullRequest().getId().equals(pr.getId()))
                    .toList();

                assertThat(reviews).hasSize(1);
                PullRequestReview review = reviews.get(0);
                assertThat(review.getState()).isEqualTo(PullRequestReview.State.APPROVED);
                assertThat(review.getId()).isNegative();

                long expectedId = GitLabMergeRequestProcessor.generateApprovalReviewId(
                    NATIVE_MR4_ID,
                    NATIVE_APPROVER_ID
                );
                assertThat(review.getId()).isEqualTo(expectedId);
            });

            assertThat(eventListener.getReviewSubmittedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("deletes review on 'unapproved' event")
        void unapproveMergeRequest_deletesReview() throws Exception {
            // Create MR !4 and approve it
            handler.handleEvent(loadPayload("merge_request.approved"));
            eventListener.clear();

            // Unapprove MR !4
            handler.handleEvent(loadPayload("merge_request.unapproved"));

            transactionTemplate.executeWithoutResult(status -> {
                long reviewId = GitLabMergeRequestProcessor.generateApprovalReviewId(
                    NATIVE_MR4_ID, NATIVE_APPROVER_ID
                );
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
        @DisplayName("full lifecycle: open -> close -> reopen (MR !3)")
        void fullLifecycle_openCloseReopen() throws Exception {
            // Open MR !3
            handler.handleEvent(loadPayload("merge_request.open"));
            assertThat(eventListener.getCreatedEvents()).hasSize(1);

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR3_IID)
                    .orElseThrow();
                assertThat(pr.getState()).isEqualTo(Issue.State.OPEN);
            });

            // Close MR !3
            handler.handleEvent(loadPayload("merge_request.close"));
            assertThat(eventListener.getClosedEvents()).hasSize(1);
            assertThat(eventListener.getClosedEvents().get(0).wasMerged()).isFalse();

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR3_IID)
                    .orElseThrow();
                assertThat(pr.getState()).isEqualTo(Issue.State.CLOSED);
            });

            // Reopen MR !3
            handler.handleEvent(loadPayload("merge_request.reopen"));
            assertThat(eventListener.getReopenedEvents()).hasSize(1);

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR3_IID)
                    .orElseThrow();
                assertThat(pr.getState()).isEqualTo(Issue.State.OPEN);
            });
        }

        @Test
        @DisplayName("full lifecycle: approve -> unapprove (MR !4)")
        void fullLifecycle_approveUnapprove() throws Exception {
            // Approve MR !4 (also creates it)
            handler.handleEvent(loadPayload("merge_request.approved"));
            assertThat(eventListener.getReviewSubmittedEvents()).hasSize(1);

            transactionTemplate.executeWithoutResult(status -> {
                long reviewId = GitLabMergeRequestProcessor.generateApprovalReviewId(
                    NATIVE_MR4_ID, NATIVE_APPROVER_ID
                );
                assertThat(reviewRepository.findById(reviewId)).isPresent();
            });

            // Unapprove MR !4
            handler.handleEvent(loadPayload("merge_request.unapproved"));
            assertThat(eventListener.getReviewDismissedEvents()).hasSize(1);

            transactionTemplate.executeWithoutResult(status -> {
                long reviewId = GitLabMergeRequestProcessor.generateApprovalReviewId(
                    NATIVE_MR4_ID, NATIVE_APPROVER_ID
                );
                assertThat(reviewRepository.findById(reviewId)).isEmpty();
            });
        }

        @Test
        @DisplayName("full lifecycle: update -> merge (MR !2)")
        void fullLifecycle_updateMerge() throws Exception {
            // Create MR !2 via update
            handler.handleEvent(loadPayload("merge_request.update"));
            assertThat(eventListener.getCreatedEvents()).hasSize(1);

            // Merge MR !2
            handler.handleEvent(loadPayload("merge_request.merge"));
            assertThat(eventListener.getMergedEvents()).hasSize(1);

            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR2_IID)
                    .orElseThrow();
                assertThat(pr.getState()).isEqualTo(Issue.State.MERGED);
                assertThat(pr.isMerged()).isTrue();
                assertThat(pr.getTitle()).isEqualTo(MR2_TITLE);
            });
        }

        @Test
        @DisplayName("IID namespace isolation — Issue #3 and MR !3 coexist in same repository")
        void iidNamespaceIsolation_issueAndMrCoexist() throws Exception {
            // Create an Issue with number=3 in the same repository
            transactionTemplate.executeWithoutResult(status -> {
                issueRepository.upsertCore(
                    /* nativeId */ 888888L,
                    /* providerId */ savedProvider.getId(),
                    /* number */ MR3_IID, // Same number as MR !3
                    /* title */ "Issue with same IID",
                    /* body */ "This is an issue with the same IID as the MR",
                    /* state */ "OPEN",
                    /* stateReason */ null,
                    /* htmlUrl */ "https://gitlab.lrz.de/hephaestustest/demo-repository/-/issues/3",
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

            // Now create MR !3
            handler.handleEvent(loadPayload("merge_request.open"));

            // Both should exist independently
            transactionTemplate.executeWithoutResult(status -> {
                // Issue #3 exists as Issue type
                Issue issue = issueRepository.findByRepositoryIdAndNumber(savedRepo.getId(), MR3_IID).orElse(null);
                assertThat(issue).isNotNull();
                assertThat(issue.getTitle()).isEqualTo("Issue with same IID");
                assertThat(issue.isPullRequest()).isFalse();

                // MR !3 exists as PullRequest type
                PullRequest pr = pullRequestRepository
                    .findByRepositoryIdAndNumber(savedRepo.getId(), MR3_IID)
                    .orElse(null);
                assertThat(pr).isNotNull();
                assertThat(pr.getTitle()).isEqualTo(MR3_TITLE);
                assertThat(pr.isPullRequest()).isTrue();
            });
        }
    }

    // ==================== Domain Events ====================

    @Nested
    @DisplayName("Domain Events")
    class DomainEvents {

        @Test
        @DisplayName("publishes correct domain events for MR !3 lifecycle")
        void domainEvents_mr3Lifecycle() throws Exception {
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
        }

        @Test
        @DisplayName("publishes correct domain events for MR !2 merge")
        void domainEvents_mr2Merge() throws Exception {
            // Create via update
            handler.handleEvent(loadPayload("merge_request.update"));
            eventListener.clear();

            // Merge -> PullRequestClosed(wasMerged=true) + PullRequestMerged
            handler.handleEvent(loadPayload("merge_request.merge"));
            assertThat(eventListener.getClosedEvents()).hasSize(1);
            assertThat(eventListener.getClosedEvents().get(0).wasMerged()).isTrue();
            assertThat(eventListener.getMergedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("publishes correct domain events for MR !4 approval")
        void domainEvents_mr4Approval() throws Exception {
            // Approve -> ReviewSubmitted
            handler.handleEvent(loadPayload("merge_request.approved"));
            assertThat(eventListener.getReviewSubmittedEvents()).hasSize(1);

            eventListener.clear();

            // Unapprove -> ReviewDismissed
            handler.handleEvent(loadPayload("merge_request.unapproved"));
            assertThat(eventListener.getReviewDismissedEvents()).hasSize(1);
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
            .findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.lrz.de")
            .orElseGet(() ->
                gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, "https://gitlab.lrz.de"))
            );

        Organization org = new Organization();
        org.setNativeId(1L);
        org.setLogin(FIXTURE_ORG_LOGIN);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("HephaestusTest");
        org.setAvatarUrl("");
        org.setHtmlUrl("https://gitlab.lrz.de/hephaestustest");
        org.setProvider(savedProvider);
        org = organizationRepository.save(org);

        Repository repo = new Repository();
        repo.setNativeId(246765L);
        repo.setName("demo-repository");
        repo.setNameWithOwner(FIXTURE_REPO_FULL_NAME);
        repo.setHtmlUrl("https://gitlab.lrz.de/hephaestustest/demo-repository");
        repo.setVisibility(Repository.Visibility.PRIVATE);
        repo.setDefaultBranch("main");
        repo.setCreatedAt(Instant.now());
        repo.setUpdatedAt(Instant.now());
        repo.setPushedAt(Instant.now());
        repo.setOrganization(org);
        repo.setProvider(savedProvider);
        savedRepo = repositoryRepository.save(repo);

        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug("hephaestus-test-gitlab");
        workspace.setDisplayName("HephaestusTest GitLab");
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
