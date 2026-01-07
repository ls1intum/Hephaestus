package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
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
 * Integration tests for GitHubIssueCommentMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 */
@DisplayName("GitHub Issue Comment Message Handler")
@Transactional
class GitHubIssueCommentMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubIssueCommentMessageHandler handler;

    @Autowired
    private IssueCommentRepository commentRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Repository testRepository;
    private Issue testIssue;

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

    private void createTestIssue(Long issueId, int number) {
        testIssue = new Issue();
        testIssue.setId(issueId);
        testIssue.setNumber(number);
        testIssue.setTitle("Test Issue");
        testIssue.setState(Issue.State.OPEN);
        testIssue.setRepository(testRepository);
        testIssue.setCreatedAt(Instant.now());
        testIssue.setUpdatedAt(Instant.now());
        testIssue = issueRepository.save(testIssue);
    }

    @Test
    @DisplayName("Should return correct event key")
    void shouldReturnCorrectEventKey() {
        assertThat(handler.getEventType()).isEqualTo(GitHubEventType.ISSUE_COMMENT);
    }

    @Test
    @DisplayName("Should create comment on created event")
    void shouldCreateCommentOnCreatedEvent() throws Exception {
        // Given
        GitHubIssueCommentEventDTO event = loadPayload("issue_comment.created");

        // Create the issue that the comment belongs to
        createTestIssue(event.issue().getDatabaseId(), event.issue().number());

        // Verify comment doesn't exist initially
        assertThat(commentRepository.findById(event.comment().id())).isEmpty();

        // When
        handler.handleEvent(event);

        // Then
        assertThat(commentRepository.findById(event.comment().id()))
            .isPresent()
            .get()
            .satisfies(comment -> {
                assertThat(comment.getId()).isEqualTo(event.comment().id());
                assertThat(comment.getBody()).isEqualTo(event.comment().body());
                assertThat(comment.getHtmlUrl()).isEqualTo(event.comment().htmlUrl());
            });
    }

    @Test
    @DisplayName("Should update comment on edited event")
    void shouldUpdateCommentOnEditedEvent() throws Exception {
        // Given - first create the comment
        GitHubIssueCommentEventDTO createEvent = loadPayload("issue_comment.created");
        createTestIssue(createEvent.issue().getDatabaseId(), createEvent.issue().number());
        handler.handleEvent(createEvent);

        // Load edited event
        GitHubIssueCommentEventDTO editEvent = loadPayload("issue_comment.edited");

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
    @DisplayName("Should delete comment on deleted event")
    void shouldDeleteCommentOnDeletedEvent() throws Exception {
        // Given - first create the comment
        GitHubIssueCommentEventDTO createEvent = loadPayload("issue_comment.created");
        createTestIssue(createEvent.issue().getDatabaseId(), createEvent.issue().number());
        handler.handleEvent(createEvent);

        // Verify it exists
        assertThat(commentRepository.findById(createEvent.comment().id())).isPresent();

        // Load deleted event
        GitHubIssueCommentEventDTO deleteEvent = loadPayload("issue_comment.deleted");

        // When
        handler.handleEvent(deleteEvent);

        // Then
        assertThat(commentRepository.findById(deleteEvent.comment().id())).isEmpty();
    }

    private GitHubIssueCommentEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubIssueCommentEventDTO.class);
    }
}
