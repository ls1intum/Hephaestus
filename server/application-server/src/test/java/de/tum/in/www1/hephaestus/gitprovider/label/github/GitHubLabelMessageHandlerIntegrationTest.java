package de.tum.in.www1.hephaestus.gitprovider.label.github;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
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

/**
 * Integration tests for GitHubLabelMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 * Fixtures are real GitHub webhook payloads from HephaestusTest/TestRepository.
 * <p>
 * <b>Fixture Values (label.created.json):</b>
 * <ul>
 *   <li>Label ID: 8747399111</li>
 *   <li>Label name: "documentation"</li>
 *   <li>Label color: "0075ca"</li>
 *   <li>Label description: "Improvements or additions to documentation"</li>
 * </ul>
 * <p>
 * Note: This test class does NOT use @Transactional because the GitHubLabelProcessor
 * uses REQUIRES_NEW propagation. Having @Transactional here would cause deadlocks
 * as the test transaction would hold locks needed by the processor's new transaction.
 */
@DisplayName("GitHub Label Message Handler")
class GitHubLabelMessageHandlerIntegrationTest extends BaseIntegrationTest {

    // IDs from the actual GitHub webhook fixtures
    private static final Long FIXTURE_ORG_ID = 215361191L;
    private static final Long FIXTURE_REPO_ID = 998279771L;
    private static final String FIXTURE_ORG_LOGIN = "HephaestusTest";
    private static final String FIXTURE_REPO_NAME = "TestRepository";
    private static final String FIXTURE_REPO_FULL_NAME = "HephaestusTest/TestRepository";

    // Exact fixture values from label.created.json for correctness verification
    private static final Long FIXTURE_LABEL_ID = 8747399111L;
    private static final String FIXTURE_LABEL_NAME = "documentation";
    private static final String FIXTURE_LABEL_COLOR = "0075ca";
    private static final String FIXTURE_LABEL_DESCRIPTION = "Improvements or additions to documentation";

    @Autowired
    private GitHubLabelMessageHandler handler;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private Repository testRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

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
        testRepository = new Repository();
        testRepository.setId(FIXTURE_REPO_ID);
        testRepository.setName(FIXTURE_REPO_NAME);
        testRepository.setNameWithOwner(FIXTURE_REPO_FULL_NAME);
        testRepository.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME);
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
        workspace.setAccountLogin(FIXTURE_ORG_LOGIN);
        workspace.setAccountType(AccountType.ORG);
        workspaceRepository.save(workspace);
    }

    @Test
    @DisplayName("Should return correct event type")
    void shouldReturnCorrectEventType() {
        assertThat(handler.getEventType()).isEqualTo(GitHubEventType.LABEL);
    }

    @Test
    @DisplayName("Should process created label events with exact fixture values")
    void shouldProcessCreatedLabelEvents() throws Exception {
        // Given
        GitHubLabelEventDTO event = loadPayload("label.created");
        assertThat(labelRepository.findById(FIXTURE_LABEL_ID)).isEmpty();

        // When
        handler.handleEvent(event);

        // Then - verify ALL persisted fields against hardcoded fixture values
        Label label = labelRepository.findById(FIXTURE_LABEL_ID).orElseThrow();

        // Core schema fields (mapped to DB columns)
        assertThat(label.getId()).isEqualTo(FIXTURE_LABEL_ID);
        assertThat(label.getName()).isEqualTo(FIXTURE_LABEL_NAME);
        assertThat(label.getColor()).isEqualTo(FIXTURE_LABEL_COLOR);
        assertThat(label.getDescription()).isEqualTo(FIXTURE_LABEL_DESCRIPTION);

        // Repository association (foreign key)
        assertThat(label.getRepository()).isNotNull();
        assertThat(label.getRepository().getId()).isEqualTo(FIXTURE_REPO_ID);

        // Note: createdAt/updatedAt are not provided in webhook payloads (only in GraphQL)
        // Note: lastSyncAt is ETL infrastructure, not set by webhook handler
    }

    @Test
    @DisplayName("Should update all label fields on 'edited' event")
    void shouldProcessEditedLabelEvents() throws Exception {
        // Given - create existing label with stale data
        GitHubLabelEventDTO event = loadPayload("label.edited");
        Long labelId = event.label().id();

        Label existingLabel = new Label();
        existingLabel.setId(labelId);
        existingLabel.setName("stale-name");
        existingLabel.setColor("ffffff");
        existingLabel.setDescription("stale description");
        existingLabel.setRepository(testRepository);
        labelRepository.save(existingLabel);

        // When
        handler.handleEvent(event);

        // Then - verify all mutable fields are updated from DTO
        Label label = labelRepository.findById(labelId).orElseThrow();
        assertThat(label.getName()).isEqualTo(event.label().name());
        assertThat(label.getColor()).isEqualTo(event.label().color());
        assertThat(label.getDescription()).isEqualTo(event.label().description());

        // Verify repository association preserved (not overwritten)
        assertThat(label.getRepository().getId()).isEqualTo(FIXTURE_REPO_ID);
    }

    @Test
    @DisplayName("Should process deleted label events")
    void shouldProcessDeletedLabelEvents() throws Exception {
        // Given
        GitHubLabelEventDTO event = loadPayload("label.deleted");

        // Create existing label
        Label existingLabel = new Label();
        existingLabel.setId(event.label().id());
        existingLabel.setName(event.label().name());
        existingLabel.setColor(event.label().color());
        existingLabel.setRepository(testRepository);
        labelRepository.save(existingLabel);

        // Verify it exists
        assertThat(labelRepository.findById(event.label().id())).isPresent();

        // When
        handler.handleEvent(event);

        // Then
        assertThat(labelRepository.findById(event.label().id())).isEmpty();
    }

    private GitHubLabelEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubLabelEventDTO.class);
    }

    /**
     * Creates a test repository reference DTO matching the fixture data.
     */
    private GitHubRepositoryRefDTO createTestRepoRef() {
        return new GitHubRepositoryRefDTO(
            FIXTURE_REPO_ID,
            "R_test",
            FIXTURE_REPO_NAME,
            FIXTURE_REPO_FULL_NAME,
            false,
            "https://github.com/" + FIXTURE_REPO_FULL_NAME
        );
    }

    // ==================== Edge Case Tests ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null label in event gracefully")
        void shouldHandleNullLabelGracefully() {
            // Given - event with null label
            GitHubLabelEventDTO event = new GitHubLabelEventDTO("created", null, createTestRepoRef(), null);

            // When/Then - should not throw, just log warning
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();

            // No labels should be created
            assertThat(labelRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should handle event with missing repository context")
        void shouldHandleMissingRepositoryContext() {
            // Given - event without repository
            GitHubLabelDTO labelDto = new GitHubLabelDTO(
                999999L,
                "LA_test",
                "test-label",
                "Test description",
                "ff0000",
                null,
                null
            );
            GitHubLabelEventDTO event = new GitHubLabelEventDTO("created", labelDto, null, null);

            // When/Then - should not throw
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle label with null description")
        void shouldHandleLabelWithNullDescription() throws Exception {
            // Given - create label DTO with null description
            Long labelId = 123456789L;
            GitHubLabelDTO labelDto = new GitHubLabelDTO(
                labelId,
                "LA_nodeId",
                "no-description-label",
                null, // null description
                "abcdef",
                null,
                null
            );
            GitHubLabelEventDTO event = new GitHubLabelEventDTO("created", labelDto, createTestRepoRef(), null);

            // When
            handler.handleEvent(event);

            // Then
            assertThat(labelRepository.findById(labelId))
                .isPresent()
                .get()
                .satisfies(label -> {
                    assertThat(label.getName()).isEqualTo("no-description-label");
                    assertThat(label.getDescription()).isNull();
                });
        }

        @Test
        @DisplayName("Should update description from value to null")
        void shouldUpdateDescriptionToNull() throws Exception {
            // Given - existing label with description
            Long labelId = 987654321L;
            Label existingLabel = new Label();
            existingLabel.setId(labelId);
            existingLabel.setName("has-description");
            existingLabel.setColor("123456");
            existingLabel.setDescription("original description");
            existingLabel.setRepository(testRepository);
            labelRepository.save(existingLabel);

            // When - update with null description
            GitHubLabelDTO labelDto = new GitHubLabelDTO(
                labelId,
                "LA_nodeId",
                "has-description",
                null, // setting to null
                "123456",
                null,
                null
            );
            GitHubLabelEventDTO event = new GitHubLabelEventDTO("edited", labelDto, createTestRepoRef(), null);
            handler.handleEvent(event);

            // Then - description should be null now
            assertThat(labelRepository.findById(labelId)).isPresent().get().extracting(Label::getDescription).isNull();
        }

        @Test
        @DisplayName("Should handle idempotent label creation")
        void shouldHandleIdempotentCreation() throws Exception {
            // Given
            GitHubLabelEventDTO event = loadPayload("label.created");

            // When - handle same event twice
            handler.handleEvent(event);
            handler.handleEvent(event);

            // Then - only one label should exist
            assertThat(labelRepository.findById(event.label().id())).isPresent();
            assertThat(labelRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle deletion of non-existent label gracefully")
        void shouldHandleDeletionOfNonExistentLabel() throws Exception {
            // Given - label doesn't exist
            GitHubLabelEventDTO event = loadPayload("label.deleted");
            assertThat(labelRepository.findById(event.label().id())).isEmpty();

            // When/Then - should not throw
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle unknown action gracefully")
        void shouldHandleUnknownAction() {
            // Given - event with unknown action
            GitHubLabelDTO labelDto = new GitHubLabelDTO(
                111222333L,
                "LA_unknown",
                "unknown-action-label",
                "desc",
                "000000",
                null,
                null
            );
            GitHubLabelEventDTO event = new GitHubLabelEventDTO(
                "unknown_action", // not created/edited/deleted
                labelDto,
                createTestRepoRef(),
                null
            );

            // When/Then - should not throw
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();
        }
    }

    // ==================== Repository Association Tests ====================

    @Nested
    @DisplayName("Repository Association")
    class RepositoryAssociation {

        @Test
        @DisplayName("Should associate created label with correct repository")
        void shouldAssociateLabelWithRepository() throws Exception {
            // Given
            GitHubLabelEventDTO event = loadPayload("label.created");

            // When
            handler.handleEvent(event);

            // Then
            assertThat(labelRepository.findById(event.label().id()))
                .isPresent()
                .get()
                .satisfies(label -> {
                    assertThat(label.getRepository()).isNotNull();
                    assertThat(label.getRepository().getId()).isEqualTo(FIXTURE_REPO_ID);
                    assertThat(label.getRepository().getNameWithOwner()).isEqualTo(FIXTURE_REPO_FULL_NAME);
                });
        }

        @Test
        @DisplayName("Should find label by repository and name")
        void shouldFindLabelByRepositoryAndName() throws Exception {
            // Given
            GitHubLabelEventDTO event = loadPayload("label.created");
            handler.handleEvent(event);

            // When
            var foundLabel = labelRepository.findByRepositoryIdAndName(FIXTURE_REPO_ID, event.label().name());

            // Then
            assertThat(foundLabel)
                .isPresent()
                .get()
                .satisfies(label -> {
                    assertThat(label.getId()).isEqualTo(event.label().id());
                    assertThat(label.getName()).isEqualTo(event.label().name());
                });
        }
    }

    // ==================== Label-Issue Association Tests ====================

    /**
     * Tests that verify label-issue relationships.
     * <p>
     * Note: This class does NOT use @Transactional because the handler uses REQUIRES_NEW.
     * We use TransactionTemplate for lazy loading assertions to avoid connection leaks.
     */
    @Nested
    @DisplayName("Label-Issue Relationship")
    class LabelIssueRelationship {

        @Test
        @DisplayName("Should preserve label-issue relationships after label edit")
        void shouldPreserveLabelIssueRelationshipsAfterEdit() throws Exception {
            // Given - create label and issue with that label
            GitHubLabelEventDTO createEvent = loadPayload("label.created");
            handler.handleEvent(createEvent);

            Label label = labelRepository.findById(createEvent.label().id()).orElseThrow();

            Issue issue = new Issue();
            issue.setId(12345L);
            issue.setNumber(1);
            issue.setTitle("Test Issue");
            issue.setState(Issue.State.OPEN);
            issue.setRepository(testRepository);
            issue.setCreatedAt(Instant.now());
            issue.setUpdatedAt(Instant.now());
            issue.getLabels().add(label);
            issueRepository.save(issue);

            // When - edit the label
            GitHubLabelDTO editedDto = new GitHubLabelDTO(
                createEvent.label().id(),
                createEvent.label().nodeId(),
                "renamed-documentation",
                "Updated description",
                "ff0000",
                null,
                null
            );
            GitHubLabelEventDTO editEvent = new GitHubLabelEventDTO("edited", editedDto, createTestRepoRef(), null);
            handler.handleEvent(editEvent);

            // Then - issue should still have the label (now with updated name)
            // Use TransactionTemplate for lazy loading assertions
            transactionTemplate.executeWithoutResult(status -> {
                Issue updatedIssue = issueRepository.findById(12345L).orElseThrow();
                assertThat(updatedIssue.getLabels())
                    .hasSize(1)
                    .first()
                    .satisfies(l -> {
                        assertThat(l.getName()).isEqualTo("renamed-documentation");
                        assertThat(l.getColor()).isEqualTo("ff0000");
                    });
            });
        }
    }
}
