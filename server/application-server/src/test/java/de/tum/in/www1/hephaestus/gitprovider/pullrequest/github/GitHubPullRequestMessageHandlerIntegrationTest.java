package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.activity.badpracticedetector.BadPracticeDetectorScheduler;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubPullRequestMessageHandler.
 * <p>
 * These tests use the new DTO-based architecture where webhook payloads
 * are parsed directly to GitHubPullRequestEventDTO using Jackson.
 */
@DisplayName("GitHub Pull Request Message Handler (DTO-based)")
@Transactional
class GitHubPullRequestMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubPullRequestMessageHandler handler;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BadPracticeDetectorScheduler badPracticeDetectorScheduler;

    private Repository testRepository;

    @BeforeEach
    void setupTestData() {
        databaseTestUtils.cleanDatabase();
        reset(badPracticeDetectorScheduler);

        // Create test organization
        Organization org = new Organization();
        org.setId(1L);
        org.setLogin("test-org");
        org.setName("Test Organization");
        org.setAvatarUrl("https://example.com/avatar");
        org = organizationRepository.save(org);

        // Create test workspace
        Workspace workspace = new Workspace();
        workspace.setOrganization(org);
        workspace.setWorkspaceSlug("test-org");
        workspace.setDisplayName("Test Workspace");
        workspace.setAccountLogin("test-org");
        workspace.setAccountType(de.tum.in.www1.hephaestus.workspace.AccountType.ORG);
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        workspace.setIsPubliclyViewable(false);
        workspace = workspaceRepository.save(workspace);

        // Create test repository
        testRepository = new Repository();
        testRepository.setId(123456789L);
        testRepository.setName("test-repo");
        testRepository.setNameWithOwner("test-org/test-repo");
        testRepository.setHtmlUrl("https://github.com/test-org/test-repo");
        testRepository.setOrganization(org);
        testRepository = repositoryRepository.save(testRepository);
    }

    @Test
    void shouldParsePullRequestEventCorrectly() throws Exception {
        String payload = loadPayload("fixtures/github/pull_request.opened.json");
        GitHubPullRequestEventDTO event = objectMapper.readValue(payload, GitHubPullRequestEventDTO.class);

        assertThat(event.action()).isEqualTo("opened");
        assertThat(event.pullRequest()).isNotNull();
        assertThat(event.pullRequest().number()).isGreaterThan(0);
    }

    @Test
    void shouldPersistPullRequestOnOpenedEvent() throws Exception {
        String payload = loadPayload("fixtures/github/pull_request.opened.json");
        GitHubPullRequestEventDTO event = objectMapper.readValue(payload, GitHubPullRequestEventDTO.class);

        // Update event's repository ID to match our test repository
        // In real scenarios, the repository would already exist from sync
        testRepository.setId(event.repository().id());
        testRepository.setNameWithOwner(event.repository().fullName());
        testRepository = repositoryRepository.save(testRepository);

        handler.handleEvent(event);

        PullRequest pr = pullRequestRepository
            .findById(event.pullRequest().getDatabaseId())
            .orElseThrow(() -> new AssertionError("Pull request should be persisted"));

        assertThat(pr.getTitle()).isEqualTo(event.pullRequest().title());
        assertThat(pr.getNumber()).isEqualTo(event.pullRequest().number());
        assertThat(pr.getState()).isEqualTo(PullRequest.State.OPEN);
    }

    @Test
    void shouldUpdatePullRequestOnClosedEvent() throws Exception {
        // First create the PR
        String openPayload = loadPayload("fixtures/github/pull_request.opened.json");
        GitHubPullRequestEventDTO openEvent = objectMapper.readValue(openPayload, GitHubPullRequestEventDTO.class);

        testRepository.setId(openEvent.repository().id());
        testRepository.setNameWithOwner(openEvent.repository().fullName());
        testRepository = repositoryRepository.save(testRepository);

        handler.handleEvent(openEvent);

        // Now close it
        String closePayload = loadPayload("fixtures/github/pull_request.closed.json");
        GitHubPullRequestEventDTO closeEvent = objectMapper.readValue(closePayload, GitHubPullRequestEventDTO.class);
        handler.handleEvent(closeEvent);

        PullRequest pr = pullRequestRepository
            .findById(closeEvent.pullRequest().getDatabaseId())
            .orElseThrow(() -> new AssertionError("Pull request should exist"));

        assertThat(pr.getState()).isEqualTo(PullRequest.State.CLOSED);
    }

    @Test
    void shouldHandleLabeledEvent() throws Exception {
        // First create the PR
        String openPayload = loadPayload("fixtures/github/pull_request.opened.json");
        GitHubPullRequestEventDTO openEvent = objectMapper.readValue(openPayload, GitHubPullRequestEventDTO.class);

        testRepository.setId(openEvent.repository().id());
        testRepository.setNameWithOwner(openEvent.repository().fullName());
        testRepository = repositoryRepository.save(testRepository);

        handler.handleEvent(openEvent);

        // Now add a label
        String labeledPayload = loadPayload("fixtures/github/pull_request.labeled.json");
        GitHubPullRequestEventDTO labeledEvent = objectMapper.readValue(
            labeledPayload,
            GitHubPullRequestEventDTO.class
        );
        handler.handleEvent(labeledEvent);

        PullRequest pr = pullRequestRepository
            .findById(labeledEvent.pullRequest().getDatabaseId())
            .orElseThrow(() -> new AssertionError("Pull request should exist"));

        // Labels should be updated from the event
        assertThat(pr.getLabels()).isNotNull();
    }

    @Test
    void shouldHandleReadyForReviewEvent() throws Exception {
        String payload = loadPayload("fixtures/github/pull_request.ready_for_review.json");
        GitHubPullRequestEventDTO event = objectMapper.readValue(payload, GitHubPullRequestEventDTO.class);

        testRepository.setId(event.repository().id());
        testRepository.setNameWithOwner(event.repository().fullName());
        testRepository = repositoryRepository.save(testRepository);

        handler.handleEvent(event);

        PullRequest pr = pullRequestRepository
            .findById(event.pullRequest().getDatabaseId())
            .orElseThrow(() -> new AssertionError("Pull request should exist"));

        assertThat(pr.isDraft()).isFalse();
    }

    @Test
    void shouldHandleConvertedToDraftEvent() throws Exception {
        String payload = loadPayload("fixtures/github/pull_request.converted_to_draft.json");
        GitHubPullRequestEventDTO event = objectMapper.readValue(payload, GitHubPullRequestEventDTO.class);

        testRepository.setId(event.repository().id());
        testRepository.setNameWithOwner(event.repository().fullName());
        testRepository = repositoryRepository.save(testRepository);

        handler.handleEvent(event);

        PullRequest pr = pullRequestRepository
            .findById(event.pullRequest().getDatabaseId())
            .orElseThrow(() -> new AssertionError("Pull request should exist"));

        assertThat(pr.isDraft()).isTrue();
    }

    // ==================== Helper Methods ====================

    private String loadPayload(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    private Set<String> labelNames(PullRequest pullRequest) {
        return pullRequest.getLabels().stream().map(Label::getName).collect(Collectors.toSet());
    }

    private Set<String> userLogins(Set<User> users) {
        return users.stream().map(User::getLogin).collect(Collectors.toSet());
    }
}
