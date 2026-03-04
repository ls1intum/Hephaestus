package de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab.dto.GitLabMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for GitLabMilestoneProcessor.
 * <p>
 * Tests the processor independently from the webhook handler to verify:
 * <ul>
 *   <li>Milestone upsert logic (create vs update)</li>
 *   <li>State mapping: GitLab {@code "active"} &rarr; OPEN, {@code "closed"} &rarr; CLOSED</li>
 *   <li>Domain event publishing (MilestoneCreated, MilestoneUpdated, MilestoneDeleted)</li>
 *   <li>Due date parsing (date-only string &rarr; Instant at midnight UTC)</li>
 *   <li>Group milestone deterministic nativeId</li>
 *   <li>htmlUrl construction (GraphQL webPath vs webhook projectWebUrl)</li>
 * </ul>
 */
@DisplayName("GitLab Milestone Processor")
@TestPropertySource(
    properties = {
        "hephaestus.gitlab.enabled=true",
        "hephaestus.gitlab.default-server-url=https://gitlab.lrz.de",
        "hephaestus.gitlab.connect-timeout=30s",
        "hephaestus.gitlab.read-timeout=60s",
        "hephaestus.gitlab.rate-limit-delay=200ms",
        "hephaestus.gitlab.sync-page-delay=5m",
    }
)
class GitLabMilestoneProcessorIntegrationTest extends BaseIntegrationTest {

    private static final String FIXTURE_ORG_LOGIN = "hephaestustest";
    private static final String FIXTURE_REPO_FULL_NAME = "hephaestustest/demo-repository";

    @Autowired
    private GitLabMilestoneProcessor milestoneProcessor;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private TestMilestoneEventListener eventListener;

    private Repository testRepository;
    private Workspace testWorkspace;
    private GitProvider gitlabProvider;

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
        @DisplayName("creates a new milestone with all fields")
        void shouldCreateNewMilestone() {
            GitLabMilestoneDTO dto = new GitLabMilestoneDTO(
                18066L,
                2,
                "v2.0.0",
                "Second major release",
                "active",
                "2026-06-01",
                "/hephaestustest/demo-repository/-/milestones/2",
                null,
                false,
                3,
                10,
                "2026-01-31T18:27:14Z",
                "2026-01-31T18:27:14Z"
            );

            Milestone result = milestoneProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getNativeId()).isEqualTo(18066L);
            assertThat(result.getNumber()).isEqualTo(2);
            assertThat(result.getTitle()).isEqualTo("v2.0.0");
            assertThat(result.getDescription()).isEqualTo("Second major release");
            assertThat(result.getState()).isEqualTo(Milestone.State.OPEN);
            assertThat(result.getDueOn()).isEqualTo(LocalDate.of(2026, 6, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
            assertThat(result.getHtmlUrl()).isEqualTo(
                "https://gitlab.lrz.de/hephaestustest/demo-repository/-/milestones/2"
            );
            assertThat(result.getOpenIssuesCount()).isEqualTo(7);
            assertThat(result.getClosedIssuesCount()).isEqualTo(3);
            assertThat(result.getCreatedAt()).isNotNull();
            assertThat(result.getUpdatedAt()).isNotNull();
            assertThat(result.getLastSyncAt()).isNotNull();

            assertThat(eventListener.getCreatedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("maps 'active' state to OPEN")
        void shouldMapActiveStateToOpen() {
            GitLabMilestoneDTO dto = createDto(1, "active");

            Milestone result = milestoneProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getState()).isEqualTo(Milestone.State.OPEN);
        }

        @Test
        @DisplayName("maps 'closed' state to CLOSED and approximates closedAt")
        void shouldMapClosedStateToClosed() {
            GitLabMilestoneDTO dto = new GitLabMilestoneDTO(
                18066L,
                2,
                "v2.0.0",
                null,
                "closed",
                null,
                null,
                null,
                false,
                null,
                null,
                "2026-01-31T18:27:14Z",
                "2026-01-31T18:27:16Z"
            );

            Milestone result = milestoneProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getState()).isEqualTo(Milestone.State.CLOSED);
            assertThat(result.getClosedAt()).isNotNull();
        }

        @Test
        @DisplayName("clears closedAt when milestone is reopened")
        void shouldClearClosedAtOnReopen() {
            // Create as closed
            GitLabMilestoneDTO closedDto = new GitLabMilestoneDTO(
                18066L,
                2,
                "v2.0.0",
                null,
                "closed",
                null,
                null,
                null,
                false,
                null,
                null,
                "2026-01-31T18:27:14Z",
                "2026-01-31T18:27:16Z"
            );
            milestoneProcessor.process(closedDto, testRepository, testContext());

            // Reopen
            GitLabMilestoneDTO reopenDto = new GitLabMilestoneDTO(
                18066L,
                2,
                "v2.0.0",
                null,
                "active",
                null,
                null,
                null,
                false,
                null,
                null,
                "2026-01-31T18:27:14Z",
                "2026-01-31T18:27:19Z"
            );
            Milestone result = milestoneProcessor.process(reopenDto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getState()).isEqualTo(Milestone.State.OPEN);
            assertThat(result.getClosedAt()).isNull();
        }

        @Test
        @DisplayName("updates existing milestone by iid")
        void shouldUpdateExistingMilestone() {
            // Create
            GitLabMilestoneDTO createDto = createDto(2, "active");
            milestoneProcessor.process(createDto, testRepository, testContext());
            eventListener.clear();

            // Update
            GitLabMilestoneDTO updateDto = new GitLabMilestoneDTO(
                18066L,
                2,
                "v2.0.0 - Updated",
                "Updated description",
                "active",
                "2026-12-31",
                null,
                null,
                false,
                null,
                null,
                "2026-01-31T18:27:14Z",
                "2026-02-15T10:00:00Z"
            );
            Milestone result = milestoneProcessor.process(updateDto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("v2.0.0 - Updated");
            assertThat(result.getDescription()).isEqualTo("Updated description");
            assertThat(result.getDueOn()).isEqualTo(
                LocalDate.of(2026, 12, 31).atStartOfDay(ZoneOffset.UTC).toInstant()
            );

            assertThat(eventListener.getUpdatedEvents()).hasSize(1);
            assertThat(milestoneRepository.findAllByRepository_Id(testRepository.getId())).hasSize(1);
        }

        @Test
        @DisplayName("is idempotent — processing same DTO twice produces one milestone")
        void shouldBeIdempotent() {
            GitLabMilestoneDTO dto = createDto(2, "active");

            milestoneProcessor.process(dto, testRepository, testContext());
            milestoneProcessor.process(dto, testRepository, testContext());

            assertThat(milestoneRepository.findAllByRepository_Id(testRepository.getId())).hasSize(1);
        }

        @Test
        @DisplayName("uses deterministic negative nativeId for group milestones")
        void shouldUseDeterministicIdForGroupMilestone() {
            GitLabMilestoneDTO dto = new GitLabMilestoneDTO(
                99999L,
                5,
                "Group Milestone",
                "Inherited",
                "active",
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                null
            );

            Milestone result = milestoneProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getNativeId()).isNegative();
            assertThat(result.getNumber()).isEqualTo(5);
        }

        @Test
        @DisplayName("constructs htmlUrl from webPath (GraphQL)")
        void shouldConstructHtmlUrlFromWebPath() {
            GitLabMilestoneDTO dto = new GitLabMilestoneDTO(
                18066L,
                2,
                "v2.0.0",
                null,
                "active",
                null,
                "/hephaestustest/demo-repository/-/milestones/2",
                null,
                false,
                null,
                null,
                null,
                null
            );

            Milestone result = milestoneProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getHtmlUrl()).isEqualTo(
                "https://gitlab.lrz.de/hephaestustest/demo-repository/-/milestones/2"
            );
        }

        @Test
        @DisplayName("constructs htmlUrl from projectWebUrl (webhook)")
        void shouldConstructHtmlUrlFromProjectWebUrl() {
            GitLabMilestoneDTO dto = new GitLabMilestoneDTO(
                18066L,
                2,
                "v2.0.0",
                null,
                "active",
                null,
                null,
                "https://gitlab.lrz.de/hephaestustest/demo-repository",
                false,
                null,
                null,
                null,
                null
            );

            Milestone result = milestoneProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getHtmlUrl()).isEqualTo(
                "https://gitlab.lrz.de/hephaestustest/demo-repository/-/milestones/2"
            );
        }

        @Test
        @DisplayName("parses due date from date-only string")
        void shouldParseDueDate() {
            GitLabMilestoneDTO dto = new GitLabMilestoneDTO(
                18066L,
                2,
                "v2.0.0",
                null,
                "active",
                "2026-06-01",
                null,
                null,
                false,
                null,
                null,
                null,
                null
            );

            Milestone result = milestoneProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getDueOn()).isEqualTo(LocalDate.of(2026, 6, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
        }

        @Test
        @DisplayName("handles null description")
        void shouldHandleNullDescription() {
            GitLabMilestoneDTO dto = createDto(2, "active");

            Milestone result = milestoneProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getDescription()).isNull();
        }

        @Test
        @DisplayName("handles null dueDate")
        void shouldHandleNullDueDate() {
            GitLabMilestoneDTO dto = new GitLabMilestoneDTO(
                18066L,
                2,
                "v2.0.0",
                null,
                "active",
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null
            );

            Milestone result = milestoneProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getDueOn()).isNull();
        }

        @Test
        @DisplayName("returns null for null DTO")
        void shouldReturnNullForNullDto() {
            Milestone result = milestoneProcessor.process(null, testRepository, testContext());

            assertThat(result).isNull();
            assertThat(eventListener.getCreatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("returns null for invalid iid (zero)")
        void shouldReturnNullForInvalidIid() {
            GitLabMilestoneDTO dto = new GitLabMilestoneDTO(
                18066L,
                0,
                "Invalid",
                null,
                "active",
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null
            );

            Milestone result = milestoneProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("defaults to OPEN state for null state")
        void shouldDefaultToOpenForNullState() {
            GitLabMilestoneDTO dto = new GitLabMilestoneDTO(
                18066L,
                2,
                "v2.0.0",
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null
            );

            Milestone result = milestoneProcessor.process(dto, testRepository, testContext());

            assertThat(result).isNotNull();
            assertThat(result.getState()).isEqualTo(Milestone.State.OPEN);
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteMethod {

        @Test
        @DisplayName("deletes milestone and publishes event")
        void shouldDeleteMilestone() {
            GitLabMilestoneDTO dto = createDto(2, "active");
            Milestone milestone = milestoneProcessor.process(dto, testRepository, testContext());
            eventListener.clear();

            milestoneProcessor.delete(milestone.getId(), testContext());

            assertThat(milestoneRepository.findById(milestone.getId())).isEmpty();
            assertThat(eventListener.getDeletedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("handles null milestoneId gracefully")
        void shouldHandleNullId() {
            assertThatCode(() -> milestoneProcessor.delete(null, testContext())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("handles non-existent milestone gracefully")
        void shouldHandleNonExistentMilestone() {
            milestoneProcessor.delete(-999L, testContext());
            assertThat(eventListener.getDeletedEvents()).isEmpty();
        }
    }

    // ==================== Helpers ====================

    private GitLabMilestoneDTO createDto(int iid, String state) {
        return new GitLabMilestoneDTO(
            18066L,
            iid,
            "v2.0.0",
            null,
            state,
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            null
        );
    }

    // ==================== Test Data Setup ====================

    private void setupTestData() {
        gitlabProvider = gitProviderRepository
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
        org.setProvider(gitlabProvider);
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
        testRepository.setProvider(gitlabProvider);
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
    static class TestMilestoneEventListener {

        private final List<DomainEvent.MilestoneCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.MilestoneUpdated> updatedEvents = new ArrayList<>();
        private final List<DomainEvent.MilestoneDeleted> deletedEvents = new ArrayList<>();

        @EventListener
        void onCreated(DomainEvent.MilestoneCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        void onUpdated(DomainEvent.MilestoneUpdated event) {
            updatedEvents.add(event);
        }

        @EventListener
        void onDeleted(DomainEvent.MilestoneDeleted event) {
            deletedEvents.add(event);
        }

        void clear() {
            createdEvents.clear();
            updatedEvents.clear();
            deletedEvents.clear();
        }

        List<DomainEvent.MilestoneCreated> getCreatedEvents() {
            return createdEvents;
        }

        List<DomainEvent.MilestoneUpdated> getUpdatedEvents() {
            return updatedEvents;
        }

        List<DomainEvent.MilestoneDeleted> getDeletedEvents() {
            return deletedEvents;
        }
    }
}
