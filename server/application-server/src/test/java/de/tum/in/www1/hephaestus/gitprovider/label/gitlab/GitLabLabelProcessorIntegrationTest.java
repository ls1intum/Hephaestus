package de.tum.in.www1.hephaestus.gitprovider.label.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.gitlab.dto.GitLabLabelDTO;
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

@DisplayName("GitLabLabelProcessor Integration Test")
class GitLabLabelProcessorIntegrationTest extends BaseIntegrationTest {

    private static final String FIXTURE_ORG_LOGIN = "hephaestustest";
    private static final String FIXTURE_REPO_FULL_NAME = "hephaestustest/demo-repository";

    @Autowired
    private GitLabLabelProcessor labelProcessor;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

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

    private ProcessingContext testContext() {
        return ProcessingContext.forSync(testWorkspace.getId(), testRepository);
    }

    @Nested
    @DisplayName("process()")
    class ProcessMethod {

        @Test
        @DisplayName("creates a new label with all fields")
        void shouldCreateNewLabel() {
            GitLabLabelDTO dto = new GitLabLabelDTO(
                "gid://gitlab/ProjectLabel/123",
                "bug",
                "#FF0000",
                "Something isn't working",
                "2026-01-15T10:00:00Z",
                "2026-01-15T10:00:00Z"
            );

            Label result = labelProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("bug");
            assertThat(result.getColor()).isEqualTo("#FF0000");
            assertThat(result.getDescription()).isEqualTo("Something isn't working");
            assertThat(result.getCreatedAt()).isNotNull();
            assertThat(result.getUpdatedAt()).isNotNull();
            assertThat(result.getLastSyncAt()).isNotNull();
            assertThat(result.getId()).isNegative(); // deterministic ID

            assertThat(eventListener.getCreatedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("maps GitLab title to Label name")
        void shouldMapTitleToName() {
            GitLabLabelDTO dto = new GitLabLabelDTO(null, "enhancement", "#428BCA", null, null, null);

            Label result = labelProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("enhancement");
        }

        @Test
        @DisplayName("updates existing label by name")
        void shouldUpdateExistingLabel() {
            // Create
            GitLabLabelDTO createDto = new GitLabLabelDTO(null, "bug", "#FF0000", "Old description", null, null);
            labelProcessor.process(createDto, testRepository, testContext());
            eventListener.clear();

            // Update
            GitLabLabelDTO updateDto = new GitLabLabelDTO(null, "bug", "#00FF00", "New description", null, null);
            Label result = labelProcessor.process(updateDto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getColor()).isEqualTo("#00FF00");
            assertThat(result.getDescription()).isEqualTo("New description");

            assertThat(eventListener.getUpdatedEvents()).hasSize(1);
            assertThat(labelRepository.findAllByRepository_Id(testRepository.getId())).hasSize(1);
        }

        @Test
        @DisplayName("is idempotent — processing same DTO twice produces one label")
        void shouldBeIdempotent() {
            GitLabLabelDTO dto = new GitLabLabelDTO(null, "bug", "#FF0000", null, null, null);

            labelProcessor.process(dto, testRepository, testContext());
            labelProcessor.process(dto, testRepository, testContext());

            assertThat(labelRepository.findAllByRepository_Id(testRepository.getId())).hasSize(1);
        }

        @Test
        @DisplayName("handles null description")
        void shouldHandleNullDescription() {
            GitLabLabelDTO dto = new GitLabLabelDTO(null, "no-desc", "#CCCCCC", null, null, null);

            Label result = labelProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getDescription()).isNull();
        }

        @Test
        @DisplayName("returns null for null DTO")
        void shouldReturnNullForNullDto() {
            Label result = labelProcessor.process(null, testRepository, testContext());
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for blank title")
        void shouldReturnNullForBlankTitle() {
            GitLabLabelDTO dto = new GitLabLabelDTO(null, "  ", "#FF0000", null, null, null);
            Label result = labelProcessor.process(dto, testRepository, testContext());
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("deduplicates group-level labels by name within a repository")
        void shouldDeduplicateGroupLabels() {
            // Same label name from different sources (project vs group)
            GitLabLabelDTO projectLabel = new GitLabLabelDTO(
                "gid://gitlab/ProjectLabel/1",
                "shared",
                "#FF0000",
                null,
                null,
                null
            );
            GitLabLabelDTO groupLabel = new GitLabLabelDTO(
                "gid://gitlab/GroupLabel/999",
                "shared",
                "#FF0000",
                null,
                null,
                null
            );

            labelProcessor.process(projectLabel, testRepository, testContext());
            labelProcessor.process(groupLabel, testRepository, testContext());

            assertThat(labelRepository.findAllByRepository_Id(testRepository.getId())).hasSize(1);
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteMethod {

        @Test
        @DisplayName("deletes label and publishes event")
        void shouldDeleteLabel() {
            GitLabLabelDTO dto = new GitLabLabelDTO(null, "to-delete", "#FF0000", null, null, null);
            Label label = labelProcessor.process(dto, testRepository, testContext());
            eventListener.clear();

            labelProcessor.delete(label.getId(), testContext());

            assertThat(labelRepository.findById(label.getId())).isEmpty();
            assertThat(eventListener.getDeletedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("handles null labelId gracefully")
        void shouldHandleNullId() {
            labelProcessor.delete(null, testContext());
            // No exception thrown
        }

        @Test
        @DisplayName("handles non-existent label gracefully")
        void shouldHandleNonExistentLabel() {
            labelProcessor.delete(-999L, testContext());
            assertThat(eventListener.getDeletedEvents()).isEmpty();
        }
    }

    // ==================== Test Data Setup ====================

    private void setupTestData() {
        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.lrz.de")
            .orElseGet(() ->
                gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, "https://gitlab.lrz.de"))
            );

        Organization org = new Organization();
        org.setNativeId(1L);
        org.setLogin(FIXTURE_ORG_LOGIN);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("HephaestusTest");
        org.setAvatarUrl("");
        org.setHtmlUrl("https://gitlab.lrz.de/hephaestustest");
        org.setProvider(provider);
        org = organizationRepository.save(org);

        testRepository = new Repository();
        testRepository.setNativeId(246765L);
        testRepository.setName("demo-repository");
        testRepository.setNameWithOwner(FIXTURE_REPO_FULL_NAME);
        testRepository.setHtmlUrl("https://gitlab.lrz.de/hephaestustest/demo-repository");
        testRepository.setVisibility(Repository.Visibility.PRIVATE);
        testRepository.setDefaultBranch("main");
        testRepository.setCreatedAt(Instant.now());
        testRepository.setUpdatedAt(Instant.now());
        testRepository.setPushedAt(Instant.now());
        testRepository.setOrganization(org);
        testRepository.setProvider(provider);
        testRepository = repositoryRepository.save(testRepository);

        testWorkspace = new Workspace();
        testWorkspace.setWorkspaceSlug("hephaestus-test-gitlab");
        testWorkspace.setDisplayName("HephaestusTest GitLab");
        testWorkspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        testWorkspace.setIsPubliclyViewable(true);
        testWorkspace.setOrganization(org);
        testWorkspace.setAccountLogin(FIXTURE_ORG_LOGIN);
        testWorkspace.setAccountType(AccountType.ORG);
        testWorkspace = workspaceRepository.save(testWorkspace);
    }

    // ==================== Test Event Listener ====================

    @Component
    static class TestLabelEventListener {

        private final List<DomainEvent.LabelCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.LabelUpdated> updatedEvents = new ArrayList<>();
        private final List<DomainEvent.LabelDeleted> deletedEvents = new ArrayList<>();

        @EventListener
        void onCreated(DomainEvent.LabelCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        void onUpdated(DomainEvent.LabelUpdated event) {
            updatedEvents.add(event);
        }

        @EventListener
        void onDeleted(DomainEvent.LabelDeleted event) {
            deletedEvents.add(event);
        }

        void clear() {
            createdEvents.clear();
            updatedEvents.clear();
            deletedEvents.clear();
        }

        List<DomainEvent.LabelCreated> getCreatedEvents() {
            return createdEvents;
        }

        List<DomainEvent.LabelUpdated> getUpdatedEvents() {
            return updatedEvents;
        }

        List<DomainEvent.LabelDeleted> getDeletedEvents() {
            return deletedEvents;
        }
    }
}
