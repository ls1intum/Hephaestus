package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
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
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubIssueMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs (no hub4j dependency).
 */
@DisplayName("GitHub Issue Message Handler")
@Transactional
class GitHubIssueMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubIssueMessageHandler handler;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    void shouldReturnCorrectEventKey() {
        assertThat(handler.getEventKey()).isEqualTo("issues");
    }

    @Test
    void shouldPersistIssueOnOpenedEvent() throws Exception {
        // Setup: create repository and workspace
        setupTestData();

        // Load and parse the payload
        GitHubIssueEventDTO event = loadPayload("issues.opened");

        // Handle the event
        handler.handleEvent(event);

        // Verify the issue was persisted
        Issue issue = issueRepository.findById(event.issue().getDatabaseId()).orElse(null);
        assertThat(issue).isNotNull();
        assertThat(issue.getTitle()).isEqualTo(event.issue().title());
        assertThat(issue.getState()).isEqualTo(Issue.State.OPEN);
        assertThat(issue.getNumber()).isEqualTo(event.issue().number());
    }

    @Test
    void shouldDeleteIssueOnDeletedEvent() throws Exception {
        // Setup: create repository and issue
        setupTestData();

        // First create an issue
        GitHubIssueEventDTO openedEvent = loadPayload("issues.opened");
        handler.handleEvent(openedEvent);

        // Verify issue exists
        assertThat(issueRepository.existsById(openedEvent.issue().getDatabaseId())).isTrue();

        // Now delete it
        GitHubIssueEventDTO deletedEvent = loadPayload("issues.deleted");
        handler.handleEvent(deletedEvent);

        // Verify issue was deleted
        assertThat(issueRepository.existsById(deletedEvent.issue().getDatabaseId())).isFalse();
    }

    @Test
    void shouldHandleClosedEvent() throws Exception {
        // Setup: create repository and issue
        setupTestData();

        // First create an issue
        GitHubIssueEventDTO openedEvent = loadPayload("issues.opened");
        handler.handleEvent(openedEvent);

        // Now close it
        GitHubIssueEventDTO closedEvent = loadPayload("issues.closed");
        handler.handleEvent(closedEvent);

        // Verify issue state
        Issue issue = issueRepository.findById(closedEvent.issue().getDatabaseId()).orElse(null);
        assertThat(issue).isNotNull();
        assertThat(issue.getState()).isEqualTo(Issue.State.CLOSED);
    }

    @Test
    void shouldHandleReopenedEvent() throws Exception {
        // Setup
        setupTestData();

        // Create and close issue
        GitHubIssueEventDTO openedEvent = loadPayload("issues.opened");
        handler.handleEvent(openedEvent);
        GitHubIssueEventDTO closedEvent = loadPayload("issues.closed");
        handler.handleEvent(closedEvent);

        // Reopen it
        GitHubIssueEventDTO reopenedEvent = loadPayload("issues.reopened");
        handler.handleEvent(reopenedEvent);

        // Verify issue state
        Issue issue = issueRepository.findById(reopenedEvent.issue().getDatabaseId()).orElse(null);
        assertThat(issue).isNotNull();
        assertThat(issue.getState()).isEqualTo(Issue.State.OPEN);
    }

    // Helper methods

    private GitHubIssueEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubIssueEventDTO.class);
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
        Repository repo = new Repository();
        repo.setId(1000663383L);
        repo.setName("TestRepository");
        repo.setNameWithOwner("HephaestusTest/TestRepository");
        repo.setHtmlUrl("https://github.com/HephaestusTest/TestRepository");
        repo.setVisibility(Repository.Visibility.PUBLIC);
        repo.setDefaultBranch("main");
        repo.setCreatedAt(Instant.now());
        repo.setUpdatedAt(Instant.now());
        repo.setPushedAt(Instant.now());
        repo.setOrganization(org);
        repo = repositoryRepository.save(repo);

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

    private Set<String> labelNames(Issue issue) {
        return issue.getLabels().stream().map(l -> l.getName()).collect(Collectors.toSet());
    }
}
