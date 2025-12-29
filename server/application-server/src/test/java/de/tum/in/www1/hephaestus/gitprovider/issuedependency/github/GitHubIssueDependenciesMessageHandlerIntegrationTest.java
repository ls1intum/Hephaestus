package de.tum.in.www1.hephaestus.gitprovider.issuedependency.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler.GitHubMessageDomain;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.dto.GitHubIssueDependenciesEventDTO;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubIssueDependenciesMessageHandler.
 * <p>
 * Tests the full webhook handling flow for issue dependency events using JSON
 * fixtures parsed directly into DTOs. Verifies:
 * - Correct routing of webhook actions (added/removed)
 * - Issue creation when blocked/blocking issues don't exist yet
 * - Dependency relationship persistence (blocked_by/blocking)
 * - Edge cases in event handling
 */
@DisplayName("GitHub Issue Dependencies Message Handler")
@Transactional
class GitHubIssueDependenciesMessageHandlerIntegrationTest extends BaseIntegrationTest {

    // IDs from the actual GitHub webhook fixtures
    private static final Long FIXTURE_ORG_ID = 215361191L;
    private static final Long FIXTURE_REPO_ID = 998279771L;
    private static final String FIXTURE_ORG_LOGIN = "HephaestusTest";
    private static final String FIXTURE_REPO_FULL_NAME = "HephaestusTest/TestRepository";

    // Issue IDs from fixtures
    private static final Long BLOCKED_ISSUE_ID = 3578496080L; // Issue #20 - the blocked issue
    private static final Long BLOCKING_ISSUE_ID = 3578496081L; // Issue #21 - the blocking issue

    @Autowired
    private GitHubIssueDependenciesMessageHandler handler;

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

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

    // ==================== Event Key Tests ====================

    @Nested
    @DisplayName("Event Key")
    class EventKey {

        @Test
        @DisplayName("Should return 'issue_dependencies' as event key")
        void shouldReturnCorrectEventKey() {
            assertThat(handler.getEventKey()).isEqualTo("issue_dependencies");
        }

        @Test
        @DisplayName("Should return REPOSITORY domain (default)")
        void shouldReturnRepositoryDomain() {
            // IssueDependencies uses the default REPOSITORY domain
            assertThat(handler.getDomain()).isEqualTo(GitHubMessageDomain.REPOSITORY);
        }
    }

    // ==================== Added Action Tests ====================

    @Nested
    @DisplayName("Added Action")
    class AddedAction {

        @Test
        @DisplayName("Should create dependency relationship on 'added' action")
        void shouldCreateDependencyOnAdded() throws Exception {
            // Given
            GitHubIssueDependenciesEventDTO event = loadPayload("issue_dependencies.added");

            // When
            handler.handleEvent(event);

            // Then - both issues should be created
            Issue blockedIssue = issueRepository.findById(BLOCKED_ISSUE_ID).orElse(null);
            Issue blockingIssue = issueRepository.findById(BLOCKING_ISSUE_ID).orElse(null);

            assertThat(blockedIssue).isNotNull();
            assertThat(blockingIssue).isNotNull();

            // Verify the dependency relationship was created
            assertThat(blockedIssue.getBlockedBy()).hasSize(1).extracting(Issue::getId).contains(BLOCKING_ISSUE_ID);
        }

        @Test
        @DisplayName("Should add to existing dependencies on 'added' action")
        void shouldAddToExistingDependencies() throws Exception {
            // Given - pre-create the issues without dependencies
            Repository repo = repositoryRepository.findById(FIXTURE_REPO_ID).orElseThrow();

            Issue existingBlockedIssue = new Issue();
            existingBlockedIssue.setId(BLOCKED_ISSUE_ID);
            existingBlockedIssue.setNumber(20);
            existingBlockedIssue.setTitle("Existing blocked issue");
            existingBlockedIssue.setState(Issue.State.OPEN);
            existingBlockedIssue.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME + "/issues/20");
            existingBlockedIssue.setRepository(repo);
            issueRepository.save(existingBlockedIssue);

            Issue existingBlockingIssue = new Issue();
            existingBlockingIssue.setId(BLOCKING_ISSUE_ID);
            existingBlockingIssue.setNumber(21);
            existingBlockingIssue.setTitle("Existing blocking issue");
            existingBlockingIssue.setState(Issue.State.OPEN);
            existingBlockingIssue.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME + "/issues/21");
            existingBlockingIssue.setRepository(repo);
            issueRepository.save(existingBlockingIssue);

            GitHubIssueDependenciesEventDTO event = loadPayload("issue_dependencies.added");

            // When
            handler.handleEvent(event);

            // Then - verify relationship was added
            Issue blockedIssue = issueRepository.findById(BLOCKED_ISSUE_ID).orElse(null);
            assertThat(blockedIssue).isNotNull();
            assertThat(blockedIssue.getBlockedBy()).hasSize(1).extracting(Issue::getId).contains(BLOCKING_ISSUE_ID);
        }

        @Test
        @DisplayName("Should handle 'added' action idempotently")
        void shouldHandleAddedIdempotently() throws Exception {
            // Given
            GitHubIssueDependenciesEventDTO event = loadPayload("issue_dependencies.added");

            // When - process the same event twice
            handler.handleEvent(event);
            handler.handleEvent(event);

            // Then - should still only have one dependency
            Issue blockedIssue = issueRepository.findById(BLOCKED_ISSUE_ID).orElse(null);
            assertThat(blockedIssue).isNotNull();
            assertThat(blockedIssue.getBlockedBy()).hasSize(1);
        }
    }

    // ==================== Removed Action Tests ====================

    @Nested
    @DisplayName("Removed Action")
    class RemovedAction {

        @Test
        @DisplayName("Should remove dependency relationship on 'removed' action")
        void shouldRemoveDependencyOnRemoved() throws Exception {
            // Given - first add the dependency
            GitHubIssueDependenciesEventDTO addEvent = loadPayload("issue_dependencies.added");
            handler.handleEvent(addEvent);

            // Verify it was added
            Issue blockedIssue = issueRepository.findById(BLOCKED_ISSUE_ID).orElse(null);
            assertThat(blockedIssue).isNotNull();
            assertThat(blockedIssue.getBlockedBy()).hasSize(1);

            // When - remove the dependency
            GitHubIssueDependenciesEventDTO removeEvent = loadPayload("issue_dependencies.removed");
            handler.handleEvent(removeEvent);

            // Then - dependency should be removed
            blockedIssue = issueRepository.findById(BLOCKED_ISSUE_ID).orElse(null);
            assertThat(blockedIssue).isNotNull();
            assertThat(blockedIssue.getBlockedBy()).isEmpty();
        }

        @Test
        @DisplayName("Should handle 'removed' action gracefully when dependency doesn't exist")
        void shouldHandleRemoveOfNonExistentDependency() throws Exception {
            // Given - create issues but no dependency relationship
            Repository repo = repositoryRepository.findById(FIXTURE_REPO_ID).orElseThrow();

            Issue blockedIssue = new Issue();
            blockedIssue.setId(BLOCKED_ISSUE_ID);
            blockedIssue.setNumber(20);
            blockedIssue.setTitle("Blocked issue without dependencies");
            blockedIssue.setState(Issue.State.OPEN);
            blockedIssue.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME + "/issues/20");
            blockedIssue.setRepository(repo);
            issueRepository.save(blockedIssue);

            Issue blockingIssue = new Issue();
            blockingIssue.setId(BLOCKING_ISSUE_ID);
            blockingIssue.setNumber(21);
            blockingIssue.setTitle("Blocking issue");
            blockingIssue.setState(Issue.State.OPEN);
            blockingIssue.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME + "/issues/21");
            blockingIssue.setRepository(repo);
            issueRepository.save(blockingIssue);

            GitHubIssueDependenciesEventDTO removeEvent = loadPayload("issue_dependencies.removed");

            // When - should not throw
            handler.handleEvent(removeEvent);

            // Then - issues should still exist
            assertThat(issueRepository.existsById(BLOCKED_ISSUE_ID)).isTrue();
            assertThat(issueRepository.existsById(BLOCKING_ISSUE_ID)).isTrue();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null blocked issue gracefully")
        void shouldHandleNullBlockedIssueGracefully() {
            // Given - event with null blocked_issue
            GitHubIssueDependenciesEventDTO event = new GitHubIssueDependenciesEventDTO(
                "added",
                null,
                null,
                null,
                null
            );

            // When - should not throw
            handler.handleEvent(event);
            // Then - handler logs warning but doesn't crash
        }

        @Test
        @DisplayName("Should handle null blocking issue gracefully")
        void shouldHandleNullBlockingIssueGracefully() throws Exception {
            // Given - load a valid event and create with null blocking issue
            GitHubIssueDependenciesEventDTO baseEvent = loadPayload("issue_dependencies.added");
            GitHubIssueDependenciesEventDTO event = new GitHubIssueDependenciesEventDTO(
                "added",
                baseEvent.blockedIssue(),
                null, // blocking issue is null
                baseEvent.repository(),
                baseEvent.sender()
            );

            // When - should not throw
            handler.handleEvent(event);
            // Then - handler handles null gracefully
        }

        @Test
        @DisplayName("Should handle unknown action gracefully")
        void shouldHandleUnknownActionGracefully() throws Exception {
            // Given - load a valid event and modify the action
            GitHubIssueDependenciesEventDTO baseEvent = loadPayload("issue_dependencies.added");
            GitHubIssueDependenciesEventDTO event = new GitHubIssueDependenciesEventDTO(
                "unknown_action",
                baseEvent.blockedIssue(),
                baseEvent.blockingIssue(),
                baseEvent.repository(),
                baseEvent.sender()
            );

            // When - should not throw
            handler.handleEvent(event);

            // Then - issues are still processed (created/updated)
            assertThat(issueRepository.existsById(BLOCKED_ISSUE_ID)).isTrue();
            assertThat(issueRepository.existsById(BLOCKING_ISSUE_ID)).isTrue();
        }

        @Test
        @DisplayName("Should skip processing when workspace context cannot be created")
        void shouldSkipProcessingWithoutWorkspaceContext() throws Exception {
            // Given - delete the workspace and organization to ensure no context can be created
            workspaceRepository.deleteAll();
            // Also delete the repository which breaks the chain
            issueRepository.deleteAll();
            repositoryRepository.deleteAll();
            organizationRepository.deleteAll();

            GitHubIssueDependenciesEventDTO event = loadPayload("issue_dependencies.added");

            // When - should not throw
            handler.handleEvent(event);

            // Then - issues should NOT be created (no workspace/repository context)
            assertThat(issueRepository.existsById(BLOCKED_ISSUE_ID)).isFalse();
        }
    }

    // ==================== Setup Helpers ====================

    private void setupTestData() {
        // Create organization matching fixture data
        Organization org = new Organization();
        org.setId(FIXTURE_ORG_ID);
        org.setGithubId(FIXTURE_ORG_ID);
        org.setLogin(FIXTURE_ORG_LOGIN);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/" + FIXTURE_ORG_ID + "?v=4");
        org = organizationRepository.save(org);

        // Create repository matching fixture data
        Repository repo = new Repository();
        repo.setId(FIXTURE_REPO_ID);
        repo.setName("TestRepository");
        repo.setNameWithOwner(FIXTURE_REPO_FULL_NAME);
        repo.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME);
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
        workspace.setAccountLogin(FIXTURE_ORG_LOGIN);
        workspace.setAccountType(AccountType.ORG);
        workspaceRepository.save(workspace);
    }

    private GitHubIssueDependenciesEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubIssueDependenciesEventDTO.class);
    }
}
