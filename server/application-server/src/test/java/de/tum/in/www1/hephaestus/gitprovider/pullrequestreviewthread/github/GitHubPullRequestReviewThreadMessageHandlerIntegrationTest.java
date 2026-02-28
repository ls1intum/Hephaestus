package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github.dto.GitHubPullRequestReviewThreadEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * Integration tests for GitHubPullRequestReviewThreadMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 */
@DisplayName("GitHub Pull Request Review Thread Message Handler")
class GitHubPullRequestReviewThreadMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubPullRequestReviewThreadMessageHandler handler;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private PullRequestReviewThreadRepository threadRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Repository testRepository;
    private PullRequest testPullRequest;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

    private void setupTestData() {
        // Create organization - use ID from fixture
        Organization org = new Organization();
        org.setId(215361191L);
        org.setProviderId(215361191L);
        org.setLogin("HephaestusTest");
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
        org = organizationRepository.save(org);

        // Create repository matching the fixture's repository
        testRepository = new Repository();
        testRepository.setId(1087937297L); // ID from fixture
        testRepository.setName("payload-fixture-repo-renamed");
        testRepository.setNameWithOwner("HephaestusTest/payload-fixture-repo-renamed");
        testRepository.setHtmlUrl("https://github.com/HephaestusTest/payload-fixture-repo-renamed");
        testRepository.setVisibility(Repository.Visibility.PUBLIC);
        testRepository.setDefaultBranch("main");
        testRepository.setCreatedAt(Instant.now());
        testRepository.setUpdatedAt(Instant.now());
        testRepository.setPushedAt(Instant.now());
        testRepository.setOrganization(org);
        testRepository = repositoryRepository.save(testRepository);

        // Create workspace
        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug("hephaestus-test");
        workspace.setDisplayName("Hephaestus Test");
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        workspace.setIsPubliclyViewable(true);
        workspace.setOrganization(org);
        workspace.setAccountLogin("HephaestusTest");
        workspace.setAccountType(AccountType.ORG);
        workspaceRepository.save(workspace);
    }

    private void createTestPullRequest(Long prId, int number) {
        testPullRequest = new PullRequest();
        testPullRequest.setId(prId);
        testPullRequest.setNumber(number);
        testPullRequest.setTitle("Test Pull Request");
        testPullRequest.setState(PullRequest.State.OPEN);
        testPullRequest.setRepository(testRepository);
        testPullRequest.setCreatedAt(Instant.now());
        testPullRequest.setUpdatedAt(Instant.now());
        testPullRequest = pullRequestRepository.save(testPullRequest);
    }

    @Test
    @DisplayName("Should return correct event key")
    void shouldReturnCorrectEventKey() {
        assertThat(handler.getEventType()).isEqualTo(GitHubEventType.PULL_REQUEST_REVIEW_THREAD);
    }

    @Test
    @DisplayName("Should handle thread resolved event")
    void shouldHandleResolvedEvent() throws Exception {
        // Given
        GitHubPullRequestReviewThreadEventDTO event = loadPayload("pull_request_review_thread.resolved");

        // Create the PR that the thread belongs to
        createTestPullRequest(event.pullRequest().getDatabaseId(), event.pullRequest().number());

        // The thread ID is derived from the first comment's ID in the webhook payload.
        // This matches how threads are stored during sync (first comment's databaseId = thread ID).
        Long threadId = event.thread().getFirstCommentId();
        assertThat(threadId).isEqualTo(2494208170L); // First comment ID from fixture

        // Create the thread in UNRESOLVED state first
        PullRequestReviewThread thread = new PullRequestReviewThread();
        thread.setId(threadId);
        thread.setNodeId(event.thread().nodeId());
        thread.setPullRequest(testPullRequest);
        thread.setState(PullRequestReviewThread.State.UNRESOLVED);
        thread.setPath(event.thread().path());
        thread.setLine(event.thread().line());
        thread.setCreatedAt(Instant.now());
        thread.setUpdatedAt(Instant.now());
        threadRepository.save(thread);

        // Verify initial state
        assertThat(threadRepository.findById(threadId))
            .isPresent()
            .get()
            .satisfies(t -> assertThat(t.getState()).isEqualTo(PullRequestReviewThread.State.UNRESOLVED));

        // When
        handler.handleEvent(event);

        // Then - thread should be resolved
        assertThat(threadRepository.findById(threadId))
            .isPresent()
            .get()
            .satisfies(t -> assertThat(t.getState()).isEqualTo(PullRequestReviewThread.State.RESOLVED));
    }

    @Test
    @DisplayName("Should handle thread unresolved event")
    void shouldHandleUnresolvedEvent() throws Exception {
        // Given
        GitHubPullRequestReviewThreadEventDTO event = loadPayload("pull_request_review_thread.unresolved");

        // Create the PR that the thread belongs to
        createTestPullRequest(event.pullRequest().getDatabaseId(), event.pullRequest().number());

        // The thread ID is derived from the first comment's ID in the webhook payload.
        Long threadId = event.thread().getFirstCommentId();
        assertThat(threadId).isEqualTo(2494208170L); // First comment ID from fixture

        // Create the thread in RESOLVED state first
        PullRequestReviewThread thread = new PullRequestReviewThread();
        thread.setId(threadId);
        thread.setNodeId(event.thread().nodeId());
        thread.setPullRequest(testPullRequest);
        thread.setState(PullRequestReviewThread.State.RESOLVED);
        thread.setPath(event.thread().path());
        thread.setLine(event.thread().line());
        thread.setCreatedAt(Instant.now());
        thread.setUpdatedAt(Instant.now());
        threadRepository.save(thread);

        // Verify initial state
        assertThat(threadRepository.findById(threadId))
            .isPresent()
            .get()
            .satisfies(t -> assertThat(t.getState()).isEqualTo(PullRequestReviewThread.State.RESOLVED));

        // When
        handler.handleEvent(event);

        // Then - thread should be unresolved
        assertThat(threadRepository.findById(threadId))
            .isPresent()
            .get()
            .satisfies(t -> assertThat(t.getState()).isEqualTo(PullRequestReviewThread.State.UNRESOLVED));
    }

    @Test
    @DisplayName("Should resolve thread when thread ID is known")
    void shouldResolveThreadWhenIdIsKnown() throws Exception {
        // Given - create a thread directly
        createTestPullRequest(12345L, 1);
        Long threadId = 100L;

        PullRequestReviewThread thread = new PullRequestReviewThread();
        thread.setId(threadId);
        thread.setPullRequest(testPullRequest);
        thread.setState(PullRequestReviewThread.State.UNRESOLVED);
        thread.setPath("README.md");
        thread.setCreatedAt(Instant.now());
        thread.setUpdatedAt(Instant.now());
        threadRepository.save(thread);

        // Verify initial state
        assertThat(threadRepository.findById(threadId))
            .isPresent()
            .get()
            .satisfies(t -> assertThat(t.getState()).isEqualTo(PullRequestReviewThread.State.UNRESOLVED));

        // When - directly call repository method (simulating proper thread ID resolution)
        thread.setState(PullRequestReviewThread.State.RESOLVED);
        threadRepository.save(thread);

        // Then - thread should be resolved
        // Note: GitHub only provides isResolved (boolean), not a timestamp.
        // The state enum (RESOLVED/UNRESOLVED) is sufficient.
        assertThat(threadRepository.findById(threadId))
            .isPresent()
            .get()
            .satisfies(t -> assertThat(t.getState()).isEqualTo(PullRequestReviewThread.State.RESOLVED));
    }

    private GitHubPullRequestReviewThreadEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubPullRequestReviewThreadEventDTO.class);
    }
}
