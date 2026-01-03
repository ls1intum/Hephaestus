package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO;
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
 * Integration tests for GitHubPullRequestReviewMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 */
@DisplayName("GitHub Pull Request Review Message Handler")
@Transactional
class GitHubPullRequestReviewMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubPullRequestReviewMessageHandler handler;

    @Autowired
    private PullRequestReviewRepository reviewRepository;

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
        assertThat(handler.getEventKey()).isEqualTo("pull_request_review");
    }

    @Test
    @DisplayName("Should create review on submitted event")
    void shouldCreateReviewOnSubmittedEvent() throws Exception {
        // Given
        GitHubPullRequestReviewEventDTO event = loadPayload("pull_request_review.submitted");

        // Create the PR that the review belongs to
        createTestPullRequest(event.pullRequest().getDatabaseId(), event.pullRequest().number());

        // Verify review doesn't exist initially
        assertThat(reviewRepository.findById(event.review().id())).isEmpty();

        // When
        handler.handleEvent(event);

        // Then
        assertThat(reviewRepository.findById(event.review().id()))
            .isPresent()
            .get()
            .satisfies(review -> {
                assertThat(review.getId()).isEqualTo(event.review().id());
                assertThat(review.getBody()).isEqualTo(event.review().body());
                assertThat(review.getHtmlUrl()).isEqualTo(event.review().htmlUrl());
            });
    }

    @Test
    @DisplayName("Should update review on edited event")
    void shouldUpdateReviewOnEditedEvent() throws Exception {
        // Given - first create the review
        GitHubPullRequestReviewEventDTO submitEvent = loadPayload("pull_request_review.submitted");
        createTestPullRequest(submitEvent.pullRequest().getDatabaseId(), submitEvent.pullRequest().number());
        handler.handleEvent(submitEvent);

        // Load edited event
        GitHubPullRequestReviewEventDTO editEvent = loadPayload("pull_request_review.edited");

        // When
        handler.handleEvent(editEvent);

        // Then
        assertThat(reviewRepository.findById(editEvent.review().id()))
            .isPresent()
            .get()
            .satisfies(review -> {
                assertThat(review.getBody()).isEqualTo(editEvent.review().body());
            });
    }

    @Test
    @DisplayName("Should handle dismissed event")
    void shouldHandleDismissedEvent() throws Exception {
        // Given - load dismissed event first to get the correct review ID
        GitHubPullRequestReviewEventDTO dismissEvent = loadPayload("pull_request_review.dismissed");
        createTestPullRequest(dismissEvent.pullRequest().getDatabaseId(), dismissEvent.pullRequest().number());

        // Create the review first (simulate submitted state) with the ID that will be dismissed
        PullRequest pr = pullRequestRepository.findById(dismissEvent.pullRequest().getDatabaseId()).orElseThrow();
        PullRequestReview existingReview = new PullRequestReview();
        existingReview.setId(dismissEvent.review().id());
        existingReview.setState(PullRequestReview.State.APPROVED);
        existingReview.setPullRequest(pr);
        existingReview.setDismissed(false);
        reviewRepository.save(existingReview);

        // When
        handler.handleEvent(dismissEvent);

        // Then - review should be marked as dismissed
        assertThat(reviewRepository.findById(dismissEvent.review().id()))
            .isPresent()
            .get()
            .satisfies(review -> {
                assertThat(review.isDismissed()).isTrue();
            });
    }

    private GitHubPullRequestReviewEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubPullRequestReviewEventDTO.class);
    }
}
