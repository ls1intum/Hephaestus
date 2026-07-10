package de.tum.cit.aet.hephaestus.integration.scm.github.label;

import static org.assertj.core.api.Assertions.*;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.Label;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.LabelRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventType;
import de.tum.cit.aet.hephaestus.integration.scm.github.label.dto.GitHubLabelDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.label.dto.GitHubLabelEventDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

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
    private IdentityProviderRepository gitProviderRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Repository testRepository;
    private IdentityProvider gitProvider;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

    private void setupTestData() {
        // Create GitHub provider
        gitProvider = gitProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseGet(() ->
                gitProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
            );

        // Create organization matching fixture data
        Organization org = new Organization();
        org.setNativeId(FIXTURE_ORG_ID);
        org.setLogin(FIXTURE_ORG_LOGIN);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/" + FIXTURE_ORG_ID + "?v=4");
        org.setHtmlUrl("https://github.com/" + FIXTURE_ORG_LOGIN);
        org.setProvider(gitProvider);
        org = organizationRepository.save(org);

        // Create repository matching fixture data
        testRepository = new Repository();
        testRepository.setNativeId(FIXTURE_REPO_ID);
        testRepository.setName(FIXTURE_REPO_NAME);
        testRepository.setNameWithOwner(FIXTURE_REPO_FULL_NAME);
        testRepository.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME);
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
        workspace.setAccountLogin(FIXTURE_ORG_LOGIN);
        workspace.setAccountType(AccountType.ORG);
        workspaceRepository.save(workspace);
    }

    @Test
    void shouldReturnCorrectEventType() {
        assertThat(handler.key().eventType()).isEqualTo("repository.label");
    }

    @Test
    void shouldProcessCreatedLabelEvents() throws Exception {
        GitHubLabelEventDTO event = loadPayload("label.created");
        assertThat(labelRepository.findByNativeIdAndProviderId(FIXTURE_LABEL_ID, gitProvider.getId())).isEmpty();

        handler.handleEvent(event);

        // Then - verify ALL persisted fields against hardcoded fixture values
        Label label = labelRepository.findByNativeIdAndProviderId(FIXTURE_LABEL_ID, gitProvider.getId()).orElseThrow();

        // Core schema fields (mapped to DB columns)
        assertThat(label.getNativeId()).isEqualTo(FIXTURE_LABEL_ID);
        assertThat(label.getName()).isEqualTo(FIXTURE_LABEL_NAME);
        assertThat(label.getColor()).isEqualTo(FIXTURE_LABEL_COLOR);
        assertThat(label.getDescription()).isEqualTo(FIXTURE_LABEL_DESCRIPTION);

        // Repository association (foreign key)
        assertThat(label.getRepository()).isNotNull();
        assertThat(label.getRepository().getId()).isEqualTo(testRepository.getId());

        // Note: createdAt/updatedAt are not provided in webhook payloads (only in GraphQL)
        // Note: lastSyncAt is ETL infrastructure, not set by webhook handler
    }

    @Test
    void shouldProcessEditedLabelEvents() throws Exception {
        // Given - create existing label with stale data
        GitHubLabelEventDTO event = loadPayload("label.edited");
        Long labelId = event.label().id();

        Label existingLabel = new Label();
        existingLabel.setNativeId(labelId);
        existingLabel.setProvider(gitProvider);
        existingLabel.setName("stale-name");
        existingLabel.setColor("ffffff");
        existingLabel.setDescription("stale description");
        existingLabel.setRepository(testRepository);
        labelRepository.save(existingLabel);

        handler.handleEvent(event);

        // Then - verify all mutable fields are updated from DTO
        Label label = labelRepository.findByNativeIdAndProviderId(labelId, gitProvider.getId()).orElseThrow();
        assertThat(label.getName()).isEqualTo(event.label().name());
        assertThat(label.getColor()).isEqualTo(event.label().color());
        assertThat(label.getDescription()).isEqualTo(event.label().description());

        // Verify repository association preserved (not overwritten)
        assertThat(label.getRepository().getId()).isEqualTo(testRepository.getId());
    }

    @Test
    void shouldProcessDeletedLabelEvents() throws Exception {
        GitHubLabelEventDTO event = loadPayload("label.deleted");

        // Create existing label
        Label existingLabel = new Label();
        existingLabel.setNativeId(event.label().id());
        existingLabel.setProvider(gitProvider);
        existingLabel.setName(event.label().name());
        existingLabel.setColor(event.label().color());
        existingLabel.setRepository(testRepository);
        labelRepository.save(existingLabel);

        // Verify it exists
        assertThat(labelRepository.findByNativeIdAndProviderId(event.label().id(), gitProvider.getId())).isPresent();

        handler.handleEvent(event);

        assertThat(labelRepository.findByNativeIdAndProviderId(event.label().id(), gitProvider.getId())).isEmpty();
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
            "https://github.com/" + FIXTURE_REPO_FULL_NAME,
            null
        );
    }

    // Edge Case Tests

    @Nested
    class EdgeCases {

        @Test
        void shouldHandleNullLabelGracefully() {
            // Given - event with null label
            GitHubLabelEventDTO event = new GitHubLabelEventDTO("created", null, createTestRepoRef(), null);

            // When/Then - should not throw, just log warning
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();

            // No labels should be created
            assertThat(labelRepository.count()).isZero();
        }

        @Test
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

            handler.handleEvent(event);

            assertThat(labelRepository.findByNativeIdAndProviderId(labelId, gitProvider.getId()))
                .isPresent()
                .get()
                .satisfies(label -> {
                    assertThat(label.getName()).isEqualTo("no-description-label");
                    assertThat(label.getDescription()).isNull();
                });
        }

        @Test
        void shouldUpdateDescriptionToNull() throws Exception {
            // Given - existing label with description
            Long labelId = 987654321L;
            Label existingLabel = new Label();
            existingLabel.setNativeId(labelId);
            existingLabel.setProvider(gitProvider);
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
            assertThat(labelRepository.findByNativeIdAndProviderId(labelId, gitProvider.getId()))
                .isPresent()
                .get()
                .extracting(Label::getDescription)
                .isNull();
        }

        @Test
        void shouldHandleIdempotentCreation() throws Exception {
            GitHubLabelEventDTO event = loadPayload("label.created");

            // When - handle same event twice
            handler.handleEvent(event);
            handler.handleEvent(event);

            // Then - only one label should exist
            assertThat(
                labelRepository.findByNativeIdAndProviderId(event.label().id(), gitProvider.getId())
            ).isPresent();
            assertThat(labelRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldHandleDeletionOfNonExistentLabel() throws Exception {
            // Given - label doesn't exist
            GitHubLabelEventDTO event = loadPayload("label.deleted");
            assertThat(labelRepository.findByNativeIdAndProviderId(event.label().id(), gitProvider.getId())).isEmpty();

            // When/Then - should not throw
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();
        }

        @Test
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

    // Repository Association Tests

    @Nested
    class RepositoryAssociation {

        @Test
        void shouldAssociateLabelWithRepository() throws Exception {
            GitHubLabelEventDTO event = loadPayload("label.created");

            handler.handleEvent(event);

            // Then — use TransactionTemplate for lazy-loaded repository access
            transactionTemplate.executeWithoutResult(status -> {
                assertThat(labelRepository.findByNativeIdAndProviderId(event.label().id(), gitProvider.getId()))
                    .isPresent()
                    .get()
                    .satisfies(label -> {
                        assertThat(label.getRepository()).isNotNull();
                        assertThat(label.getRepository().getId()).isEqualTo(testRepository.getId());
                        assertThat(label.getRepository().getNameWithOwner()).isEqualTo(FIXTURE_REPO_FULL_NAME);
                    });
            });
        }

        @Test
        void shouldFindLabelByRepositoryAndName() throws Exception {
            GitHubLabelEventDTO event = loadPayload("label.created");
            handler.handleEvent(event);

            var foundLabel = labelRepository.findByRepositoryIdAndName(testRepository.getId(), event.label().name());

            assertThat(foundLabel)
                .isPresent()
                .get()
                .satisfies(label -> {
                    assertThat(label.getNativeId()).isEqualTo(event.label().id());
                    assertThat(label.getName()).isEqualTo(event.label().name());
                });
        }
    }

    // Label-Issue Association Tests

    /**
     * Tests that verify label-issue relationships.
     * <p>
     * Note: This class does NOT use @Transactional because the handler uses REQUIRES_NEW.
     * We use TransactionTemplate for lazy loading assertions to avoid connection leaks.
     */
    @Nested
    class LabelIssueRelationship {

        @Test
        void shouldPreserveLabelIssueRelationshipsAfterEdit() throws Exception {
            // Given - create label and issue with that label
            GitHubLabelEventDTO createEvent = loadPayload("label.created");
            handler.handleEvent(createEvent);

            Label label = labelRepository
                .findByNativeIdAndProviderId(createEvent.label().id(), gitProvider.getId())
                .orElseThrow();

            Issue issue = new Issue();
            issue.setNativeId(12345L);
            issue.setNumber(1);
            issue.setTitle("Test Issue");
            issue.setState(Issue.State.OPEN);
            issue.setRepository(testRepository);
            issue.setCreatedAt(Instant.now());
            issue.setUpdatedAt(Instant.now());
            issue.setProvider(gitProvider);
            issue.getLabels().add(label);
            Issue savedIssue = issueRepository.save(issue);
            Long savedIssueId = savedIssue.getId();

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
                Issue updatedIssue = issueRepository.findById(savedIssueId).orElseThrow();
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
