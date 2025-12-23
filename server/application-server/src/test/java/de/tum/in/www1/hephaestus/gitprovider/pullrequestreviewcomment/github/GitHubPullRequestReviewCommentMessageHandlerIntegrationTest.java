package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.dto.GitHubPullRequestReviewCommentEventDTO;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubPullRequestReviewCommentMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs (no hub4j dependency).
 */
@DisplayName("GitHub Pull Request Review Comment Message Handler")
@Transactional
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
    private ObjectMapper objectMapper;

    private Repository testRepository;
    private PullRequest testPullRequest;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

    private void setupTestData() {
        // Create organization
        Organization org = new Organization();
        org.setId(215361191L);
        org.setGithubId(215361191L);
        org.setLogin("HephaestusTest");
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
        org = organizationRepository.save(org);

        // Create repository
        testRepository = new Repository();
        testRepository.setId(1000663383L);
        testRepository.setName("TestRepository");
        testRepository.setNameWithOwner("HephaestusTest/TestRepository");
        testRepository.setHtmlUrl("https://github.com/HephaestusTest/TestRepository");
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
        assertThat(handler.getEventKey()).isEqualTo("pull_request_review_comment");
    }

    @Test
    @DisplayName("Should create review comment on created event")
    void shouldCreateReviewCommentOnCreatedEvent() throws Exception {
        // Given
        GitHubPullRequestReviewCommentEventDTO event = loadPayload("pull_request_review_comment.created");

        // Create the PR that the comment belongs to
        createTestPullRequest(event.pullRequest().getDatabaseId(), event.pullRequest().number());

        // Verify comment doesn't exist initially
        assertThat(commentRepository.findById(event.comment().id())).isEmpty();

        // When
        handler.handleEvent(event);

        // Then - verify comment is created with required fields
        assertThat(commentRepository.findById(event.comment().id()))
            .isPresent()
            .get()
            .satisfies(comment -> {
                assertThat(comment.getId()).isEqualTo(event.comment().id());
                assertThat(comment.getBody()).isEqualTo(event.comment().body());
                assertThat(comment.getPath()).isEqualTo(event.comment().path());
                // Verify thread is created (required FK)
                assertThat(comment.getThread()).isNotNull();
                assertThat(comment.getThread().getId()).isNotNull();
                // Verify required fields are populated
                assertThat(comment.getCommitId()).isNotEmpty();
                assertThat(comment.getSide()).isNotNull();
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
        assertThat(commentRepository.findById(editEvent.comment().id()))
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
        assertThat(commentRepository.findById(createEvent.comment().id())).isPresent();

        // Load deleted event
        GitHubPullRequestReviewCommentEventDTO deleteEvent = loadPayload("pull_request_review_comment.deleted");

        // When
        handler.handleEvent(deleteEvent);

        // Then
        assertThat(commentRepository.findById(deleteEvent.comment().id())).isEmpty();
    }

    private GitHubPullRequestReviewCommentEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubPullRequestReviewCommentEventDTO.class);
    }
}
