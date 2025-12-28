package de.tum.in.www1.hephaestus.gitprovider.label.github;

import static org.assertj.core.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubLabelProcessor.
 * <p>
 * Tests the processor independently from the webhook handler to verify:
 * - Label upsert logic (create vs update)
 * - Domain event publishing (LabelProcessed, LabelDeleted)
 * - Context handling and workspace association
 * - Edge cases in DTO processing
 */
@DisplayName("GitHub Label Processor")
@Transactional
class GitHubLabelProcessorIntegrationTest extends BaseIntegrationTest {

    private static final Long TEST_ORG_ID = 215361191L;
    private static final Long TEST_REPO_ID = 998279771L;
    private static final String TEST_ORG_LOGIN = "HephaestusTest";
    private static final String TEST_REPO_FULL_NAME = "HephaestusTest/TestRepository";

    @Autowired
    private GitHubLabelProcessor processor;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private TestLabelEventListener eventListener;

    private Repository testRepository;
    private Workspace testWorkspace;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    private void setupTestData() {
        // Create organization
        Organization org = new Organization();
        org.setId(TEST_ORG_ID);
        org.setGithubId(TEST_ORG_ID);
        org.setLogin(TEST_ORG_LOGIN);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/" + TEST_ORG_ID);
        org = organizationRepository.save(org);

        // Create repository
        testRepository = new Repository();
        testRepository.setId(TEST_REPO_ID);
        testRepository.setName("TestRepository");
        testRepository.setNameWithOwner(TEST_REPO_FULL_NAME);
        testRepository.setHtmlUrl("https://github.com/" + TEST_REPO_FULL_NAME);
        testRepository.setVisibility(Repository.Visibility.PUBLIC);
        testRepository.setDefaultBranch("main");
        testRepository.setCreatedAt(Instant.now());
        testRepository.setUpdatedAt(Instant.now());
        testRepository.setPushedAt(Instant.now());
        testRepository.setOrganization(org);
        testRepository = repositoryRepository.save(testRepository);

        // Create workspace
        testWorkspace = new Workspace();
        testWorkspace.setWorkspaceSlug("hephaestus-test");
        testWorkspace.setDisplayName("Hephaestus Test");
        testWorkspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        testWorkspace.setIsPubliclyViewable(true);
        testWorkspace.setOrganization(org);
        testWorkspace.setAccountLogin(TEST_ORG_LOGIN);
        testWorkspace.setAccountType(AccountType.ORG);
        testWorkspace = workspaceRepository.save(testWorkspace);
    }

    private ProcessingContext createContext() {
        return ProcessingContext.forSync(testWorkspace.getId(), testRepository);
    }

    // ==================== Process (Create/Update) Tests ====================

    @Nested
    @DisplayName("Process Method")
    class ProcessMethod {

        @Test
        @DisplayName("Should create new label and publish LabelCreated event")
        void shouldCreateNewLabelAndPublishEvent() {
            // Given
            Long labelId = 111222333L;
            GitHubLabelDTO dto = new GitHubLabelDTO(
                labelId,
                "LA_node123",
                "new-feature",
                "A new feature label",
                "00ff00"
            );

            // When
            Label result = processor.process(dto, testRepository, createContext());

            // Then - verify label created
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(labelId);
            assertThat(result.getName()).isEqualTo("new-feature");
            assertThat(result.getColor()).isEqualTo("00ff00");
            assertThat(result.getDescription()).isEqualTo("A new feature label");
            assertThat(result.getRepository().getId()).isEqualTo(TEST_REPO_ID);

            // Verify persisted
            assertThat(labelRepository.findById(labelId)).isPresent();

            // Verify LabelCreated event published
            assertThat(eventListener.getCreatedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.label().id()).isEqualTo(labelId);
                    assertThat(event.workspaceId()).isEqualTo(testWorkspace.getId());
                    assertThat(event.repositoryId()).isEqualTo(TEST_REPO_ID);
                });
            assertThat(eventListener.getUpdatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("Should update existing label and publish LabelUpdated event")
        void shouldUpdateExistingLabelAndPublishEvent() {
            // Given - create existing label
            Long labelId = 444555666L;
            Label existing = new Label();
            existing.setId(labelId);
            existing.setName("old-name");
            existing.setColor("ffffff");
            existing.setDescription("Old description");
            existing.setRepository(testRepository);
            labelRepository.save(existing);

            eventListener.clear();

            GitHubLabelDTO dto = new GitHubLabelDTO(
                labelId,
                "LA_node456",
                "updated-name",
                "Updated description",
                "ff0000"
            );

            // When
            Label result = processor.process(dto, testRepository, createContext());

            // Then - verify label updated
            assertThat(result.getName()).isEqualTo("updated-name");
            assertThat(result.getColor()).isEqualTo("ff0000");
            assertThat(result.getDescription()).isEqualTo("Updated description");

            // Verify LabelUpdated event published
            assertThat(eventListener.getUpdatedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.label().name()).isEqualTo("updated-name");
                });
            assertThat(eventListener.getCreatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("Should return null for null DTO")
        void shouldReturnNullForNullDto() {
            // When
            Label result = processor.process(null, testRepository, createContext());

            // Then
            assertThat(result).isNull();
            assertThat(eventListener.getCreatedEvents()).isEmpty();
            assertThat(eventListener.getUpdatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("Should create label with generated ID when DTO has null ID (GraphQL sync scenario)")
        void shouldCreateLabelWithGeneratedIdWhenDtoHasNullId() {
            // Given - DTO without ID (like GraphQL sync)
            GitHubLabelDTO dto = new GitHubLabelDTO(
                null, // null ID - simulates GraphQL response
                "LA_node",
                "graphql-synced-label",
                "desc",
                "000000"
            );

            // When
            Label result = processor.process(dto, testRepository, createContext());

            // Then - label should be created with a generated negative ID
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getId()).isNegative(); // Generated IDs are negative to avoid collision
            assertThat(result.getName()).isEqualTo("graphql-synced-label");
            assertThat(eventListener.getCreatedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should update existing label by name when DTO has null ID")
        void shouldUpdateExistingLabelByNameWhenDtoHasNullId() {
            // Given - existing label
            Long existingId = 999888777L;
            Label existingLabel = new Label();
            existingLabel.setId(existingId);
            existingLabel.setName("existing-label");
            existingLabel.setColor("111111");
            existingLabel.setDescription("old description");
            existingLabel.setRepository(testRepository);
            labelRepository.save(existingLabel);

            // DTO without ID (like GraphQL sync) but matching name
            GitHubLabelDTO dto = new GitHubLabelDTO(
                null, // null ID
                "LA_node",
                "existing-label", // same name
                "new description",
                "222222"
            );

            // When
            Label result = processor.process(dto, testRepository, createContext());

            // Then - should update existing label, not create new one
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(existingId); // keeps original ID
            assertThat(result.getDescription()).isEqualTo("new description");
            assertThat(result.getColor()).isEqualTo("222222");
            assertThat(labelRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle label with null description")
        void shouldHandleLabelWithNullDescription() {
            // Given
            Long labelId = 777888999L;
            GitHubLabelDTO dto = new GitHubLabelDTO(
                labelId,
                "LA_node789",
                "no-desc-label",
                null, // null description
                "abcdef"
            );

            // When
            Label result = processor.process(dto, testRepository, createContext());

            // Then
            assertThat(result.getDescription()).isNull();
            assertThat(labelRepository.findById(labelId).get().getDescription()).isNull();
        }

        @Test
        @DisplayName("Should be idempotent - processing same DTO twice")
        void shouldBeIdempotent() {
            // Given
            Long labelId = 123123123L;
            GitHubLabelDTO dto = new GitHubLabelDTO(
                labelId,
                "LA_idem",
                "idempotent-label",
                "Same every time",
                "112233"
            );

            // When - process twice
            processor.process(dto, testRepository, createContext());
            eventListener.clear();
            processor.process(dto, testRepository, createContext());

            // Then - only one label exists, second time emits LabelUpdated (not Created)
            assertThat(labelRepository.count()).isEqualTo(1);
            assertThat(eventListener.getUpdatedEvents()).hasSize(1);
            assertThat(eventListener.getCreatedEvents()).isEmpty();
        }
    }

    // ==================== Delete Tests ====================

    @Nested
    @DisplayName("Delete Method")
    class DeleteMethod {

        @Test
        @DisplayName("Should delete existing label and publish LabelDeleted event")
        void shouldDeleteLabelAndPublishEvent() {
            // Given - create label
            Long labelId = 555666777L;
            Label label = new Label();
            label.setId(labelId);
            label.setName("to-delete");
            label.setColor("ff0000");
            label.setRepository(testRepository);
            labelRepository.save(label);

            assertThat(labelRepository.findById(labelId)).isPresent();

            // When
            processor.delete(labelId, createContext());

            // Then - label deleted
            assertThat(labelRepository.findById(labelId)).isEmpty();

            // Verify event published
            assertThat(eventListener.getDeletedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.labelId()).isEqualTo(labelId);
                    assertThat(event.labelName()).isEqualTo("to-delete");
                    assertThat(event.workspaceId()).isEqualTo(testWorkspace.getId());
                    assertThat(event.repositoryId()).isEqualTo(TEST_REPO_ID);
                });
        }

        @Test
        @DisplayName("Should handle deletion of non-existent label gracefully")
        void shouldHandleDeletionOfNonExistentLabel() {
            // Given - label doesn't exist
            Long nonExistentId = 999999999L;
            assertThat(labelRepository.findById(nonExistentId)).isEmpty();

            // When/Then - should not throw
            assertThatCode(() -> processor.delete(nonExistentId, createContext())).doesNotThrowAnyException();

            // No event published
            assertThat(eventListener.getDeletedEvents()).isEmpty();
        }

        @Test
        @DisplayName("Should handle null label ID")
        void shouldHandleNullLabelId() {
            // When/Then - should not throw
            assertThatCode(() -> processor.delete(null, createContext())).doesNotThrowAnyException();

            // No event published
            assertThat(eventListener.getDeletedEvents()).isEmpty();
        }
    }

    // ==================== Test Event Listener ====================

    @Component
    static class TestLabelEventListener {

        private final List<DomainEvent.LabelCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.LabelUpdated> updatedEvents = new ArrayList<>();
        private final List<DomainEvent.LabelDeleted> deletedEvents = new ArrayList<>();

        @EventListener
        public void onLabelCreated(DomainEvent.LabelCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onLabelUpdated(DomainEvent.LabelUpdated event) {
            updatedEvents.add(event);
        }

        @EventListener
        public void onLabelDeleted(DomainEvent.LabelDeleted event) {
            deletedEvents.add(event);
        }

        public List<DomainEvent.LabelCreated> getCreatedEvents() {
            return new ArrayList<>(createdEvents);
        }

        public List<DomainEvent.LabelUpdated> getUpdatedEvents() {
            return new ArrayList<>(updatedEvents);
        }

        public List<DomainEvent.LabelDeleted> getDeletedEvents() {
            return new ArrayList<>(deletedEvents);
        }

        public void clear() {
            createdEvents.clear();
            updatedEvents.clear();
            deletedEvents.clear();
        }
    }
}
