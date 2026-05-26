package de.tum.cit.aet.hephaestus.integration.github.pullrequestreviewcomment;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.common.GitProvider;
import de.tum.cit.aet.hephaestus.integration.scm.common.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.scm.common.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubEventType;
import de.tum.cit.aet.hephaestus.integration.scm.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.integration.scm.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.cit.aet.hephaestus.integration.github.pullrequestreviewcomment.dto.GitHubPullRequestReviewCommentEventDTO;
import de.tum.cit.aet.hephaestus.integration.scm.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for GitHubPullRequestReviewCommentMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 */
@DisplayName("GitHub Pull Request Review Comment Message Handler")
class GitHubPullRequestReviewCommentMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubPullRequestReviewCommentMessageHandler handler;

    @Autowired
    private PullRequestReviewCommentRepository commentRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private GitProvider gitProvider;
    private Repository testRepository;
    private PullRequest testPullRequest;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

    private void setupTestData() {
        // Create GitHub provider
        gitProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));

        // Create organization
        Organization org = new Organization();
        org.setNativeId(215361191L);
        org.setLogin("HephaestusTest");
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
        org.setProvider(gitProvider);
        org = organizationRepository.save(org);

        // Create repository
        testRepository = new Repository();
        testRepository.setNativeId(1000663383L);
        testRepository.setName("TestRepository");
        testRepository.setNameWithOwner("HephaestusTest/TestRepository");
        testRepository.setHtmlUrl("https://github.com/HephaestusTest/TestRepository");
        testRepository.setVisibility(Repository.Visibility.PUBLIC);
        testRepository.setDefaultBranch("main");
        testRepository.setCreatedAt(Instant.now());
        testRepository.setUpdatedAt(Instant.now());
        testRepository.setPushedAt(Instant.now());
        testRepository.setOrganization(org);
        testRepository.setProvider(gitProvider);
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
        testPullRequest.setNativeId(prId);
        testPullRequest.setNumber(number);
        testPullRequest.setTitle("Test Pull Request");
        testPullRequest.setState(PullRequest.State.OPEN);
        testPullRequest.setRepository(testRepository);
        testPullRequest.setCreatedAt(Instant.now());
        testPullRequest.setUpdatedAt(Instant.now());
        testPullRequest.setProvider(gitProvider);
        testPullRequest = pullRequestRepository.save(testPullRequest);
    }

    @Test
    @DisplayName("Should return correct event key")
    void shouldReturnCorrectEventKey() {
        assertThat(handler.key().eventType()).isEqualTo("repository.pull_request_review_comment");
    }

    @Test
    @DisplayName("Should create review comment on created event")
    void shouldCreateReviewCommentOnCreatedEvent() throws Exception {
        // Given
        GitHubPullRequestReviewCommentEventDTO event = loadPayload("pull_request_review_comment.created");

        // Create the PR that the comment belongs to
        createTestPullRequest(event.pullRequest().getDatabaseId(), event.pullRequest().number());

        // Verify comment doesn't exist initially
        assertThat(commentRepository.findByNativeIdAndProviderId(event.comment().id(), gitProvider.getId())).isEmpty();

        // When
        handler.handleEvent(event);

        // Then - verify comment is created with required fields
        assertThat(commentRepository.findByNativeIdAndProviderId(event.comment().id(), gitProvider.getId()))
            .isPresent()
            .get()
            .satisfies(comment -> {
                assertThat(comment.getNativeId()).isEqualTo(event.comment().id());
                assertThat(comment.getBody()).isEqualTo(event.comment().body());
                assertThat(comment.getPath()).isEqualTo(event.comment().path());
                // Verify thread is created (required FK)
                assertThat(comment.getThread()).isNotNull();
                assertThat(comment.getThread().getId()).isNotNull();
                // Verify required fields are populated
                assertThat(comment.getCommitId()).isNotEmpty();
            });
    }

    @Test
    @DisplayName("Should update review comment on edited event")
    void shouldUpdateReviewCommentOnEditedEvent() throws Exception {
        // Given - first create the comment
        GitHubPullRequestReviewCommentEventDTO createEvent = loadPayload("pull_request_review_comment.created");
        createTestPullRequest(createEvent.pullRequest().getDatabaseId(), createEvent.pullRequest().number());
        handler.handleEvent(createEvent);

        // Load edited event
        GitHubPullRequestReviewCommentEventDTO editEvent = loadPayload("pull_request_review_comment.edited");

        // When
        handler.handleEvent(editEvent);

        // Then
        assertThat(commentRepository.findByNativeIdAndProviderId(editEvent.comment().id(), gitProvider.getId()))
            .isPresent()
            .get()
            .satisfies(comment -> {
                assertThat(comment.getBody()).isEqualTo(editEvent.comment().body());
            });
    }

    @Test
    @DisplayName("Should delete review comment on deleted event")
    void shouldDeleteReviewCommentOnDeletedEvent() throws Exception {
        // Given - first create the comment
        GitHubPullRequestReviewCommentEventDTO createEvent = loadPayload("pull_request_review_comment.created");
        createTestPullRequest(createEvent.pullRequest().getDatabaseId(), createEvent.pullRequest().number());
        handler.handleEvent(createEvent);

        // Verify it exists
        assertThat(
            commentRepository.findByNativeIdAndProviderId(createEvent.comment().id(), gitProvider.getId())
        ).isPresent();

        // Load deleted event
        GitHubPullRequestReviewCommentEventDTO deleteEvent = loadPayload("pull_request_review_comment.deleted");

        // When
        handler.handleEvent(deleteEvent);

        // Then
        assertThat(
            commentRepository.findByNativeIdAndProviderId(deleteEvent.comment().id(), gitProvider.getId())
        ).isEmpty();
    }

    private GitHubPullRequestReviewCommentEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubPullRequestReviewCommentEventDTO.class);
    }
}
