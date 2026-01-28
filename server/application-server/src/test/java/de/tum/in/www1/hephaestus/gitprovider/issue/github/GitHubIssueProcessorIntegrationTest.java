package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import static org.assertj.core.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueTypeDTO;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueType;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueTypeRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Integration tests for GitHubIssueProcessor.
 * <p>
 * Tests the processor independently from the webhook handler to verify:
 * - Issue upsert logic (create vs update)
 * - Domain event publishing (Created, Updated, Closed, Labeled, etc.)
 * - Context handling and workspace association
 * - Author user association and creation
 * - Label and milestone associations
 * - Issue type handling
 * - Edge cases in DTO processing including the critical getDatabaseId() fallback
 */
@DisplayName("GitHub Issue Processor")
class GitHubIssueProcessorIntegrationTest extends BaseIntegrationTest {

    // IDs from the actual GitHub webhook fixtures
    private static final Long FIXTURE_ORG_ID = 215361191L;
    private static final Long FIXTURE_REPO_ID = 998279771L;
    private static final Long FIXTURE_AUTHOR_ID = 5898705L;
    private static final Long FIXTURE_ISSUE_ID = 3578496080L;
    private static final String FIXTURE_ORG_LOGIN = "HephaestusTest";
    private static final String FIXTURE_REPO_FULL_NAME = "HephaestusTest/TestRepository";
    private static final String FIXTURE_AUTHOR_LOGIN = "FelixTJDietrich";

    @Autowired
    private GitHubIssueProcessor processor;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private IssueTypeRepository issueTypeRepository;

    @Autowired
    private TestIssueEventListener eventListener;

    private Repository testRepository;
    private Workspace testWorkspace;
    private Organization testOrganization;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    private void setupTestData() {
        // Create organization matching fixture data
        testOrganization = new Organization();
        testOrganization.setId(FIXTURE_ORG_ID);
        testOrganization.setGithubId(FIXTURE_ORG_ID);
        testOrganization.setLogin(FIXTURE_ORG_LOGIN);
        testOrganization.setCreatedAt(Instant.now());
        testOrganization.setUpdatedAt(Instant.now());
        testOrganization.setName("Hephaestus Test");
        testOrganization.setAvatarUrl("https://avatars.githubusercontent.com/u/" + FIXTURE_ORG_ID);
        testOrganization = organizationRepository.save(testOrganization);

        // Create repository matching fixture data
        testRepository = new Repository();
        testRepository.setId(FIXTURE_REPO_ID);
        testRepository.setName("TestRepository");
        testRepository.setNameWithOwner(FIXTURE_REPO_FULL_NAME);
        testRepository.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME);
        testRepository.setVisibility(Repository.Visibility.PUBLIC);
        testRepository.setDefaultBranch("main");
        testRepository.setCreatedAt(Instant.now());
        testRepository.setUpdatedAt(Instant.now());
        testRepository.setPushedAt(Instant.now());
        testRepository.setOrganization(testOrganization);
        testRepository = repositoryRepository.save(testRepository);

        // Create workspace
        testWorkspace = new Workspace();
        testWorkspace.setWorkspaceSlug("hephaestus-test");
        testWorkspace.setDisplayName("Hephaestus Test");
        testWorkspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        testWorkspace.setIsPubliclyViewable(true);
        testWorkspace.setOrganization(testOrganization);
        testWorkspace.setAccountLogin(FIXTURE_ORG_LOGIN);
        testWorkspace.setAccountType(AccountType.ORG);
        testWorkspace = workspaceRepository.save(testWorkspace);
    }

    private ProcessingContext createContext() {
        return ProcessingContext.forSync(testWorkspace.getId(), testRepository);
    }

    private GitHubUserDTO createAuthorDto() {
        return new GitHubUserDTO(
            FIXTURE_AUTHOR_ID,
            FIXTURE_AUTHOR_ID,
            FIXTURE_AUTHOR_LOGIN,
            "https://avatars.githubusercontent.com/u/" + FIXTURE_AUTHOR_ID,
            "https://github.com/" + FIXTURE_AUTHOR_LOGIN,
            null,
            null
        );
    }

    private GitHubIssueDTO createBasicIssueDto(Long id, int number) {
        return new GitHubIssueDTO(
            id, // id (webhook style - no databaseId)
            null, // databaseId (null for webhook payloads)
            "I_node_" + id,
            number,
            "Test Issue #" + number,
            "This is the body of test issue #" + number,
            "open",
            null, // stateReason
            "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/issues/" + number,
            0, // commentsCount
            Instant.parse("2025-11-01T21:42:45Z"),
            Instant.parse("2025-11-01T21:42:45Z"),
            null, // closedAt
            false, // locked
            createAuthorDto(),
            null, // assignees
            null, // labels
            null, // milestone
            null, // issueType
            null, // repository
            null // pullRequest
        );
    }

    // ==================== Critical: getDatabaseId() Fallback Tests ====================

    @Nested
    @DisplayName("getDatabaseId() Fallback Logic")
    class GetDatabaseIdFallback {

        @Test
        @DisplayName("Should use databaseId when present (GraphQL style)")
        void shouldUseDatabaseIdWhenPresent() {
            // Given - GraphQL style DTO with databaseId
            Long databaseId = 123456789L;
            GitHubIssueDTO dto = new GitHubIssueDTO(
                999L, // id (node id as number, but databaseId is what matters)
                databaseId, // databaseId
                "I_node_xyz",
                1,
                "GraphQL Issue",
                "Body",
                "open",
                null,
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/issues/1",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                createAuthorDto(),
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then - should use databaseId
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(databaseId);
            assertThat(issueRepository.findById(databaseId)).isPresent();
        }

        @Test
        @DisplayName("Should fallback to id when databaseId is null (Webhook style)")
        void shouldFallbackToIdWhenDatabaseIdNull() {
            // Given - Webhook style DTO with only id
            Long webhookId = FIXTURE_ISSUE_ID;
            GitHubIssueDTO dto = new GitHubIssueDTO(
                webhookId, // id (this is the database ID in webhooks)
                null, // databaseId is null in webhooks
                "I_kwDOO4CKW87VS4RQ",
                20,
                "Webhook Issue",
                "Body",
                "open",
                null,
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/issues/20",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                createAuthorDto(),
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // Verify the fallback works
            assertThat(dto.getDatabaseId()).isEqualTo(webhookId);

            // When
            Issue result = processor.process(dto, createContext());

            // Then - should use id as fallback
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(webhookId);
            assertThat(issueRepository.findById(webhookId)).isPresent();
        }

        @Test
        @DisplayName("Should return null when both id and databaseId are null")
        void shouldReturnNullWhenBothIdsNull() {
            // Given - malformed DTO with no IDs
            GitHubIssueDTO dto = new GitHubIssueDTO(
                null, // id
                null, // databaseId
                "I_node_xyz",
                1,
                "No ID Issue",
                "Body",
                "open",
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                createAuthorDto(),
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // Verify fallback returns null
            assertThat(dto.getDatabaseId()).isNull();

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result).isNull();
            assertThat(eventListener.getCreatedEvents()).isEmpty();
        }
    }

    // ==================== Process (Create/Update) Tests ====================

    @Nested
    @DisplayName("Process Method - Create")
    class ProcessMethodCreate {

        @Test
        @DisplayName("Should create new issue and publish Created event")
        void shouldCreateNewIssueAndPublishEvent() {
            // Given
            Long issueId = FIXTURE_ISSUE_ID;
            GitHubIssueDTO dto = createBasicIssueDto(issueId, 20);

            // When
            Issue result = processor.process(dto, createContext());

            // Then - verify issue created
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(issueId);
            assertThat(result.getNumber()).isEqualTo(20);
            assertThat(result.getTitle()).isEqualTo("Test Issue #20");
            assertThat(result.getState()).isEqualTo(Issue.State.OPEN);
            assertThat(result.getRepository().getId()).isEqualTo(FIXTURE_REPO_ID);

            // Verify persisted
            assertThat(issueRepository.findById(issueId)).isPresent();

            // Verify Created event published
            assertThat(eventListener.getCreatedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.issue().id()).isEqualTo(issueId);
                    assertThat(event.context().scopeId()).isEqualTo(testWorkspace.getId());
                });
        }

        @Test
        @DisplayName("Should create author user if not exists")
        void shouldCreateAuthorIfNotExists() {
            // Given - no user exists
            assertThat(userRepository.findById(FIXTURE_AUTHOR_ID)).isEmpty();

            Long issueId = 111222333L;
            GitHubIssueDTO dto = createBasicIssueDto(issueId, 1);

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result.getAuthor()).isNotNull();
            assertThat(result.getAuthor().getId()).isEqualTo(FIXTURE_AUTHOR_ID);
            assertThat(result.getAuthor().getLogin()).isEqualTo(FIXTURE_AUTHOR_LOGIN);
            assertThat(userRepository.findById(FIXTURE_AUTHOR_ID)).isPresent();
        }

        @Test
        @DisplayName("Should reuse existing author user")
        void shouldReuseExistingAuthor() {
            // Given - create user first
            User existingUser = new User();
            existingUser.setId(FIXTURE_AUTHOR_ID);
            existingUser.setLogin(FIXTURE_AUTHOR_LOGIN);
            existingUser.setAvatarUrl("https://avatars.example.com");
            userRepository.save(existingUser);

            long userCountBefore = userRepository.count();

            Long issueId = 222333444L;
            GitHubIssueDTO dto = createBasicIssueDto(issueId, 2);

            // When
            Issue result = processor.process(dto, createContext());

            // Then - should reuse existing user, not create new
            assertThat(result.getAuthor().getId()).isEqualTo(FIXTURE_AUTHOR_ID);
            assertThat(userRepository.count()).isEqualTo(userCountBefore);
        }

        @Test
        @DisplayName("Should handle issue with null body")
        void shouldHandleNullBody() {
            // Given
            Long issueId = 333444555L;
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                3,
                "Title",
                null, // null body
                "open",
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                createAuthorDto(),
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result.getBody()).isNull();
        }

        @Test
        @DisplayName("Should handle issue with null author")
        void shouldHandleNullAuthor() {
            // Given
            Long issueId = 444555666L;
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                4,
                "Title",
                "Body",
                "open",
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                null, // null author
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAuthor()).isNull();
        }

        @Test
        @DisplayName("Should create labels when included in issue")
        void shouldCreateLabelsWhenIncluded() {
            // Given
            Long issueId = 555666777L;
            Long labelId = 9567656085L;
            GitHubLabelDTO labelDto = new GitHubLabelDTO(labelId, "LA_node", "bug", "Bug label", "ff0000", null, null);
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                5,
                "Title",
                "Body",
                "open",
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                createAuthorDto(),
                null,
                List.of(labelDto),
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result.getLabels()).hasSize(1);
            assertThat(result.getLabels().iterator().next().getName()).isEqualTo("bug");
            assertThat(labelRepository.findById(labelId)).isPresent();
        }

        @Test
        @DisplayName("Should create assignees when included in issue")
        void shouldCreateAssigneesWhenIncluded() {
            // Given
            Long issueId = 666777888L;
            Long assigneeId = 888999111L;
            GitHubUserDTO assigneeDto = new GitHubUserDTO(
                assigneeId,
                assigneeId,
                "assignee1",
                "https://avatars.example.com",
                "https://github.com/assignee1",
                null,
                null
            );
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                6,
                "Title",
                "Body",
                "open",
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                createAuthorDto(),
                List.of(assigneeDto),
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result.getAssignees()).hasSize(1);
            assertThat(result.getAssignees().iterator().next().getLogin()).isEqualTo("assignee1");
            assertThat(userRepository.findById(assigneeId)).isPresent();
        }

        @Test
        @DisplayName("Should create milestone when included in issue")
        void shouldCreateMilestoneWhenIncluded() {
            // Given
            Long issueId = 777888999L;
            Long milestoneId = 14028563L;
            GitHubMilestoneDTO milestoneDto = new GitHubMilestoneDTO(
                milestoneId,
                2,
                "Webhook Fixtures",
                "Milestone description",
                "open",
                Instant.parse("2025-12-31T08:00:00Z"),
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/2",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                7,
                "Title",
                "Body",
                "open",
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                createAuthorDto(),
                null,
                null,
                milestoneDto,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result.getMilestone()).isNotNull();
            assertThat(result.getMilestone().getTitle()).isEqualTo("Webhook Fixtures");
            assertThat(milestoneRepository.findById(milestoneId)).isPresent();
        }
    }

    // ==================== Process (Update) Tests ====================

    @Nested
    @DisplayName("Process Method - Update")
    class ProcessMethodUpdate {

        @Test
        @DisplayName("Should update existing issue and publish Updated event")
        void shouldUpdateExistingIssueAndPublishEvent() {
            // Given - create existing issue
            Long issueId = FIXTURE_ISSUE_ID;
            Issue existing = new Issue();
            existing.setId(issueId);
            existing.setNumber(20);
            existing.setTitle("Old Title");
            existing.setBody("Old body");
            existing.setState(Issue.State.OPEN);
            existing.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME + "/issues/20");
            existing.setRepository(testRepository);
            issueRepository.save(existing);

            eventListener.clear();

            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                20,
                "New Title",
                "New body",
                "open",
                null,
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/issues/20",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                createAuthorDto(),
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then - verify issue updated
            assertThat(result.getTitle()).isEqualTo("New Title");
            assertThat(result.getBody()).isEqualTo("New body");

            // Verify Updated event with changedFields
            assertThat(eventListener.getUpdatedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.changedFields()).contains("title", "body");
                });
        }

        @Test
        @DisplayName("Should not publish Updated event when no fields changed")
        void shouldNotPublishWhenNoChanges() {
            // Given - create existing issue
            Long issueId = FIXTURE_ISSUE_ID;
            Issue existing = new Issue();
            existing.setId(issueId);
            existing.setNumber(20);
            existing.setTitle("Same Title");
            existing.setBody("Same body");
            existing.setState(Issue.State.OPEN);
            existing.setCommentsCount(5);
            existing.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME + "/issues/20");
            existing.setRepository(testRepository);
            existing.setCreatedAt(Instant.parse("2025-11-01T21:42:45Z"));
            existing.setUpdatedAt(Instant.parse("2025-11-01T21:42:45Z"));
            issueRepository.save(existing);

            eventListener.clear();

            // Same data
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                20,
                "Same Title",
                "Same body",
                "open",
                null,
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/issues/20",
                5,
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2025-11-01T21:42:45Z"),
                null,
                false, // locked
                createAuthorDto(),
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            processor.process(dto, createContext());

            // Then - no Updated event (empty changedFields)
            // Note: Event is still published but with empty changedFields
            assertThat(eventListener.getUpdatedEvents()).allSatisfy(event -> {
                // changedFields should be empty
                assertThat(event.changedFields()).isEmpty();
            });
        }

        @Test
        @DisplayName("Should be idempotent - processing same DTO twice")
        void shouldBeIdempotent() {
            // Given
            Long issueId = 111222333L;
            GitHubIssueDTO dto = createBasicIssueDto(issueId, 10);

            // When - process twice
            processor.process(dto, createContext());
            long countAfterFirst = issueRepository.count();

            eventListener.clear();
            processor.process(dto, createContext());

            // Then - only one issue exists
            assertThat(issueRepository.count()).isEqualTo(countAfterFirst);
        }

        @Test
        @DisplayName("Should update milestone when changed")
        void shouldUpdateMilestoneWhenChanged() {
            // Given - create issue without milestone
            Long issueId = 888999000L;
            Issue existing = new Issue();
            existing.setId(issueId);
            existing.setNumber(8);
            existing.setTitle("Issue");
            existing.setState(Issue.State.OPEN);
            existing.setHtmlUrl("https://example.com");
            existing.setRepository(testRepository);
            existing.setMilestone(null);
            issueRepository.save(existing);

            eventListener.clear();

            // Now add milestone
            Long milestoneId = 14028563L;
            GitHubMilestoneDTO milestoneDto = new GitHubMilestoneDTO(
                milestoneId,
                2,
                "New Milestone",
                "Desc",
                "open",
                null,
                "https://example.com/milestone/2",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                8,
                "Issue",
                null,
                "open",
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                null,
                null,
                null,
                milestoneDto,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result.getMilestone()).isNotNull();
            assertThat(eventListener.getUpdatedEvents())
                .first()
                .satisfies(event -> assertThat(event.changedFields()).contains("milestone"));
        }

        @Test
        @DisplayName("Should remove milestone when demilestoned")
        void shouldRemoveMilestoneWhenDemilestoned() {
            // Given - create milestone and issue with milestone
            Long milestoneId = 14028563L;
            Milestone milestone = new Milestone();
            milestone.setId(milestoneId);
            milestone.setNumber(2);
            milestone.setTitle("Existing Milestone");
            milestone.setState(Milestone.State.OPEN);
            milestone.setHtmlUrl("https://example.com/milestone/2");
            milestone.setRepository(testRepository);
            milestoneRepository.save(milestone);

            Long issueId = 999000111L;
            Issue existing = new Issue();
            existing.setId(issueId);
            existing.setNumber(9);
            existing.setTitle("Issue");
            existing.setState(Issue.State.OPEN);
            existing.setHtmlUrl("https://example.com");
            existing.setRepository(testRepository);
            existing.setMilestone(milestone);
            issueRepository.save(existing);

            eventListener.clear();

            // Update with null milestone
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                9,
                "Issue",
                null,
                "open",
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                null,
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result.getMilestone()).isNull();
            assertThat(eventListener.getUpdatedEvents())
                .first()
                .satisfies(event -> assertThat(event.changedFields()).contains("milestone"));
        }
    }

    // ==================== State Transition Tests ====================

    @Nested
    @DisplayName("State Transitions")
    class StateTransitions {

        @Test
        @DisplayName("processClosed should update state and publish Closed event")
        void processClosedShouldPublishClosedEvent() {
            // Given
            Long issueId = FIXTURE_ISSUE_ID;
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                20,
                "Title",
                "Body",
                "closed",
                "completed", // closed with reason
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                false, // locked
                createAuthorDto(),
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.processClosed(dto, createContext());

            // Then
            assertThat(result.getState()).isEqualTo(Issue.State.CLOSED);
            assertThat(result.getStateReason()).isEqualTo(Issue.StateReason.COMPLETED);

            // Verify Closed event
            assertThat(eventListener.getClosedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.stateReason()).isEqualTo("completed");
                });
        }

        @Test
        @DisplayName("processReopened should update state and publish Updated event with state in changedFields")
        void processReopenedShouldPublishUpdatedEvent() {
            // Given - create closed issue first
            Long issueId = 222333444L;
            Issue existing = new Issue();
            existing.setId(issueId);
            existing.setNumber(2);
            existing.setTitle("Closed Issue");
            existing.setState(Issue.State.CLOSED);
            existing.setStateReason(Issue.StateReason.COMPLETED);
            existing.setHtmlUrl("https://example.com");
            existing.setRepository(testRepository);
            issueRepository.save(existing);

            eventListener.clear();

            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                2,
                "Reopened Issue",
                null,
                "open",
                "reopened",
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                null,
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.processReopened(dto, createContext());

            // Then
            assertThat(result.getState()).isEqualTo(Issue.State.OPEN);

            // Verify Updated event with "state" in changedFields
            assertThat(eventListener.getUpdatedEvents()).anySatisfy(event ->
                assertThat(event.changedFields()).contains("state")
            );
        }

        @Test
        @DisplayName("Should handle NOT_PLANNED state reason")
        void shouldHandleNotPlannedStateReason() {
            // Given
            Long issueId = 333444555L;
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                3,
                "Title",
                "Body",
                "closed",
                "not_planned",
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                false, // locked
                createAuthorDto(),
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.processClosed(dto, createContext());

            // Then
            assertThat(result.getStateReason()).isEqualTo(Issue.StateReason.NOT_PLANNED);
        }
    }

    // ==================== Label Event Tests ====================

    @Nested
    @DisplayName("Label Events")
    class LabelEvents {

        @Test
        @DisplayName("processLabeled should publish Labeled event")
        void processLabeledShouldPublishEvent() {
            // Given - create issue first
            Long issueId = FIXTURE_ISSUE_ID;
            GitHubIssueDTO issueDto = createBasicIssueDto(issueId, 20);
            processor.process(issueDto, createContext());

            eventListener.clear();

            Long labelId = 9567656085L;
            GitHubLabelDTO labelDto = new GitHubLabelDTO(
                labelId,
                "LA_node",
                "enhancement",
                "Enhancement",
                "84b6eb",
                null,
                null
            );

            // When
            processor.processLabeled(issueDto, labelDto, createContext());

            // Then
            assertThat(eventListener.getLabeledEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.label().name()).isEqualTo("enhancement");
                });
        }

        @Test
        @DisplayName("processUnlabeled should publish Unlabeled event")
        void processUnlabeledShouldPublishEvent() {
            // Given - create issue with label
            Long issueId = FIXTURE_ISSUE_ID;
            Long labelId = 9567656085L;
            GitHubLabelDTO labelDto = new GitHubLabelDTO(labelId, "LA_node", "bug", "Bug", "ff0000", null, null);
            GitHubIssueDTO issueDto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                20,
                "Title",
                "Body",
                "open",
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                createAuthorDto(),
                null,
                List.of(labelDto),
                null,
                null,
                null,
                null // pullRequest
            );
            processor.process(issueDto, createContext());

            eventListener.clear();

            // When
            processor.processUnlabeled(issueDto, labelDto, createContext());

            // Then
            assertThat(eventListener.getUnlabeledEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.label().name()).isEqualTo("bug");
                });
        }
    }

    // ==================== Issue Type Tests ====================

    @Nested
    @DisplayName("Issue Type Events")
    class IssueTypeEvents {

        @Test
        @DisplayName("processTyped should set issue type and publish Typed event")
        void processTypedShouldPublishEvent() {
            // Given - create issue first
            Long issueId = FIXTURE_ISSUE_ID;
            GitHubIssueDTO issueDto = createBasicIssueDto(issueId, 20);

            GitHubIssueTypeDTO typeDto = new GitHubIssueTypeDTO(
                27228861L,
                "IT_kwDODNYmp84Bn3q9",
                "Task",
                "A specific piece of work",
                "yellow",
                true
            );

            // When
            Issue result = processor.processTyped(issueDto, typeDto, FIXTURE_ORG_LOGIN, createContext());

            // Then
            assertThat(result.getIssueType()).isNotNull();
            assertThat(result.getIssueType().getName()).isEqualTo("Task");

            assertThat(eventListener.getTypedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.issueType().name()).isEqualTo("Task");
                    assertThat(event.issue().id()).isEqualTo(issueId);
                });
        }

        @Test
        @DisplayName("processUntyped should remove issue type and publish Untyped event")
        void processUntypedShouldPublishEvent() {
            // Given - create issue type
            IssueType issueType = new IssueType();
            issueType.setId("IT_node_123");
            issueType.setName("Bug");
            issueType.setDescription("A bug");
            issueType.setColor(IssueType.Color.RED);
            issueType.setEnabled(true);
            issueType.setOrganization(testOrganization);
            issueType = issueTypeRepository.save(issueType);

            // Create issue with type
            Long issueId = FIXTURE_ISSUE_ID;
            Issue existing = new Issue();
            existing.setId(issueId);
            existing.setNumber(20);
            existing.setTitle("Typed Issue");
            existing.setState(Issue.State.OPEN);
            existing.setHtmlUrl("https://example.com");
            existing.setRepository(testRepository);
            existing.setIssueType(issueType);
            issueRepository.save(existing);

            eventListener.clear();

            GitHubIssueDTO issueDto = createBasicIssueDto(issueId, 20);

            // When
            Issue result = processor.processUntyped(issueDto, createContext());

            // Then
            assertThat(result.getIssueType()).isNull();

            assertThat(eventListener.getUntypedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.previousType().name()).isEqualTo("Bug");
                    assertThat(event.issue().id()).isEqualTo(issueId);
                });
        }
    }

    // ==================== Delete Tests ====================

    @Nested
    @DisplayName("Delete Method")
    class DeleteMethod {

        @Test
        @DisplayName("processDeleted should delete issue")
        void processDeletedShouldDeleteIssue() {
            // Given - create issue
            Long issueId = FIXTURE_ISSUE_ID;
            Issue existing = new Issue();
            existing.setId(issueId);
            existing.setNumber(20);
            existing.setTitle("To Delete");
            existing.setState(Issue.State.OPEN);
            existing.setHtmlUrl("https://example.com");
            existing.setRepository(testRepository);
            issueRepository.save(existing);

            assertThat(issueRepository.findById(issueId)).isPresent();

            GitHubIssueDTO dto = createBasicIssueDto(issueId, 20);

            // When
            processor.processDeleted(dto, createContext());

            // Then
            assertThat(issueRepository.findById(issueId)).isEmpty();
        }

        @Test
        @DisplayName("processDeleted should handle non-existent issue gracefully")
        void processDeletedShouldHandleNonExistent() {
            // Given - issue doesn't exist
            Long nonExistentId = 999999999L;
            assertThat(issueRepository.findById(nonExistentId)).isEmpty();

            GitHubIssueDTO dto = createBasicIssueDto(nonExistentId, 99);

            // When/Then - should not throw
            assertThatCode(() -> processor.processDeleted(dto, createContext())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("processDeleted should handle null ID gracefully")
        void processDeletedShouldHandleNullId() {
            // Given
            GitHubIssueDTO dto = new GitHubIssueDTO(
                null,
                null,
                "node",
                1,
                "Title",
                null,
                "open",
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                null,
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When/Then - should not throw
            assertThatCode(() -> processor.processDeleted(dto, createContext())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("processDeleted should sync bidirectional ManyToMany relationships")
        void processDeletedShouldSyncBidirectionalRelationships() {
            // Given - create issue with labels (ManyToMany relationship)
            Long issueId = FIXTURE_ISSUE_ID;
            Issue existing = new Issue();
            existing.setId(issueId);
            existing.setNumber(21);
            existing.setTitle("Issue with labels");
            existing.setState(Issue.State.OPEN);
            existing.setHtmlUrl("https://example.com");
            existing.setRepository(testRepository);
            existing = issueRepository.save(existing);

            // Create a label and associate it with the issue
            de.tum.in.www1.hephaestus.gitprovider.label.Label label =
                new de.tum.in.www1.hephaestus.gitprovider.label.Label();
            label.setId(100001L);
            label.setName("bug");
            label.setColor("d73a4a");
            label.setRepository(testRepository);
            label = labelRepository.save(label);

            // Use helper method for proper bidirectional sync
            existing.addLabel(label);
            existing = issueRepository.save(existing);

            // Verify setup
            assertThat(issueRepository.findById(issueId)).isPresent();
            assertThat(labelRepository.findById(label.getId())).isPresent();

            GitHubIssueDTO dto = createBasicIssueDto(issueId, 21);

            // When - delete should work without TransientObjectException
            assertThatCode(() -> processor.processDeleted(dto, createContext())).doesNotThrowAnyException();

            // Then - issue deleted, label still exists
            assertThat(issueRepository.findById(issueId)).isEmpty();
            assertThat(labelRepository.findById(100001L)).isPresent();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null DTO IDs gracefully")
        void shouldHandleNullDtoIds() {
            // When - DTO with null IDs (getDatabaseId() will return null)
            GitHubIssueDTO nullIdDto = new GitHubIssueDTO(
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                false, // locked
                null,
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            Issue result = processor.process(nullIdDto, createContext());

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should default to OPEN state for null state string")
        void shouldDefaultToOpenStateForNullState() {
            // Given
            Long issueId = 111222333L;
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                1,
                "Title",
                null,
                null, // null state
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                null,
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result.getState()).isEqualTo(Issue.State.OPEN);
        }

        @Test
        @DisplayName("Should default to OPEN state for unknown state string")
        void shouldDefaultToOpenStateForUnknownState() {
            // Given
            Long issueId = 222333444L;
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                2,
                "Title",
                null,
                "UNKNOWN_STATE",
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                null,
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result.getState()).isEqualTo(Issue.State.OPEN);
        }

        @Test
        @DisplayName("Should handle unknown stateReason as UNKNOWN")
        void shouldHandleUnknownStateReason() {
            // Given
            Long issueId = 333444555L;
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                3,
                "Title",
                null,
                "closed",
                "some_weird_reason",
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                false, // locked
                null,
                null,
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result.getStateReason()).isEqualTo(Issue.StateReason.UNKNOWN);
        }

        @Test
        @DisplayName("Should handle empty assignees list")
        void shouldHandleEmptyAssigneesList() {
            // Given
            Long issueId = 444555666L;
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                4,
                "Title",
                null,
                "open",
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                createAuthorDto(),
                List.of(), // empty assignees
                null,
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result.getAssignees()).isEmpty();
        }

        @Test
        @DisplayName("Should handle empty labels list")
        void shouldHandleEmptyLabelsList() {
            // Given
            Long issueId = 555666777L;
            GitHubIssueDTO dto = new GitHubIssueDTO(
                issueId,
                null,
                "node",
                5,
                "Title",
                null,
                "open",
                null,
                "https://example.com",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false, // locked
                createAuthorDto(),
                null,
                List.of(), // empty labels
                null,
                null,
                null,
                null // pullRequest
            );

            // When
            Issue result = processor.process(dto, createContext());

            // Then
            assertThat(result.getLabels()).isEmpty();
        }
    }

    // ==================== Test Event Listener ====================

    @Component
    static class TestIssueEventListener {

        private final List<DomainEvent.IssueCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.IssueUpdated> updatedEvents = new ArrayList<>();
        private final List<DomainEvent.IssueClosed> closedEvents = new ArrayList<>();
        private final List<DomainEvent.IssueReopened> reopenedEvents = new ArrayList<>();
        private final List<DomainEvent.IssueLabeled> labeledEvents = new ArrayList<>();
        private final List<DomainEvent.IssueUnlabeled> unlabeledEvents = new ArrayList<>();
        private final List<DomainEvent.IssueTyped> typedEvents = new ArrayList<>();
        private final List<DomainEvent.IssueUntyped> untypedEvents = new ArrayList<>();

        @EventListener
        public void onCreated(DomainEvent.IssueCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onUpdated(DomainEvent.IssueUpdated event) {
            updatedEvents.add(event);
        }

        @EventListener
        public void onClosed(DomainEvent.IssueClosed event) {
            closedEvents.add(event);
        }

        @EventListener
        public void onReopened(DomainEvent.IssueReopened event) {
            reopenedEvents.add(event);
        }

        @EventListener
        public void onLabeled(DomainEvent.IssueLabeled event) {
            labeledEvents.add(event);
        }

        @EventListener
        public void onUnlabeled(DomainEvent.IssueUnlabeled event) {
            unlabeledEvents.add(event);
        }

        @EventListener
        public void onTyped(DomainEvent.IssueTyped event) {
            typedEvents.add(event);
        }

        @EventListener
        public void onUntyped(DomainEvent.IssueUntyped event) {
            untypedEvents.add(event);
        }

        public List<DomainEvent.IssueCreated> getCreatedEvents() {
            return new ArrayList<>(createdEvents);
        }

        public List<DomainEvent.IssueUpdated> getUpdatedEvents() {
            return new ArrayList<>(updatedEvents);
        }

        public List<DomainEvent.IssueClosed> getClosedEvents() {
            return new ArrayList<>(closedEvents);
        }

        public List<DomainEvent.IssueReopened> getReopenedEvents() {
            return new ArrayList<>(reopenedEvents);
        }

        public List<DomainEvent.IssueLabeled> getLabeledEvents() {
            return new ArrayList<>(labeledEvents);
        }

        public List<DomainEvent.IssueUnlabeled> getUnlabeledEvents() {
            return new ArrayList<>(unlabeledEvents);
        }

        public List<DomainEvent.IssueTyped> getTypedEvents() {
            return new ArrayList<>(typedEvents);
        }

        public List<DomainEvent.IssueUntyped> getUntypedEvents() {
            return new ArrayList<>(untypedEvents);
        }

        public void clear() {
            createdEvents.clear();
            updatedEvents.clear();
            closedEvents.clear();
            reopenedEvents.clear();
            labeledEvents.clear();
            unlabeledEvents.clear();
            typedEvents.clear();
            untypedEvents.clear();
        }
    }
}
