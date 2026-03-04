package de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab.dto.GitLabMilestoneEventDTO;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for GitLabMilestoneMessageHandler.
 * <p>
 * Tests the full webhook handling flow: JSON fixtures &rarr; DTO &rarr; handler &rarr; processor &rarr; DB.
 * <p>
 * <b>Fixture values (milestone.create.json — Milestone IID #2):</b>
 * <ul>
 *   <li>Native ID: 18066</li>
 *   <li>IID: 2</li>
 *   <li>Title: "v2.0.0"</li>
 *   <li>Description: "Second major release"</li>
 *   <li>State: active &rarr; OPEN</li>
 *   <li>Due date: 2026-06-01</li>
 *   <li>Project ID: 246765</li>
 * </ul>
 */
@Tag("integration")
@DisplayName("GitLab Milestone Message Handler")
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
class GitLabMilestoneMessageHandlerIntegrationTest extends BaseIntegrationTest {

    // Fixture values from milestone.create.json
    private static final long NATIVE_MILESTONE_ID = 18066L;
    private static final int MILESTONE_IID = 2;
    private static final String FIXTURE_MILESTONE_TITLE = "v2.0.0";
    private static final String FIXTURE_MILESTONE_DESC = "Second major release";
    private static final String FIXTURE_DUE_DATE = "2026-06-01";
    private static final String FIXTURE_MILESTONE_HTML_URL =
        "https://gitlab.lrz.de/hephaestustest/demo-repository/-/milestones/2";

    // Repository/org setup
    private static final String FIXTURE_ORG_LOGIN = "hephaestustest";
    private static final String FIXTURE_REPO_FULL_NAME = "hephaestustest/demo-repository";

    @Autowired
    private GitLabMilestoneMessageHandler handler;

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
    private ObjectMapper objectMapper;

    @Autowired
    private TestMilestoneWebhookEventListener eventListener;

    private Repository savedRepo;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    // ==================== Event Type ====================

    @Nested
    @DisplayName("Event Type")
    class EventTypeTest {

        @Test
        @DisplayName("returns MILESTONE as event type")
        void returnsCorrectEventType() {
            assertThat(handler.getEventType()).isEqualTo(GitLabEventType.MILESTONE);
        }
    }

    // ==================== Basic Lifecycle ====================

    @Nested
    @DisplayName("Basic Lifecycle Events")
    class BasicLifecycleEvents {

        @Test
        @DisplayName("persists milestone with all fields on 'create' event")
        void shouldPersistMilestoneOnCreateEvent() throws Exception {
            GitLabMilestoneEventDTO event = loadPayload("milestone.create");

            handler.handleEvent(event);

            Milestone milestone = milestoneRepository
                .findByNumberAndRepositoryId(MILESTONE_IID, savedRepo.getId())
                .orElseThrow();

            assertThat(milestone.getNativeId()).isEqualTo(NATIVE_MILESTONE_ID);
            assertThat(milestone.getNumber()).isEqualTo(MILESTONE_IID);
            assertThat(milestone.getTitle()).isEqualTo(FIXTURE_MILESTONE_TITLE);
            assertThat(milestone.getDescription()).isEqualTo(FIXTURE_MILESTONE_DESC);
            assertThat(milestone.getState()).isEqualTo(Milestone.State.OPEN);
            assertThat(milestone.getDueOn()).isNotNull();
            assertThat(milestone.getHtmlUrl()).isEqualTo(FIXTURE_MILESTONE_HTML_URL);
            assertThat(milestone.getRepository().getId()).isEqualTo(savedRepo.getId());

            assertThat(eventListener.getCreatedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("closes milestone on 'close' event")
        void shouldCloseMilestoneOnCloseEvent() throws Exception {
            // Create first
            handler.handleEvent(loadPayload("milestone.create"));
            eventListener.clear();

            // Close
            handler.handleEvent(loadPayload("milestone.close"));

            Milestone milestone = milestoneRepository
                .findByNumberAndRepositoryId(MILESTONE_IID, savedRepo.getId())
                .orElse(null);
            assertThat(milestone).isNotNull();
            assertThat(milestone.getState()).isEqualTo(Milestone.State.CLOSED);
            assertThat(milestone.getClosedAt()).isNotNull();

            assertThat(eventListener.getUpdatedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("reopens milestone on 'reopen' event")
        void shouldReopenMilestoneOnReopenEvent() throws Exception {
            // Create and close
            handler.handleEvent(loadPayload("milestone.create"));
            handler.handleEvent(loadPayload("milestone.close"));
            eventListener.clear();

            // Reopen
            handler.handleEvent(loadPayload("milestone.reopen"));

            Milestone milestone = milestoneRepository
                .findByNumberAndRepositoryId(MILESTONE_IID, savedRepo.getId())
                .orElse(null);
            assertThat(milestone).isNotNull();
            assertThat(milestone.getState()).isEqualTo(Milestone.State.OPEN);
            assertThat(milestone.getClosedAt()).isNull();

            assertThat(eventListener.getUpdatedEvents()).hasSize(1);
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("handles missing repository gracefully")
        void shouldHandleMissingRepositoryGracefully() throws Exception {
            repositoryRepository.deleteAll();

            GitLabMilestoneEventDTO event = loadPayload("milestone.create");

            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();
            assertThat(milestoneRepository.count()).isZero();
        }

        @Test
        @DisplayName("is idempotent — processing same event twice")
        void shouldBeIdempotent() throws Exception {
            GitLabMilestoneEventDTO event = loadPayload("milestone.create");

            handler.handleEvent(event);
            long countAfterFirst = milestoneRepository.count();

            handler.handleEvent(event);

            assertThat(milestoneRepository.count()).isEqualTo(countAfterFirst);
        }

        @Test
        @DisplayName("full lifecycle: create → close → reopen")
        void shouldHandleFullLifecycle() throws Exception {
            handler.handleEvent(loadPayload("milestone.create"));
            assertThat(
                milestoneRepository
                    .findByNumberAndRepositoryId(MILESTONE_IID, savedRepo.getId())
                    .orElseThrow()
                    .getState()
            ).isEqualTo(Milestone.State.OPEN);

            handler.handleEvent(loadPayload("milestone.close"));
            assertThat(
                milestoneRepository
                    .findByNumberAndRepositoryId(MILESTONE_IID, savedRepo.getId())
                    .orElseThrow()
                    .getState()
            ).isEqualTo(Milestone.State.CLOSED);

            handler.handleEvent(loadPayload("milestone.reopen"));
            assertThat(
                milestoneRepository
                    .findByNumberAndRepositoryId(MILESTONE_IID, savedRepo.getId())
                    .orElseThrow()
                    .getState()
            ).isEqualTo(Milestone.State.OPEN);

            assertThat(milestoneRepository.count()).isEqualTo(1);
        }
    }

    // ==================== Helpers ====================

    private GitLabMilestoneEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("gitlab/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitLabMilestoneEventDTO.class);
    }

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

        Repository repo = new Repository();
        repo.setNativeId(246765L);
        repo.setName("demo-repository");
        repo.setNameWithOwner(FIXTURE_REPO_FULL_NAME);
        repo.setHtmlUrl("https://gitlab.lrz.de/hephaestustest/demo-repository");
        repo.setVisibility(Repository.Visibility.PRIVATE);
        repo.setDefaultBranch("main");
        repo.setCreatedAt(Instant.now());
        repo.setUpdatedAt(Instant.now());
        repo.setPushedAt(Instant.now());
        repo.setOrganization(org);
        repo.setProvider(provider);
        savedRepo = repositoryRepository.save(repo);

        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug("hephaestus-test-gitlab");
        workspace.setDisplayName("HephaestusTest GitLab");
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        workspace.setIsPubliclyViewable(true);
        workspace.setOrganization(org);
        workspace.setAccountLogin(FIXTURE_ORG_LOGIN);
        workspace.setAccountType(AccountType.ORG);
        workspaceRepository.save(workspace);
    }

    // ==================== Test Event Listener ====================

    @Component
    static class TestMilestoneWebhookEventListener {

        private final List<DomainEvent.MilestoneCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.MilestoneUpdated> updatedEvents = new ArrayList<>();
        private final List<DomainEvent.MilestoneDeleted> deletedEvents = new ArrayList<>();

        @EventListener
        public void onCreated(DomainEvent.MilestoneCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onUpdated(DomainEvent.MilestoneUpdated event) {
            updatedEvents.add(event);
        }

        @EventListener
        public void onDeleted(DomainEvent.MilestoneDeleted event) {
            deletedEvents.add(event);
        }

        public List<DomainEvent.MilestoneCreated> getCreatedEvents() {
            return new ArrayList<>(createdEvents);
        }

        public List<DomainEvent.MilestoneUpdated> getUpdatedEvents() {
            return new ArrayList<>(updatedEvents);
        }

        public List<DomainEvent.MilestoneDeleted> getDeletedEvents() {
            return new ArrayList<>(deletedEvents);
        }

        public void clear() {
            createdEvents.clear();
            updatedEvents.clear();
            deletedEvents.clear();
        }
    }
}
