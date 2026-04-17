package de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabUserLookup;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookLabel;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookUser;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RepositoryScopeFilter;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab.dto.GitLabMergeRequestEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.gitlab.GitLabUserService;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

@Tag("unit")
@DisplayName("GitLabMergeRequestProcessor")
class GitLabMergeRequestProcessorTest extends BaseUnitTest {

    private static final long REPO_ID = 1L;
    private static final long RAW_MR_ID = 999555L;
    private static final long ENTITY_MR_ID = 100L;
    private static final int MR_IID = 5;
    private static final long RAW_USER_ID = 12345L;
    private static final long ENTITY_USER_ID = 200L;
    private static final Long PROVIDER_ID = 2L;
    private static final long RAW_APPROVER_ID = 11111L;
    private static final long ENTITY_APPROVER_ID = 300L;

    @Mock
    private GitLabUserService gitLabUserService;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private PullRequestReviewRepository reviewRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private ScopeIdResolver scopeIdResolver;

    @Mock
    private RepositoryScopeFilter repositoryScopeFilter;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private GitLabMergeRequestProcessor processor;
    private Repository testRepo;
    private GitProvider gitLabProvider;

    @BeforeEach
    void setUp() {
        GitLabProperties properties = new GitLabProperties(
            "https://gitlab.com",
            Duration.ofSeconds(30),
            Duration.ofSeconds(60),
            Duration.ofMillis(200),
            Duration.ofMinutes(5)
        );

        processor = new GitLabMergeRequestProcessor(
            gitLabUserService,
            pullRequestRepository,
            reviewRepository,
            milestoneRepository,
            userRepository,
            labelRepository,
            repositoryRepository,
            scopeIdResolver,
            repositoryScopeFilter,
            properties,
            eventPublisher
        );

        gitLabProvider = new GitProvider();
        gitLabProvider.setId(PROVIDER_ID);
        gitLabProvider.setType(GitProviderType.GITLAB);
        gitLabProvider.setServerUrl("https://gitlab.com");

        testRepo = new Repository();
        testRepo.setId(REPO_ID);
        testRepo.setNameWithOwner("gitlab-org/gitlab");
        testRepo.setProvider(gitLabProvider);
        testRepo.setDefaultBranch("main");

        // Default: upsertCore succeeds
        lenient()
            .when(
                pullRequestRepository.upsertCore(
                    anyLong(),
                    anyLong(),
                    anyInt(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    anyLong(),
                    any(),
                    any(),
                    anyBoolean(),
                    anyBoolean(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            )
            .thenReturn(1);

        // upsertUser is void - no stubbing needed
    }

    // ========================================================================
    // State Mapping
    // ========================================================================

    @Nested
    @DisplayName("State mapping")
    class StateMapping {

        @Test
        @DisplayName("opened state maps to OPEN")
        void mapState_opened() {
            PullRequest pr = createPullRequestEntity();
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pr));

            GitLabMergeRequestEventDTO event = createEvent("open", "opened", false);
            processor.process(event, createContext());

            verify(pullRequestRepository).upsertCore(
                eq(RAW_MR_ID),
                eq(PROVIDER_ID),
                eq(MR_IID),
                any(),
                any(),
                eq("OPEN"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                any(),
                any(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("closed state maps to CLOSED")
        void mapState_closed() {
            PullRequest pr = createPullRequestEntity();
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pr));

            GitLabMergeRequestEventDTO event = createEvent("close", "closed", false);
            processor.process(event, createContext());

            verify(pullRequestRepository).upsertCore(
                eq(RAW_MR_ID),
                eq(PROVIDER_ID),
                eq(MR_IID),
                any(),
                any(),
                eq("CLOSED"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                any(),
                any(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("merged state maps to MERGED")
        void mapState_merged() {
            PullRequest pr = createPullRequestEntity();
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pr));

            GitLabMergeRequestEventDTO event = createEvent("merge", "merged", false);
            processor.process(event, createContext());

            verify(pullRequestRepository).upsertCore(
                eq(RAW_MR_ID),
                eq(PROVIDER_ID),
                eq(MR_IID),
                any(),
                any(),
                eq("MERGED"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                any(),
                any(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("null state defaults to OPEN")
        void mapState_null() {
            PullRequest pr = createPullRequestEntity();
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pr));

            GitLabMergeRequestEventDTO event = createEventWithState("open", null);
            processor.process(event, createContext());

            verify(pullRequestRepository).upsertCore(
                eq(RAW_MR_ID),
                eq(PROVIDER_ID),
                eq(MR_IID),
                any(),
                any(),
                eq("OPEN"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                any(),
                any(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("unknown state defaults to OPEN")
        void mapState_unknown() {
            PullRequest pr = createPullRequestEntity();
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pr));

            GitLabMergeRequestEventDTO event = createEventWithState("open", "some_unknown_state");
            processor.process(event, createContext());

            verify(pullRequestRepository).upsertCore(
                eq(RAW_MR_ID),
                eq(PROVIDER_ID),
                eq(MR_IID),
                any(),
                any(),
                eq("OPEN"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                any(),
                any(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("locked state maps to CLOSED")
        void mapState_locked() {
            PullRequest pr = createPullRequestEntity();
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pr));

            GitLabMergeRequestEventDTO event = createEventWithState("open", "locked");
            processor.process(event, createContext());

            verify(pullRequestRepository).upsertCore(
                eq(RAW_MR_ID),
                eq(PROVIDER_ID),
                eq(MR_IID),
                any(),
                any(),
                eq("CLOSED"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                any(),
                any(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }
    }

    // ========================================================================
    // Webhook Event Processing
    // ========================================================================

    @Nested
    @DisplayName("Webhook event processing")
    class WebhookProcessing {

        @Test
        @DisplayName("process() creates new PR and publishes PullRequestCreated")
        void processCreatesNewPR() {
            PullRequest pr = createPullRequestEntity();
            // 1st: stale check + isNew (process) -> empty (new PR)
            // 2nd: post-upsert fetch (upsertMergeRequest) -> found
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabWebhookUser.class), eq(PROVIDER_ID))).thenReturn(author);

            GitLabMergeRequestEventDTO event = createEvent("open", "opened", false);
            PullRequest result = processor.process(event, createContext());

            assertThat(result).isNotNull();
            assertThat(result.getProvider()).isEqualTo(gitLabProvider);

            ArgumentCaptor<DomainEvent.PullRequestCreated> eventCaptor = ArgumentCaptor.forClass(
                DomainEvent.PullRequestCreated.class
            );
            verify(eventPublisher).publishEvent(eventCaptor.capture());
        }

        @Test
        @DisplayName("process() updates existing PR and publishes no PullRequestCreated")
        void processUpdatesExistingPR() {
            PullRequest pr = createPullRequestEntity();
            // 2 calls: stale check + isNew (process), post-upsert fetch (upsertMergeRequest)
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabWebhookUser.class), eq(PROVIDER_ID))).thenReturn(author);

            GitLabMergeRequestEventDTO event = createEvent("update", "opened", false);
            PullRequest result = processor.process(event, createContext());

            assertThat(result).isNotNull();

            // No PullRequestCreated event since PR already existed
            verify(eventPublisher, never()).publishEvent(any(DomainEvent.PullRequestCreated.class));
        }

        @Test
        @DisplayName("process() updates existing PR and publishes PullRequestUpdated")
        void processUpdatesExistingPRPublishesPullRequestUpdated() {
            PullRequest pr = createPullRequestEntity();
            // 2 calls: stale check + isNew (process), post-upsert fetch (upsertMergeRequest)
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabWebhookUser.class), eq(PROVIDER_ID))).thenReturn(author);

            GitLabMergeRequestEventDTO event = createEvent("update", "opened", false);
            PullRequest result = processor.process(event, createContext());

            assertThat(result).isNotNull();

            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

            boolean hasPullRequestUpdated = eventCaptor
                .getAllValues()
                .stream()
                .anyMatch(e -> e instanceof DomainEvent.PullRequestUpdated);
            assertThat(hasPullRequestUpdated).isTrue();
        }

        @Test
        @DisplayName("processClosed() sets state to CLOSED and publishes PullRequestClosed(wasMerged=false)")
        void processClosedPublishesEvent() {
            PullRequest pr = createPullRequestEntity();
            // 2 calls: stale+isNew check (process), post-upsert fetch (upsertMergeRequest)
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabWebhookUser.class), eq(PROVIDER_ID))).thenReturn(author);

            GitLabMergeRequestEventDTO event = createEvent("close", "closed", false);
            PullRequest result = processor.processClosed(event, createContext());

            assertThat(result).isNotNull();

            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

            boolean hasPullRequestClosed = eventCaptor
                .getAllValues()
                .stream()
                .anyMatch(e -> e instanceof DomainEvent.PullRequestClosed closed && !closed.wasMerged());
            assertThat(hasPullRequestClosed).isTrue();
        }

        @Test
        @DisplayName("processReopened() sets state to OPEN and publishes PullRequestReopened")
        void processReopenedPublishesEvent() {
            PullRequest pr = createPullRequestEntity();
            pr.setState(Issue.State.CLOSED);
            // 2 calls: stale+isNew check (process), post-upsert fetch (upsertMergeRequest)
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabWebhookUser.class), eq(PROVIDER_ID))).thenReturn(author);

            GitLabMergeRequestEventDTO event = createEvent("reopen", "opened", false);
            PullRequest result = processor.processReopened(event, createContext());

            assertThat(result).isNotNull();

            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

            boolean hasPullRequestReopened = eventCaptor
                .getAllValues()
                .stream()
                .anyMatch(e -> e instanceof DomainEvent.PullRequestReopened);
            assertThat(hasPullRequestReopened).isTrue();
        }

        @Test
        @DisplayName(
            "processMerged() sets state to MERGED, isMerged=true, publishes PullRequestClosed(wasMerged=true) and PullRequestMerged"
        )
        void processMergedPublishesEvents() {
            PullRequest pr = createPullRequestEntity();
            // 2 calls: stale+isNew check (process), post-upsert fetch (upsertMergeRequest)
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabWebhookUser.class), eq(PROVIDER_ID))).thenReturn(author);

            GitLabMergeRequestEventDTO event = createEvent("merge", "merged", false);
            PullRequest result = processor.processMerged(event, createContext());

            assertThat(result).isNotNull();

            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

            List<Object> publishedEvents = eventCaptor.getAllValues();

            boolean hasPullRequestClosed = publishedEvents
                .stream()
                .anyMatch(e -> e instanceof DomainEvent.PullRequestClosed closed && closed.wasMerged());
            boolean hasPullRequestMerged = publishedEvents
                .stream()
                .anyMatch(e -> e instanceof DomainEvent.PullRequestMerged);

            assertThat(hasPullRequestClosed).isTrue();
            assertThat(hasPullRequestMerged).isTrue();
        }

        @Test
        @DisplayName("processApproved() creates PullRequestReview with state APPROVED and publishes ReviewSubmitted")
        void processApprovedCreatesReview() {
            PullRequest pr = createPullRequestEntity();
            pr.setNativeId(RAW_MR_ID);
            // 2 calls: stale+isNew check (process), post-upsert fetch (upsertMergeRequest)
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(userRepository.findByNativeIdAndProviderId(RAW_USER_ID, PROVIDER_ID)).thenReturn(Optional.of(author));

            User approver = createApproverEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabWebhookUser.class), eq(PROVIDER_ID))).thenReturn(
                approver
            );

            long expectedReviewId = GitLabMergeRequestProcessor.generateApprovalNativeId(RAW_MR_ID, RAW_APPROVER_ID);
            when(reviewRepository.findByNativeIdAndProviderId(expectedReviewId, PROVIDER_ID)).thenReturn(
                Optional.empty()
            );

            GitLabMergeRequestEventDTO event = createApprovalEvent("approved", "opened");
            PullRequest result = processor.processApproved(event, createContext());

            assertThat(result).isNotNull();

            ArgumentCaptor<PullRequestReview> reviewCaptor = ArgumentCaptor.forClass(PullRequestReview.class);
            verify(reviewRepository).save(reviewCaptor.capture());

            PullRequestReview savedReview = reviewCaptor.getValue();
            assertThat(savedReview.getState()).isEqualTo(PullRequestReview.State.APPROVED);
            assertThat(savedReview.getAuthor()).isEqualTo(approver);
            assertThat(savedReview.getNativeId()).isEqualTo(expectedReviewId);

            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

            boolean hasReviewSubmitted = eventCaptor
                .getAllValues()
                .stream()
                .anyMatch(e -> e instanceof DomainEvent.ReviewSubmitted);
            assertThat(hasReviewSubmitted).isTrue();
        }

        @Test
        @DisplayName("processUnapproved() dismisses review and publishes ReviewDismissed")
        void processUnapprovedDismissesReview() {
            PullRequest pr = createPullRequestEntity();
            pr.setNativeId(RAW_MR_ID);
            // 2 calls: stale+isNew check (process), post-upsert fetch (upsertMergeRequest)
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(userRepository.findByNativeIdAndProviderId(RAW_USER_ID, PROVIDER_ID)).thenReturn(Optional.of(author));

            User approver = createApproverEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabWebhookUser.class), eq(PROVIDER_ID))).thenReturn(
                approver
            );

            long expectedNativeId = GitLabMergeRequestProcessor.generateApprovalNativeId(RAW_MR_ID, RAW_APPROVER_ID);
            PullRequestReview existingReview = new PullRequestReview();
            existingReview.setNativeId(expectedNativeId);
            existingReview.setState(PullRequestReview.State.APPROVED);
            existingReview.setHtmlUrl("https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5#approvals");
            existingReview.setSubmittedAt(Instant.now());
            existingReview.setAuthor(approver);
            existingReview.setPullRequest(pr);
            pr.getReviews().add(existingReview);

            when(reviewRepository.findByNativeIdAndProviderId(expectedNativeId, PROVIDER_ID)).thenReturn(
                Optional.of(existingReview)
            );

            GitLabMergeRequestEventDTO event = createApprovalEvent("unapproved", "opened");
            PullRequest result = processor.processUnapproved(event, createContext());

            assertThat(result).isNotNull();

            // Verify the review was dismissed and saved
            assertThat(existingReview.getState()).isEqualTo(PullRequestReview.State.DISMISSED);
            assertThat(existingReview.isDismissed()).isTrue();
            verify(reviewRepository).save(existingReview);

            // ReviewDismissed is published (not ReviewSubmitted)
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

            boolean hasReviewDismissed = eventCaptor
                .getAllValues()
                .stream()
                .anyMatch(e -> e instanceof DomainEvent.ReviewDismissed);
            assertThat(hasReviewDismissed).isTrue();
        }

        @Test
        @DisplayName("processApproved() is idempotent — skips save if review already exists")
        void processApprovedIdempotent() {
            PullRequest pr = createPullRequestEntity();
            pr.setNativeId(RAW_MR_ID);
            // 2 calls: stale+isNew check (process), post-upsert fetch (upsertMergeRequest)
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(userRepository.findByNativeIdAndProviderId(RAW_USER_ID, PROVIDER_ID)).thenReturn(Optional.of(author));

            User approver = createApproverEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabWebhookUser.class), eq(PROVIDER_ID))).thenReturn(
                approver
            );

            // Review ALREADY exists — simulates duplicate approval webhook
            long expectedNativeId = GitLabMergeRequestProcessor.generateApprovalNativeId(RAW_MR_ID, RAW_APPROVER_ID);
            PullRequestReview existingReview = new PullRequestReview();
            existingReview.setNativeId(expectedNativeId);
            existingReview.setState(PullRequestReview.State.APPROVED);
            when(reviewRepository.findByNativeIdAndProviderId(expectedNativeId, PROVIDER_ID)).thenReturn(
                Optional.of(existingReview)
            );

            GitLabMergeRequestEventDTO event = createApprovalEvent("approved", "opened");
            PullRequest result = processor.processApproved(event, createContext());

            assertThat(result).isNotNull();
            // Review should NOT be saved again
            verify(reviewRepository, never()).save(any(PullRequestReview.class));
        }

        @Test
        @DisplayName("processUnapproved() with no existing review does nothing")
        void processUnapprovedNoExistingReview() {
            PullRequest pr = createPullRequestEntity();
            pr.setNativeId(RAW_MR_ID);
            // 2 calls: stale+isNew check (process), post-upsert fetch (upsertMergeRequest)
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(userRepository.findByNativeIdAndProviderId(RAW_USER_ID, PROVIDER_ID)).thenReturn(Optional.of(author));

            User approver = createApproverEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabWebhookUser.class), eq(PROVIDER_ID))).thenReturn(
                approver
            );

            // No existing review — findById returns empty
            long expectedReviewId = GitLabMergeRequestProcessor.generateApprovalNativeId(RAW_MR_ID, RAW_APPROVER_ID);
            when(reviewRepository.findByNativeIdAndProviderId(expectedReviewId, PROVIDER_ID)).thenReturn(
                Optional.empty()
            );

            GitLabMergeRequestEventDTO event = createApprovalEvent("unapproved", "opened");
            PullRequest result = processor.processUnapproved(event, createContext());

            assertThat(result).isNotNull();
            // No save should happen (no review to update)
            verify(reviewRepository, never()).save(any(PullRequestReview.class));
            // Only PullRequestUpdated from process(), no ReviewSubmitted
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
            boolean hasReviewSubmitted = eventCaptor
                .getAllValues()
                .stream()
                .anyMatch(e -> e instanceof DomainEvent.ReviewSubmitted);
            assertThat(hasReviewSubmitted).isFalse();
        }

        @Test
        @DisplayName("processMerged() resolves mergeUser when mergeUserId matches event user")
        void processMergedResolvesMergeUser() {
            PullRequest pr = createPullRequestEntity();
            // 2 calls: stale+isNew check (process), post-upsert fetch (upsertMergeRequest)
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabWebhookUser.class), eq(PROVIDER_ID))).thenReturn(author);

            // Create event with mergeUserId matching the event user's ID
            var attrs = new GitLabMergeRequestEventDTO.ObjectAttributes(
                RAW_MR_ID,
                MR_IID,
                "Add awesome feature",
                "This MR adds an awesome feature",
                "merged",
                "merge",
                "feature/awesome-feature",
                "main",
                false,
                RAW_USER_ID,
                RAW_USER_ID, // mergeUserId = event.user().id()
                null,
                "2024-01-15T10:00:00Z",
                "2024-01-15T14:00:00Z",
                null,
                "2024-01-15T14:00:00Z",
                "https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5",
                null,
                null
            );
            GitLabMergeRequestEventDTO event = new GitLabMergeRequestEventDTO(
                "merge_request",
                "merge_request",
                createUser(),
                createProject(),
                attrs,
                List.of(new GitLabWebhookLabel(101L, "feature", "#0075ca")),
                null,
                null
            );

            PullRequest result = processor.processMerged(event, createContext());

            assertThat(result).isNotNull();
            // mergedById should be the author's entity ID (since mergeUser = event.user())
            verify(pullRequestRepository).upsertCore(
                eq(RAW_MR_ID),
                eq(PROVIDER_ID),
                eq(MR_IID),
                any(),
                any(),
                eq("MERGED"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                any(),
                any(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(author.getId())
            );
        }

        @Test
        @DisplayName("processApproved() with null user skips approval creation")
        void processApprovedNullUserSkips() {
            PullRequest pr = createPullRequestEntity();
            pr.setNativeId(RAW_MR_ID);
            // 2 calls: stale+isNew check (process), post-upsert fetch (upsertMergeRequest)
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(userRepository.findByNativeIdAndProviderId(RAW_USER_ID, PROVIDER_ID)).thenReturn(Optional.of(author));

            // Create event with null user (the approval actor)
            var attrs = new GitLabMergeRequestEventDTO.ObjectAttributes(
                RAW_MR_ID,
                MR_IID,
                "Add awesome feature",
                "This MR adds an awesome feature",
                "opened",
                "approved",
                "feature/awesome-feature",
                "main",
                false,
                RAW_USER_ID,
                null,
                null,
                "2024-01-15T10:00:00Z",
                "2024-01-15T14:00:00Z",
                null,
                null,
                "https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5",
                null,
                null
            );
            GitLabMergeRequestEventDTO event = new GitLabMergeRequestEventDTO(
                "merge_request",
                "merge_request",
                null,
                createProject(), // null user
                attrs,
                List.of(new GitLabWebhookLabel(101L, "feature", "#0075ca")),
                null,
                null
            );

            PullRequest result = processor.processApproved(event, createContext());

            assertThat(result).isNotNull();
            // No review should be saved when user is null
            verify(reviewRepository, never()).save(any(PullRequestReview.class));
        }

        @Test
        @DisplayName("processUnapproved() is idempotent when review is already DISMISSED")
        void processUnapproved_alreadyDismissed_isIdempotent() {
            PullRequest pr = createPullRequestEntity();
            pr.setNativeId(RAW_MR_ID);
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(userRepository.findByNativeIdAndProviderId(RAW_USER_ID, PROVIDER_ID)).thenReturn(Optional.of(author));

            User approver = createApproverEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabWebhookUser.class), eq(PROVIDER_ID))).thenReturn(
                approver
            );

            // Review already in DISMISSED state
            long expectedNativeId = GitLabMergeRequestProcessor.generateApprovalNativeId(RAW_MR_ID, RAW_APPROVER_ID);
            PullRequestReview existingReview = new PullRequestReview();
            existingReview.setNativeId(expectedNativeId);
            existingReview.setState(PullRequestReview.State.DISMISSED);
            existingReview.setDismissed(true);
            existingReview.setHtmlUrl("https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5#approvals");
            existingReview.setSubmittedAt(Instant.now());
            existingReview.setAuthor(approver);
            existingReview.setPullRequest(pr);
            pr.getReviews().add(existingReview);

            when(reviewRepository.findByNativeIdAndProviderId(expectedNativeId, PROVIDER_ID)).thenReturn(
                Optional.of(existingReview)
            );

            GitLabMergeRequestEventDTO event = createApprovalEvent("unapproved", "opened");
            PullRequest result = processor.processUnapproved(event, createContext());

            assertThat(result).isNotNull();
            // Already DISMISSED — save() should NOT be called
            verify(reviewRepository, never()).save(any(PullRequestReview.class));
            assertThat(existingReview.getState()).isEqualTo(PullRequestReview.State.DISMISSED);
        }

        @Test
        @DisplayName("processApproved() re-approval from CHANGES_REQUESTED updates to APPROVED")
        void processApproved_reApproval_fromChangesRequested_updatesToApproved() {
            PullRequest pr = createPullRequestEntity();
            pr.setNativeId(RAW_MR_ID);
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(userRepository.findByNativeIdAndProviderId(RAW_USER_ID, PROVIDER_ID)).thenReturn(Optional.of(author));

            User approver = createApproverEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabWebhookUser.class), eq(PROVIDER_ID))).thenReturn(
                approver
            );

            // Existing review in CHANGES_REQUESTED state (from a prior unapproval)
            long expectedNativeId = GitLabMergeRequestProcessor.generateApprovalNativeId(RAW_MR_ID, RAW_APPROVER_ID);
            PullRequestReview existingReview = new PullRequestReview();
            existingReview.setNativeId(expectedNativeId);
            existingReview.setState(PullRequestReview.State.CHANGES_REQUESTED);
            existingReview.setHtmlUrl("https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5#approvals");
            existingReview.setSubmittedAt(Instant.now().minusSeconds(3600));
            existingReview.setAuthor(approver);
            existingReview.setPullRequest(pr);
            pr.getReviews().add(existingReview);

            when(reviewRepository.findByNativeIdAndProviderId(expectedNativeId, PROVIDER_ID)).thenReturn(
                Optional.of(existingReview)
            );

            GitLabMergeRequestEventDTO event = createApprovalEvent("approved", "opened");
            PullRequest result = processor.processApproved(event, createContext());

            assertThat(result).isNotNull();

            // Review should be updated to APPROVED and saved
            assertThat(existingReview.getState()).isEqualTo(PullRequestReview.State.APPROVED);
            verify(reviewRepository).save(existingReview);

            // ReviewSubmitted event should be emitted for the state change
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
            boolean hasReviewSubmitted = eventCaptor
                .getAllValues()
                .stream()
                .anyMatch(e -> e instanceof DomainEvent.ReviewSubmitted);
            assertThat(hasReviewSubmitted).isTrue();
        }

        @Test
        @DisplayName("processRequestedChangesFromNote() updates existing APPROVED review to CHANGES_REQUESTED")
        void processRequestedChangesFromNote_updatesExistingApproval() {
            PullRequest pr = createPullRequestEntity();
            pr.setNativeId(RAW_MR_ID);

            User reviewer = createUserEntity();
            reviewer.setNativeId(RAW_USER_ID);

            PullRequestReview existingReview = new PullRequestReview();
            existingReview.setState(PullRequestReview.State.APPROVED);
            existingReview.setAuthor(reviewer);
            existingReview.setPullRequest(pr);

            long approvalNativeId = GitLabMergeRequestProcessor.generateApprovalNativeId(RAW_MR_ID, RAW_USER_ID);
            when(reviewRepository.findByNativeIdAndProviderId(approvalNativeId, PROVIDER_ID)).thenReturn(
                Optional.of(existingReview)
            );

            processor.processRequestedChangesFromNote(pr, reviewer, createContext());

            assertThat(existingReview.getState()).isEqualTo(PullRequestReview.State.CHANGES_REQUESTED);
            verify(reviewRepository).save(existingReview);
            verify(eventPublisher, atLeastOnce()).publishEvent(any(DomainEvent.ReviewSubmitted.class));
        }

        @Test
        @DisplayName("processRequestedChangesFromNote() is idempotent when already CHANGES_REQUESTED")
        void processRequestedChangesFromNote_idempotent() {
            PullRequest pr = createPullRequestEntity();
            pr.setNativeId(RAW_MR_ID);

            User reviewer = createUserEntity();
            reviewer.setNativeId(RAW_USER_ID);

            PullRequestReview existingReview = new PullRequestReview();
            existingReview.setState(PullRequestReview.State.CHANGES_REQUESTED);

            long approvalNativeId = GitLabMergeRequestProcessor.generateApprovalNativeId(RAW_MR_ID, RAW_USER_ID);
            when(reviewRepository.findByNativeIdAndProviderId(approvalNativeId, PROVIDER_ID)).thenReturn(
                Optional.of(existingReview)
            );

            processor.processRequestedChangesFromNote(pr, reviewer, createContext());

            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("processRequestedChangesFromNote() skips when no existing review (prevents false positives)")
        void processRequestedChangesFromNote_noExistingReview_skips() {
            PullRequest pr = createPullRequestEntity();
            pr.setNativeId(RAW_MR_ID);

            User reviewer = createUserEntity();
            reviewer.setNativeId(RAW_USER_ID);

            long approvalNativeId = GitLabMergeRequestProcessor.generateApprovalNativeId(RAW_MR_ID, RAW_USER_ID);
            when(reviewRepository.findByNativeIdAndProviderId(approvalNativeId, PROVIDER_ID)).thenReturn(
                Optional.empty()
            );

            processor.processRequestedChangesFromNote(pr, reviewer, createContext());

            verify(reviewRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any(DomainEvent.ReviewSubmitted.class));
        }

        @Test
        @DisplayName("processRequestedChangesFromNote() skips when reviewer nativeId is null")
        void processRequestedChangesFromNote_nullNativeId_skips() {
            PullRequest pr = createPullRequestEntity();
            pr.setNativeId(RAW_MR_ID);

            User reviewer = createUserEntity();
            reviewer.setNativeId(null);

            processor.processRequestedChangesFromNote(pr, reviewer, createContext());

            verify(reviewRepository, never()).findByNativeIdAndProviderId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("process() with missing id and iid skips processing")
        void processMissingIdSkips() {
            var attrs = new GitLabMergeRequestEventDTO.ObjectAttributes(
                null,
                null,
                "Title",
                "desc",
                "opened",
                "open",
                "feature/branch",
                "main",
                false,
                12345L,
                null,
                null,
                "2024-01-15T10:00:00Z",
                "2024-01-15T10:00:00Z",
                null,
                null,
                "https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5",
                null,
                null
            );
            GitLabMergeRequestEventDTO event = new GitLabMergeRequestEventDTO(
                "merge_request",
                "merge_request",
                createUser(),
                createProject(),
                attrs,
                null,
                null,
                null
            );

            PullRequest result = processor.process(event, createContext());

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("process() returns existing entity for stale webhook (allows lifecycle events)")
        void processSkipsStaleWebhookUpdate() {
            // The staleness check returns the existing entity without calling upsertCore.
            // This allows callers (processClosed, processMerged, etc.) to still publish
            // lifecycle events while preventing stale data from overwriting newer sync data.
            PullRequest pr = createPullRequestEntity();
            pr.setUpdatedAt(Instant.parse("2024-02-01T00:00:00Z"));
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID)).thenReturn(Optional.of(pr));

            // Create an event with older updatedAt ("2024-01-15T10:00:00Z") than the existing entity
            GitLabMergeRequestEventDTO event = createEvent("update", "opened", false);
            PullRequest result = processor.process(event, createContext());

            // Stale webhooks return existing entity (not null) so lifecycle events can still fire
            assertThat(result).isSameAs(pr);

            // upsertCore is NEVER called because the staleness check short-circuits
            verify(pullRequestRepository, never()).upsertCore(
                anyLong(),
                anyLong(),
                anyInt(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyLong(),
                any(),
                any(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );

            // No PullRequestCreated/PullRequestUpdated events for stale webhooks
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    // ========================================================================
    // GraphQL Sync Processing
    // ========================================================================

    @Nested
    @DisplayName("GraphQL sync processing")
    class SyncProcessing {

        @Test
        @DisplayName("processFromSync() creates PR from GraphQL sync data")
        void processFromSyncCreatesPR() {
            PullRequest pr = createPullRequestEntity();
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabUserLookup.class), eq(PROVIDER_ID))).thenReturn(author);

            var syncData = createSyncData();
            PullRequest result = processor.processFromSync(syncData, testRepo, 1L);

            assertThat(result).isNotNull();
            assertThat(result.getProvider()).isEqualTo(gitLabProvider);

            verify(pullRequestRepository).upsertCore(
                eq(RAW_MR_ID),
                eq(PROVIDER_ID),
                eq(MR_IID),
                any(),
                any(),
                eq("OPEN"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                any(),
                any(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("processFromSync() publishes PullRequestCreated for new PR")
        void processFromSyncPublishesCreatedEvent() {
            PullRequest pr = createPullRequestEntity();
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pr));

            var syncData = createSyncData();
            processor.processFromSync(syncData, testRepo, 1L);

            ArgumentCaptor<DomainEvent.PullRequestCreated> eventCaptor = ArgumentCaptor.forClass(
                DomainEvent.PullRequestCreated.class
            );
            verify(eventPublisher).publishEvent(eventCaptor.capture());
        }

        @Test
        @DisplayName("processFromSync() publishes PullRequestUpdated for existing PR")
        void processFromSyncPublishesUpdatedForExisting() {
            PullRequest pr = createPullRequestEntity();
            // PR already exists
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            var syncData = createSyncData();
            processor.processFromSync(syncData, testRepo, 1L);

            var captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());

            boolean hasUpdated = captor
                .getAllValues()
                .stream()
                .anyMatch(e -> e instanceof DomainEvent.PullRequestUpdated);
            assertThat(hasUpdated).as("PullRequestUpdated event should be published for existing MR in sync").isTrue();

            boolean hasCreated = captor
                .getAllValues()
                .stream()
                .anyMatch(e -> e instanceof DomainEvent.PullRequestCreated);
            assertThat(hasCreated).as("PullRequestCreated should NOT be published for existing MR in sync").isFalse();
        }

        @Test
        @DisplayName("processFromSync() links milestone when milestoneIid is provided")
        void processFromSyncLinksMilestone() {
            PullRequest pr = createPullRequestEntity();
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabUserLookup.class), eq(PROVIDER_ID))).thenReturn(author);

            Milestone milestone = new Milestone();
            milestone.setId(42L);
            milestone.setNumber(3);
            when(milestoneRepository.findByNumberAndRepositoryId(3, REPO_ID)).thenReturn(Optional.of(milestone));

            var syncData = new GitLabMergeRequestProcessor.SyncMergeRequestData(
                "gid://gitlab/MergeRequest/999555",
                "5",
                "Add awesome feature",
                null,
                "opened",
                false,
                null,
                null,
                false,
                "https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5",
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                "feature/awesome-feature",
                "main",
                null,
                null,
                false,
                0,
                "gid://gitlab/User/12345",
                "testuser",
                "Test User",
                "https://gitlab.com/uploads/avatar.png",
                "https://gitlab.com/testuser",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                3
            );
            processor.processFromSync(syncData, testRepo, 1L);

            verify(pullRequestRepository).upsertCore(
                eq(RAW_MR_ID),
                eq(PROVIDER_ID),
                eq(MR_IID),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                eq(42L),
                any(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("processFromSync() passes null milestoneId when milestone not found")
        void processFromSyncMilestoneNotFound() {
            PullRequest pr = createPullRequestEntity();
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pr));

            User author = createUserEntity();
            when(gitLabUserService.findOrCreateUser(any(GitLabUserLookup.class), eq(PROVIDER_ID))).thenReturn(author);

            when(milestoneRepository.findByNumberAndRepositoryId(99, REPO_ID)).thenReturn(Optional.empty());

            var syncData = new GitLabMergeRequestProcessor.SyncMergeRequestData(
                "gid://gitlab/MergeRequest/999555",
                "5",
                "Add awesome feature",
                null,
                "opened",
                false,
                null,
                null,
                false,
                "https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5",
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                "feature/awesome-feature",
                "main",
                null,
                null,
                false,
                0,
                "gid://gitlab/User/12345",
                "testuser",
                "Test User",
                "https://gitlab.com/uploads/avatar.png",
                "https://gitlab.com/testuser",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                99
            );
            processor.processFromSync(syncData, testRepo, 1L);

            verify(pullRequestRepository).upsertCore(
                eq(RAW_MR_ID),
                eq(PROVIDER_ID),
                eq(MR_IID),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                eq((Long) null),
                any(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("processFromSync() skips milestone lookup when milestoneIid is null")
        void processFromSyncNullMilestoneIid() {
            PullRequest pr = createPullRequestEntity();
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pr));

            var syncData = createSyncData();
            processor.processFromSync(syncData, testRepo, 1L);

            verify(milestoneRepository, never()).findByNumberAndRepositoryId(anyInt(), anyLong());
        }

        @Test
        @DisplayName("processFromSync() with invalid globalId skips processing")
        void processFromSyncInvalidGlobalId() {
            var syncData = new GitLabMergeRequestProcessor.SyncMergeRequestData(
                "invalid-id",
                "5",
                "Title",
                null,
                "opened",
                false,
                null,
                null,
                false,
                "https://example.com",
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                "feature/branch",
                "main",
                null,
                null,
                false,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
            PullRequest result = processor.processFromSync(syncData, testRepo, 1L);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("processFromSync() with invalid iid skips processing")
        void processFromSyncInvalidIid() {
            var syncData = new GitLabMergeRequestProcessor.SyncMergeRequestData(
                "gid://gitlab/MergeRequest/999555",
                "not-a-number",
                "Title",
                null,
                "opened",
                false,
                null,
                null,
                false,
                "https://example.com",
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                "feature/branch",
                "main",
                null,
                null,
                false,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
            PullRequest result = processor.processFromSync(syncData, testRepo, 1L);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("processFromSync() reconciles approvals: adds missing and removes stale")
        void processFromSyncReconcileApprovals() {
            PullRequest pr = createPullRequestEntity();
            pr.setNativeId(RAW_MR_ID);

            // Existing stale approval review that should be removed
            User staleApprover = new User();
            staleApprover.setId(400L);
            staleApprover.setNativeId(99999L);
            staleApprover.setLogin("staleuser");

            long staleNativeId = GitLabMergeRequestProcessor.generateApprovalNativeId(RAW_MR_ID, 99999L);
            PullRequestReview staleReview = new PullRequestReview();
            staleReview.setNativeId(staleNativeId);
            staleReview.setProvider(gitLabProvider);
            staleReview.setState(PullRequestReview.State.APPROVED);
            staleReview.setHtmlUrl("https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5#approvals");
            staleReview.setSubmittedAt(Instant.now());
            staleReview.setAuthor(staleApprover);
            staleReview.setPullRequest(pr);
            pr.getReviews().add(staleReview);

            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.of(pr))
                .thenReturn(Optional.of(pr));

            // Stub the author user lookup (processFromSync resolves the MR author via gitLabUserService)
            User author = createUserEntity();
            lenient()
                .when(
                    gitLabUserService.findOrCreateUser(
                        argThat(
                            (GitLabUserLookup lookup) ->
                                lookup != null && "gid://gitlab/User/12345".equals(lookup.globalId())
                        ),
                        eq(PROVIDER_ID)
                    )
                )
                .thenReturn(author);

            // New approver from sync (reconcileApprovals resolves via gitLabUserService)
            // Lenient because the merge user call passes all nulls (unmatched invocation)
            User newApprover = createApproverEntity();
            lenient()
                .when(
                    gitLabUserService.findOrCreateUser(
                        argThat(
                            (GitLabUserLookup lookup) ->
                                lookup != null && "gid://gitlab/User/11111".equals(lookup.globalId())
                        ),
                        eq(PROVIDER_ID)
                    )
                )
                .thenReturn(newApprover);

            var syncData = new GitLabMergeRequestProcessor.SyncMergeRequestData(
                "gid://gitlab/MergeRequest/999555",
                "5",
                "Add awesome feature",
                "This MR adds an awesome feature",
                "opened",
                false,
                null,
                null,
                true,
                "https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5",
                "2024-01-15T10:00:00Z",
                "2024-01-15T10:00:00Z",
                null,
                null,
                1,
                10,
                2,
                3,
                "feature/awesome-feature",
                "main",
                null,
                null,
                false,
                0,
                "gid://gitlab/User/12345",
                "testuser",
                "Test User",
                "https://gitlab.com/uploads/avatar.png",
                "https://gitlab.com/testuser",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                    new GitLabMergeRequestProcessor.SyncUserData(
                        "gid://gitlab/User/11111",
                        "reviewer1",
                        "Reviewer One",
                        "https://gitlab.com/uploads/avatar.png",
                        "https://gitlab.com/reviewer1",
                        null
                    )
                ),
                null
            );
            processor.processFromSync(syncData, testRepo, 1L);

            // Verify new approval was created (save called for new review)
            // and stale review was dismissed (save called for stale review)
            verify(reviewRepository, org.mockito.Mockito.atLeast(2)).save(any(PullRequestReview.class));

            // Verify stale approval was dismissed (not CHANGES_REQUESTED — unapproval is distinct)
            assertThat(staleReview.getState()).isEqualTo(PullRequestReview.State.DISMISSED);
        }
    }

    // ========================================================================
    // Confidential Filtering
    // ========================================================================

    @Nested
    @DisplayName("Confidential filtering")
    class ConfidentialFiltering {

        @Test
        @DisplayName("process() skips confidential merge request")
        void processSkipsConfidential() {
            GitLabMergeRequestEventDTO event = createConfidentialEvent("open", "opened");
            ProcessingContext ctx = createContext();

            PullRequest result = processor.process(event, ctx);

            assertThat(result).isNull();
            verify(pullRequestRepository, never()).upsertCore(
                anyLong(),
                anyLong(),
                anyInt(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyLong(),
                any(),
                any(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }
    }

    // ========================================================================
    // Detailed Merge Status Mapping
    // ========================================================================

    @Nested
    @DisplayName("Detailed merge status mapping")
    class DetailedMergeStatusMapping {

        @Test
        @DisplayName("mergeable maps to CLEAN")
        void mergeableMapsToClean() {
            assertMergeStatusMapping("mergeable", "CLEAN");
        }

        @Test
        @DisplayName("conflict maps to DIRTY")
        void conflictMapsToDirty() {
            assertMergeStatusMapping("conflict", "DIRTY");
        }

        @Test
        @DisplayName("need_rebase maps to DIRTY")
        void needRebaseMapsToDirty() {
            assertMergeStatusMapping("need_rebase", "DIRTY");
        }

        @Test
        @DisplayName("ci_must_pass maps to UNSTABLE")
        void ciMustPassMapsToUnstable() {
            assertMergeStatusMapping("ci_must_pass", "UNSTABLE");
        }

        @Test
        @DisplayName("not_approved maps to BLOCKED")
        void notApprovedMapsToBlocked() {
            assertMergeStatusMapping("not_approved", "BLOCKED");
        }

        @Test
        @DisplayName("checking maps to UNKNOWN")
        void checkingMapsToUnknown() {
            assertMergeStatusMapping("checking", "UNKNOWN");
        }

        @Test
        @DisplayName("unknown status defaults to UNKNOWN")
        void unknownDefaultsToUnknown() {
            assertMergeStatusMapping("some_future_status", "UNKNOWN");
        }

        @Test
        @DisplayName("null status maps to null")
        void nullMapsToNull() {
            assertMergeStatusMapping(null, null);
        }

        private void assertMergeStatusMapping(
            @org.springframework.lang.Nullable String detailedStatus,
            @org.springframework.lang.Nullable String expectedMapping
        ) {
            PullRequest pr = createPullRequestEntity();
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pr));

            var syncData = new GitLabMergeRequestProcessor.SyncMergeRequestData(
                "gid://gitlab/MergeRequest/999555",
                "5",
                "Title",
                null,
                "opened",
                false,
                null,
                detailedStatus,
                false,
                "https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5",
                "2024-01-15T10:00:00Z",
                "2024-01-15T10:00:00Z",
                null,
                null,
                1,
                10,
                2,
                3,
                "feature/branch",
                "main",
                null,
                null,
                false,
                0,
                "gid://gitlab/User/12345",
                "testuser",
                "Test User",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
            processor.processFromSync(syncData, testRepo, 1L);

            verify(pullRequestRepository).upsertCore(
                eq(RAW_MR_ID),
                eq(PROVIDER_ID),
                eq(MR_IID),
                any(),
                any(),
                eq("OPEN"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                any(),
                any(),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(expectedMapping),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }
    }

    // ========================================================================
    // Approval Review ID Generation
    // ========================================================================

    @Nested
    @DisplayName("Approval review ID generation")
    class ApprovalReviewIdGeneration {

        @Test
        @DisplayName("generates unique IDs for different (mr, user) pairs")
        void uniqueIdsForDifferentPairs() {
            long id1 = GitLabMergeRequestProcessor.generateApprovalNativeId(100, 200);
            long id2 = GitLabMergeRequestProcessor.generateApprovalNativeId(100, 201);
            long id3 = GitLabMergeRequestProcessor.generateApprovalNativeId(101, 200);

            assertThat(id1).isPositive();
            assertThat(id2).isPositive();
            assertThat(id3).isPositive();
            assertThat(id1).isNotEqualTo(id2).isNotEqualTo(id3);
            assertThat(id2).isNotEqualTo(id3);
        }

        @Test
        @DisplayName("deterministic: same inputs produce same output")
        void deterministicOutput() {
            long id1 = GitLabMergeRequestProcessor.generateApprovalNativeId(999555, 12345);
            long id2 = GitLabMergeRequestProcessor.generateApprovalNativeId(999555, 12345);
            assertThat(id1).isEqualTo(id2);
        }

        @Test
        @DisplayName("result is always positive even for max 32-bit MR ID (sign bit cleared)")
        void alwaysPositiveForMax32BitMrId() {
            long maxSafe = (1L << 32) - 1; // 4294967295
            long id = GitLabMergeRequestProcessor.generateApprovalNativeId(maxSafe, 1);
            // (0xFFFFFFFF << 32) | 1 would set sign bit, but & Long.MAX_VALUE clears it
            assertThat(id).isPositive();
        }

        @Test
        @DisplayName("result is positive for max 32-bit user ID with small MR ID")
        void positiveForMax32BitUserId() {
            long maxUser = (1L << 32) - 1; // 4294967295
            long id = GitLabMergeRequestProcessor.generateApprovalNativeId(1, maxUser);
            // (1 << 32) | 0xFFFFFFFF = 0x1_FFFFFFFF which is positive
            assertThat(id).isPositive();
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private ProcessingContext createContext() {
        return ProcessingContext.forWebhook(1L, testRepo, "open");
    }

    private PullRequest createPullRequestEntity() {
        PullRequest pr = new PullRequest();
        pr.setId(ENTITY_MR_ID);
        pr.setNativeId(RAW_MR_ID);
        pr.setNumber(MR_IID);
        pr.setTitle("Add awesome feature");
        pr.setState(Issue.State.OPEN);
        pr.setRepository(testRepo);
        pr.setHtmlUrl("https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5");
        pr.setLabels(new HashSet<>());
        pr.setAssignees(new HashSet<>());
        pr.setRequestedReviewers(new HashSet<>());
        pr.setReviews(new HashSet<>());
        return pr;
    }

    private User createUserEntity() {
        User user = new User();
        user.setId(ENTITY_USER_ID);
        user.setNativeId(RAW_USER_ID);
        user.setLogin("testuser");
        user.setName("Test User");
        return user;
    }

    private User createApproverEntity() {
        User user = new User();
        user.setId(ENTITY_APPROVER_ID);
        user.setNativeId(RAW_APPROVER_ID);
        user.setLogin("reviewer1");
        user.setName("Reviewer One");
        return user;
    }

    private GitLabMergeRequestEventDTO createEvent(String action, String state, boolean confidential) {
        var attrs = new GitLabMergeRequestEventDTO.ObjectAttributes(
            RAW_MR_ID,
            MR_IID,
            "Add awesome feature",
            "This MR adds an awesome feature",
            state,
            action,
            "feature/awesome-feature",
            "main",
            false,
            RAW_USER_ID,
            null,
            null,
            "2024-01-15T10:00:00Z",
            "2024-01-15T10:00:00Z",
            null,
            null,
            "https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5",
            null,
            null
        );
        return new GitLabMergeRequestEventDTO(
            "merge_request",
            confidential ? "confidential_merge_request" : "merge_request",
            createUser(),
            createProject(),
            attrs,
            List.of(new GitLabWebhookLabel(101L, "feature", "#0075ca")),
            null,
            null
        );
    }

    private GitLabMergeRequestEventDTO createEventWithState(String action, String state) {
        var attrs = new GitLabMergeRequestEventDTO.ObjectAttributes(
            RAW_MR_ID,
            MR_IID,
            "Add awesome feature",
            "This MR adds an awesome feature",
            state,
            action,
            "feature/awesome-feature",
            "main",
            false,
            RAW_USER_ID,
            null,
            null,
            "2024-01-15T10:00:00Z",
            "2024-01-15T10:00:00Z",
            null,
            null,
            "https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5",
            null,
            null
        );
        return new GitLabMergeRequestEventDTO(
            "merge_request",
            "merge_request",
            createUser(),
            createProject(),
            attrs,
            List.of(new GitLabWebhookLabel(101L, "feature", "#0075ca")),
            null,
            null
        );
    }

    private GitLabMergeRequestEventDTO createConfidentialEvent(String action, String state) {
        var attrs = new GitLabMergeRequestEventDTO.ObjectAttributes(
            RAW_MR_ID,
            MR_IID,
            "Secret MR",
            "Confidential description",
            state,
            action,
            "feature/secret",
            "main",
            false,
            RAW_USER_ID,
            null,
            null,
            "2024-01-15T10:00:00Z",
            "2024-01-15T10:00:00Z",
            null,
            null,
            "https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5",
            null,
            null
        );
        return new GitLabMergeRequestEventDTO(
            "merge_request",
            "confidential_merge_request",
            createUser(),
            createProject(),
            attrs,
            null,
            null,
            null
        );
    }

    private GitLabMergeRequestEventDTO createApprovalEvent(String action, String state) {
        var attrs = new GitLabMergeRequestEventDTO.ObjectAttributes(
            RAW_MR_ID,
            MR_IID,
            "Add awesome feature",
            "This MR adds an awesome feature",
            state,
            action,
            "feature/awesome-feature",
            "main",
            false,
            RAW_USER_ID,
            null,
            null,
            "2024-01-15T10:00:00Z",
            "2024-01-15T14:00:00Z",
            null,
            null,
            "https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5",
            null,
            null
        );
        return new GitLabMergeRequestEventDTO(
            "merge_request",
            "merge_request",
            createApproverUser(),
            createProject(),
            attrs,
            List.of(new GitLabWebhookLabel(101L, "feature", "#0075ca")),
            null,
            null
        );
    }

    private GitLabWebhookUser createUser() {
        return new GitLabWebhookUser(
            RAW_USER_ID,
            "testuser",
            "Test User",
            "https://gitlab.com/uploads/-/system/user/avatar/12345/avatar.png",
            null
        );
    }

    private GitLabWebhookUser createApproverUser() {
        return new GitLabWebhookUser(
            RAW_APPROVER_ID,
            "reviewer1",
            "Reviewer One",
            "https://gitlab.com/uploads/-/system/user/avatar/11111/avatar.png",
            null
        );
    }

    private de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookProject createProject() {
        return new de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookProject(
            278964L,
            "gitlab",
            "https://gitlab.com/gitlab-org/gitlab",
            "gitlab-org/gitlab"
        );
    }

    private GitLabMergeRequestProcessor.SyncMergeRequestData createSyncData() {
        return new GitLabMergeRequestProcessor.SyncMergeRequestData(
            "gid://gitlab/MergeRequest/999555",
            "5",
            "Add awesome feature",
            "This MR adds an awesome feature",
            "opened",
            false,
            null,
            null,
            false,
            "https://gitlab.com/gitlab-org/gitlab/-/merge_requests/5",
            "2024-01-15T10:00:00Z",
            "2024-01-15T10:00:00Z",
            null,
            null,
            1,
            10,
            2,
            3,
            "feature/awesome-feature",
            "main",
            null,
            null,
            false,
            0,
            "gid://gitlab/User/12345",
            "testuser",
            "Test User",
            "https://gitlab.com/uploads/avatar.png",
            "https://gitlab.com/testuser",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
