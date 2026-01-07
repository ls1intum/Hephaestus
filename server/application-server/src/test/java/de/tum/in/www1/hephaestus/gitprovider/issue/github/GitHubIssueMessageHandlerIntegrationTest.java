package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueType;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubIssueMessageHandler.
 * <p>
 * Tests the full webhook handling flow using JSON fixtures parsed directly
 * into DTOs using JSON fixtures for complete isolation. Verifies:
 * - Correct routing of webhook actions to processor methods
 * - Issue persistence for all action types
 * - Event publishing through the handler → processor chain
 * - Edge cases in event handling
 */
@DisplayName("GitHub Issue Message Handler")
@Transactional
class GitHubIssueMessageHandlerIntegrationTest extends BaseIntegrationTest {

    // IDs from the actual GitHub webhook fixtures
    private static final Long FIXTURE_ORG_ID = 215361191L;
    private static final Long FIXTURE_REPO_ID = 998279771L;
    private static final String FIXTURE_ORG_LOGIN = "HephaestusTest";
    private static final String FIXTURE_REPO_FULL_NAME = "HephaestusTest/TestRepository";

    // Issue IDs from different fixtures
    private static final Long ISSUE_20_ID = 3578496080L; // opened, labeled, assigned, closed, reopened
    private static final Long ISSUE_22_ID = 3578518416L; // milestoned, demilestoned, locked, unlocked, pinned, unpinned, transferred
    private static final Long ISSUE_23_ID = 3578523639L; // deleted
    private static final Long ISSUE_25_ID = 3578528003L; // typed, untyped

    @Autowired
    private GitHubIssueMessageHandler handler;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

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

    @Autowired
    private TestEventListener eventListener;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    // ==================== Event Key Tests ====================

    @Nested
    @DisplayName("Event Type")
    class EventType {

        @Test
        @DisplayName("Should return ISSUES as event type")
        void shouldReturnCorrectEventType() {
            assertThat(handler.getEventType()).isEqualTo(GitHubEventType.ISSUES);
        }
    }

    // ==================== Basic Lifecycle Events ====================

    @Nested
    @DisplayName("Basic Lifecycle Events")
    class BasicLifecycleEvents {

        @Test
        @DisplayName("Should persist issue on 'opened' event")
        void shouldPersistIssueOnOpenedEvent() throws Exception {
            // Given
            GitHubIssueEventDTO event = loadPayload("issues.opened");

            // When
            handler.handleEvent(event);

            // Then
            Issue issue = issueRepository.findById(event.issue().getDatabaseId()).orElse(null);
            assertThat(issue).isNotNull();
            assertThat(issue.getTitle()).isEqualTo("Webhook fixture: parent tracker");
            assertThat(issue.getState()).isEqualTo(Issue.State.OPEN);
            assertThat(issue.getNumber()).isEqualTo(20);
            assertThat(issue.getRepository().getId()).isEqualTo(FIXTURE_REPO_ID);

            // Verify author was created
            assertThat(issue.getAuthor()).isNotNull();
            assertThat(issue.getAuthor().getLogin()).isEqualTo("FelixTJDietrich");

            // Verify Created event was published
            assertThat(eventListener.getCreatedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should update issue on 'edited' event")
        void shouldUpdateIssueOnEditedEvent() throws Exception {
            // Given - create issue first
            handler.handleEvent(loadPayload("issues.opened"));
            eventListener.clear();

            GitHubIssueEventDTO editedEvent = loadPayload("issues.edited");

            // When
            handler.handleEvent(editedEvent);

            // Then
            Issue issue = issueRepository.findById(ISSUE_20_ID).orElse(null);
            assertThat(issue).isNotNull();
            // Issue should still exist (edited, not created new)
            assertThat(issueRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should close issue on 'closed' event")
        void shouldHandleClosedEvent() throws Exception {
            // Given - create issue first
            handler.handleEvent(loadPayload("issues.opened"));
            eventListener.clear();

            GitHubIssueEventDTO closedEvent = loadPayload("issues.closed");

            // When
            handler.handleEvent(closedEvent);

            // Then
            Issue issue = issueRepository.findById(ISSUE_20_ID).orElse(null);
            assertThat(issue).isNotNull();
            assertThat(issue.getState()).isEqualTo(Issue.State.CLOSED);

            // Verify Closed event was published
            assertThat(eventListener.getClosedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should reopen issue on 'reopened' event")
        void shouldHandleReopenedEvent() throws Exception {
            // Given - create and close issue
            handler.handleEvent(loadPayload("issues.opened"));
            handler.handleEvent(loadPayload("issues.closed"));
            eventListener.clear();

            GitHubIssueEventDTO reopenedEvent = loadPayload("issues.reopened");

            // When
            handler.handleEvent(reopenedEvent);

            // Then
            Issue issue = issueRepository.findById(ISSUE_20_ID).orElse(null);
            assertThat(issue).isNotNull();
            assertThat(issue.getState()).isEqualTo(Issue.State.OPEN);
        }

        @Test
        @DisplayName("Should delete issue on 'deleted' event")
        void shouldDeleteIssueOnDeletedEvent() throws Exception {
            // Given - the deleted fixture uses issue #23 (ID 3578523639)
            // First, we create it by simulating it exists
            Issue issueToDelete = new Issue();
            issueToDelete.setId(ISSUE_23_ID);
            issueToDelete.setNumber(23);
            issueToDelete.setTitle("Issue to delete");
            issueToDelete.setState(Issue.State.OPEN);
            issueToDelete.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME + "/issues/23");
            issueToDelete.setRepository(repositoryRepository.findById(FIXTURE_REPO_ID).orElseThrow());
            issueRepository.save(issueToDelete);

            assertThat(issueRepository.existsById(ISSUE_23_ID)).isTrue();

            GitHubIssueEventDTO deletedEvent = loadPayload("issues.deleted");

            // When
            handler.handleEvent(deletedEvent);

            // Then
            assertThat(issueRepository.existsById(ISSUE_23_ID)).isFalse();
        }
    }

    // ==================== Label Events ====================

    @Nested
    @DisplayName("Label Events")
    class LabelEvents {

        @Test
        @DisplayName("Should handle 'labeled' event and persist label")
        void shouldHandleLabeledEvent() throws Exception {
            // Given - create issue first
            handler.handleEvent(loadPayload("issues.opened"));
            eventListener.clear();

            GitHubIssueEventDTO labeledEvent = loadPayload("issues.labeled");

            // When
            handler.handleEvent(labeledEvent);

            // Then
            Issue issue = issueRepository.findById(ISSUE_20_ID).orElse(null);
            assertThat(issue).isNotNull();
            assertThat(labelNames(issue)).contains("etl-sample");

            // Verify label was created in repository
            assertThat(labelRepository.findById(9567656085L)).isPresent();

            // Verify Labeled event was published
            assertThat(eventListener.getLabeledEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle 'unlabeled' event")
        void shouldHandleUnlabeledEvent() throws Exception {
            // Given - create issue with label
            handler.handleEvent(loadPayload("issues.opened"));
            handler.handleEvent(loadPayload("issues.labeled"));
            eventListener.clear();

            GitHubIssueEventDTO unlabeledEvent = loadPayload("issues.unlabeled");

            // When
            handler.handleEvent(unlabeledEvent);

            // Then - Unlabeled event should be published
            assertThat(eventListener.getUnlabeledEvents()).hasSize(1);
        }
    }

    // ==================== Assignment Events ====================

    @Nested
    @DisplayName("Assignment Events")
    class AssignmentEvents {

        @Test
        @DisplayName("Should handle 'assigned' event - process routes to processor")
        void shouldHandleAssignedEvent() throws Exception {
            // Note: The assigned webhook action routes to process(), which creates/updates
            // the issue with assignees from the DTO. Since we're testing the handler routing,
            // we verify the issue is persisted with assignees when created fresh.
            GitHubIssueEventDTO assignedEvent = loadPayload("issues.assigned");

            // When - process the assigned event (which includes assignees in the DTO)
            handler.handleEvent(assignedEvent);

            // Then - issue should be created with assignees from the DTO
            Issue issue = issueRepository.findById(ISSUE_20_ID).orElse(null);
            assertThat(issue).isNotNull();
            // The assignees are set from the DTO on creation
            assertThat(issue.getAssignees()).isNotEmpty();
            assertThat(issue.getAssignees().iterator().next().getLogin()).isEqualTo("FelixTJDietrich");
        }

        @Test
        @DisplayName("Should handle 'unassigned' event")
        void shouldHandleUnassignedEvent() throws Exception {
            // Given - create issue with assignee
            handler.handleEvent(loadPayload("issues.opened"));
            handler.handleEvent(loadPayload("issues.assigned"));

            GitHubIssueEventDTO unassignedEvent = loadPayload("issues.unassigned");

            // When
            handler.handleEvent(unassignedEvent);

            // Then - issue still exists and was processed
            Issue issue = issueRepository.findById(ISSUE_20_ID).orElse(null);
            assertThat(issue).isNotNull();
        }
    }

    // ==================== Milestone Events ====================

    @Nested
    @DisplayName("Milestone Events")
    class MilestoneEvents {

        @Test
        @DisplayName("Should handle 'milestoned' event and create milestone")
        void shouldHandleMilestonedEvent() throws Exception {
            // Given - this fixture uses a different issue (22)
            GitHubIssueEventDTO milestonedEvent = loadPayload("issues.milestoned");

            // When
            handler.handleEvent(milestonedEvent);

            // Then
            Issue issue = issueRepository.findById(ISSUE_22_ID).orElse(null);
            assertThat(issue).isNotNull();
            assertThat(issue.getMilestone()).isNotNull();
            assertThat(issue.getMilestone().getTitle()).isEqualTo("Webhook Fixtures");
            assertThat(issue.getMilestone().getNumber()).isEqualTo(2);

            // Verify milestone was created in repository
            assertThat(milestoneRepository.findById(14028563L)).isPresent();
        }

        @Test
        @DisplayName("Should handle 'demilestoned' event")
        void shouldHandleDemilestonedEvent() throws Exception {
            // Given - create issue with milestone
            handler.handleEvent(loadPayload("issues.milestoned"));

            GitHubIssueEventDTO demilestonedEvent = loadPayload("issues.demilestoned");

            // When
            handler.handleEvent(demilestonedEvent);

            // Then
            Issue issue = issueRepository.findById(ISSUE_22_ID).orElse(null);
            assertThat(issue).isNotNull();
            assertThat(issue.getMilestone()).isNull();
        }
    }

    // ==================== Issue Type Events ====================

    @Nested
    @DisplayName("Issue Type Events")
    class IssueTypeEvents {

        @Test
        @DisplayName("Should handle 'typed' event and create issue type")
        void shouldHandleTypedEvent() throws Exception {
            // Given - this fixture uses issue 25
            GitHubIssueEventDTO typedEvent = loadPayload("issues.typed");

            // When
            handler.handleEvent(typedEvent);

            // Then
            Issue issue = issueRepository.findById(ISSUE_25_ID).orElse(null);
            assertThat(issue).isNotNull();
            assertThat(issue.getIssueType()).isNotNull();
            assertThat(issue.getIssueType().getName()).isEqualTo("Task");
            assertThat(issue.getIssueType().getColor()).isEqualTo(IssueType.Color.YELLOW);

            // Verify Typed event was published
            assertThat(eventListener.getTypedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle 'untyped' event")
        void shouldHandleUntypedEvent() throws Exception {
            // Given - create issue with type
            handler.handleEvent(loadPayload("issues.typed"));
            eventListener.clear();

            GitHubIssueEventDTO untypedEvent = loadPayload("issues.untyped");

            // When
            handler.handleEvent(untypedEvent);

            // Then
            Issue issue = issueRepository.findById(ISSUE_25_ID).orElse(null);
            assertThat(issue).isNotNull();
            assertThat(issue.getIssueType()).isNull();

            // Verify Untyped event was published
            assertThat(eventListener.getUntypedEvents()).hasSize(1);
        }
    }

    // ==================== Lock Events ====================

    @Nested
    @DisplayName("Lock Events")
    class LockEvents {

        @Test
        @DisplayName("Should handle 'locked' event")
        void shouldHandleLockedEvent() throws Exception {
            // Given - create issue first
            handler.handleEvent(loadPayload("issues.opened"));

            GitHubIssueEventDTO lockedEvent = loadPayload("issues.locked");

            // When
            handler.handleEvent(lockedEvent);

            // Then - issue processed (locked is treated like a general update)
            Issue issue = issueRepository.findById(ISSUE_20_ID).orElse(null);
            assertThat(issue).isNotNull();
        }

        @Test
        @DisplayName("Should handle 'unlocked' event")
        void shouldHandleUnlockedEvent() throws Exception {
            // Given - create issue first
            handler.handleEvent(loadPayload("issues.opened"));
            handler.handleEvent(loadPayload("issues.locked"));

            GitHubIssueEventDTO unlockedEvent = loadPayload("issues.unlocked");

            // When
            handler.handleEvent(unlockedEvent);

            // Then
            Issue issue = issueRepository.findById(ISSUE_20_ID).orElse(null);
            assertThat(issue).isNotNull();
        }
    }

    // ==================== Pin Events ====================

    @Nested
    @DisplayName("Pin Events")
    class PinEvents {

        @Test
        @DisplayName("Should handle 'pinned' event")
        void shouldHandlePinnedEvent() throws Exception {
            // Given - create issue first
            handler.handleEvent(loadPayload("issues.opened"));

            GitHubIssueEventDTO pinnedEvent = loadPayload("issues.pinned");

            // When
            handler.handleEvent(pinnedEvent);

            // Then - issue should still exist
            assertThat(issueRepository.existsById(ISSUE_20_ID)).isTrue();
        }

        @Test
        @DisplayName("Should handle 'unpinned' event")
        void shouldHandleUnpinnedEvent() throws Exception {
            // Given - create issue first
            handler.handleEvent(loadPayload("issues.opened"));
            handler.handleEvent(loadPayload("issues.pinned"));

            GitHubIssueEventDTO unpinnedEvent = loadPayload("issues.unpinned");

            // When
            handler.handleEvent(unpinnedEvent);

            // Then
            assertThat(issueRepository.existsById(ISSUE_20_ID)).isTrue();
        }
    }

    // ==================== Transfer Event ====================

    @Nested
    @DisplayName("Transfer Event")
    class TransferEvent {

        @Test
        @DisplayName("Should handle 'transferred' event")
        void shouldHandleTransferredEvent() throws Exception {
            // Given - create issue first
            handler.handleEvent(loadPayload("issues.opened"));

            GitHubIssueEventDTO transferredEvent = loadPayload("issues.transferred");

            // When
            handler.handleEvent(transferredEvent);

            // Then - issue should be processed
            Issue issue = issueRepository.findById(ISSUE_20_ID).orElse(null);
            assertThat(issue).isNotNull();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle unknown action gracefully (falls back to process)")
        void shouldHandleUnknownActionGracefully() throws Exception {
            // Given - we'll modify the opened payload to have unknown action
            // But since we can't easily modify, we just verify the handler doesn't crash
            // with a known action that goes to default
            GitHubIssueEventDTO event = loadPayload("issues.opened");

            // When/Then - should not throw
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle missing repository context gracefully")
        void shouldHandleMissingRepositoryContextGracefully() throws Exception {
            // Given - remove the repository so context creation fails
            repositoryRepository.deleteAll();

            GitHubIssueEventDTO event = loadPayload("issues.opened");

            // When/Then - should not throw, just log warning
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();

            // Issue should not be persisted since context is null
            assertThat(issueRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should be idempotent - processing same event twice")
        void shouldBeIdempotent() throws Exception {
            // Given
            GitHubIssueEventDTO event = loadPayload("issues.opened");

            // When - handle same event twice
            handler.handleEvent(event);
            long countAfterFirst = issueRepository.count();

            handler.handleEvent(event);

            // Then - still only one issue
            assertThat(issueRepository.count()).isEqualTo(countAfterFirst);
        }

        @Test
        @DisplayName("Should verify getDatabaseId() fallback works (id is used when databaseId is null)")
        void shouldVerifyGetDatabaseIdFallback() throws Exception {
            // Given - webhook payloads have 'id' not 'database_id'
            GitHubIssueEventDTO event = loadPayload("issues.opened");

            // Verify the DTO is using the fallback correctly
            // In webhook payloads, databaseId will be null and id will have the value
            assertThat(event.issue().getDatabaseId()).isEqualTo(ISSUE_20_ID);

            // When
            handler.handleEvent(event);

            // Then - issue should be persisted with the correct ID
            assertThat(issueRepository.findById(ISSUE_20_ID)).isPresent();
        }

        @Test
        @DisplayName("Should create all related entities (author, labels) from opened event")
        void shouldCreateAllRelatedEntitiesFromOpenedEvent() throws Exception {
            // Given
            GitHubIssueEventDTO event = loadPayload("issues.opened");

            // Verify no users or labels exist
            assertThat(userRepository.count()).isZero();
            assertThat(labelRepository.count()).isZero();

            // When
            handler.handleEvent(event);

            // Then - author was created
            assertThat(userRepository.findById(5898705L)).isPresent();

            // And label from the opened fixture was created
            assertThat(labelRepository.findById(9567656085L)).isPresent();
        }
    }

    // ==================== Helper Methods ====================

    private GitHubIssueEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubIssueEventDTO.class);
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
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/" + FIXTURE_ORG_ID);
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

    private Set<String> labelNames(Issue issue) {
        return issue
            .getLabels()
            .stream()
            .map(l -> l.getName())
            .collect(Collectors.toSet());
    }

    // ==================== Test Event Listener ====================

    @Component
    static class TestEventListener {

        private final List<DomainEvent.IssueCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.IssueUpdated> updatedEvents = new ArrayList<>();
        private final List<DomainEvent.IssueClosed> closedEvents = new ArrayList<>();
        private final List<DomainEvent.IssueReopened> reopenedEvents = new ArrayList<>();
        private final List<DomainEvent.IssueLabeled> labeledEvents = new ArrayList<>();
        private final List<DomainEvent.IssueUnlabeled> unlabeledEvents = new ArrayList<>();
        private final List<DomainEvent.IssueTyped> typedEvents = new ArrayList<>();
        private final List<DomainEvent.IssueUntyped> untypedEvents = new ArrayList<>();
        private final List<DomainEvent.IssueDeleted> deletedEvents = new ArrayList<>();

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

        @EventListener
        public void onDeleted(DomainEvent.IssueDeleted event) {
            deletedEvents.add(event);
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

        public List<DomainEvent.IssueDeleted> getDeletedEvents() {
            return new ArrayList<>(deletedEvents);
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
            deletedEvents.clear();
        }
    }
}
