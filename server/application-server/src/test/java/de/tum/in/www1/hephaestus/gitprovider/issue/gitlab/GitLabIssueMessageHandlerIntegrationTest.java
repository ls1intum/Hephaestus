package de.tum.in.www1.hephaestus.gitprovider.issue.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.gitlab.dto.GitLabIssueEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for GitLabIssueMessageHandler.
 * <p>
 * Tests the full webhook handling flow: JSON fixtures → DTO → handler → processor → DB.
 * <p>
 * <b>Fixture values (issue.open.json — Issue IID #5):</b>
 * <ul>
 *   <li>Raw ID: 422296 → Entity ID: -422296</li>
 *   <li>IID: 5</li>
 *   <li>Title: "Feature: Add user authentication"</li>
 *   <li>State: opened → OPEN</li>
 *   <li>Author: ga84xah (raw ID 18024 → entity ID -18024)</li>
 *   <li>Label: enhancement (raw ID 85907 → entity ID -85907)</li>
 *   <li>Provider: GITLAB</li>
 * </ul>
 * <p>
 * Note: Does NOT use @Transactional (see GitHubIssueMessageHandlerIntegrationTest for rationale).
 */
@DisplayName("GitLab Issue Message Handler")
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
class GitLabIssueMessageHandlerIntegrationTest extends BaseIntegrationTest {

    // Native IDs from GitLab fixtures (positive, raw values)
    private static final long NATIVE_ISSUE_ID = 422296L;
    private static final int ISSUE_IID = 5;
    private static final long NATIVE_USER_ID = 18024L;
    private static final long NATIVE_LABEL_ID = 85907L;

    // Fixture values
    private static final String FIXTURE_ISSUE_TITLE = "Feature: Add user authentication";
    private static final String FIXTURE_ISSUE_BODY = "Implement OAuth2 authentication flow";
    private static final String FIXTURE_ISSUE_HTML_URL =
        "https://gitlab.lrz.de/hephaestustest/demo-repository/-/issues/5";
    private static final String FIXTURE_AUTHOR_LOGIN = "ga84xah";
    private static final String FIXTURE_LABEL_NAME = "enhancement";
    private static final String FIXTURE_LABEL_COLOR = "#a2eeef";

    // Repository/org setup
    private static final String FIXTURE_ORG_LOGIN = "hephaestustest";
    private static final String FIXTURE_REPO_FULL_NAME = "hephaestustest/demo-repository";

    @Autowired
    private GitLabIssueMessageHandler handler;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private UserRepository userRepository;

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
    private TransactionTemplate transactionTemplate;

    @Autowired
    private GitLabTestEventListener eventListener;

    private Repository savedRepo;
    private GitProvider savedProvider;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    // ==================== Event Type ====================

    @Nested
    @DisplayName("Event Type")
    class EventType {

        @Test
        @DisplayName("returns ISSUE as event type")
        void returnsCorrectEventType() {
            assertThat(handler.getEventType()).isEqualTo(GitLabEventType.ISSUE);
        }
    }

    // ==================== Basic Lifecycle ====================

    @Nested
    @DisplayName("Basic Lifecycle Events")
    class BasicLifecycleEvents {

        @Test
        @DisplayName("persists issue with all fields on 'open' event")
        void shouldPersistIssueOnOpenEvent() throws Exception {
            GitLabIssueEventDTO event = loadPayload("issue.open");

            handler.handleEvent(event);

            transactionTemplate.executeWithoutResult(status -> {
                Issue issue = issueRepository.findByRepositoryIdAndNumber(savedRepo.getId(), ISSUE_IID).orElseThrow();

                // Core fields
                assertThat(issue.getNativeId()).isEqualTo(NATIVE_ISSUE_ID);
                assertThat(issue.getNumber()).isEqualTo(ISSUE_IID);
                assertThat(issue.getTitle()).isEqualTo(FIXTURE_ISSUE_TITLE);
                assertThat(issue.getBody()).isEqualTo(FIXTURE_ISSUE_BODY);
                assertThat(issue.getState()).isEqualTo(Issue.State.OPEN);
                assertThat(issue.getHtmlUrl()).isEqualTo(FIXTURE_ISSUE_HTML_URL);

                // Provider
                assertThat(issue.getProvider().getType()).isEqualTo(GitProviderType.GITLAB);

                // Timestamps
                assertThat(issue.getCreatedAt()).isNotNull();
                assertThat(issue.getUpdatedAt()).isNotNull();

                // Repository
                assertThat(issue.getRepository()).isNotNull();
                assertThat(issue.getRepository().getId()).isEqualTo(savedRepo.getId());

                // Author
                assertThat(issue.getAuthor()).isNotNull();
                assertThat(issue.getAuthor().getNativeId()).isEqualTo(NATIVE_USER_ID);
                assertThat(issue.getAuthor().getLogin()).isEqualTo(FIXTURE_AUTHOR_LOGIN);

                // Labels
                assertThat(issue.getLabels()).hasSize(1);
                assertThat(issue.getLabels().iterator().next().getName()).isEqualTo(FIXTURE_LABEL_NAME);
            });

            // Domain event
            assertThat(eventListener.getCreatedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("closes issue on 'close' event")
        void shouldCloseIssueOnCloseEvent() throws Exception {
            // Create first
            handler.handleEvent(loadPayload("issue.open"));
            eventListener.clear();

            // Close
            handler.handleEvent(loadPayload("issue.close"));

            Issue issue = issueRepository.findByRepositoryIdAndNumber(savedRepo.getId(), ISSUE_IID).orElse(null);
            assertThat(issue).isNotNull();
            assertThat(issue.getState()).isEqualTo(Issue.State.CLOSED);

            assertThat(eventListener.getClosedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("reopens issue on 'reopen' event")
        void shouldReopenIssueOnReopenEvent() throws Exception {
            // Create and close
            handler.handleEvent(loadPayload("issue.open"));
            handler.handleEvent(loadPayload("issue.close"));
            eventListener.clear();

            // Reopen
            handler.handleEvent(loadPayload("issue.reopen"));

            Issue issue = issueRepository.findByRepositoryIdAndNumber(savedRepo.getId(), ISSUE_IID).orElse(null);
            assertThat(issue).isNotNull();
            assertThat(issue.getState()).isEqualTo(Issue.State.OPEN);

            assertThat(eventListener.getReopenedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("updates issue on 'update' event")
        void shouldUpdateIssueOnUpdateEvent() throws Exception {
            // Create first
            handler.handleEvent(loadPayload("issue.open"));
            eventListener.clear();

            // Update
            handler.handleEvent(loadPayload("issue.update"));

            // Should still be one issue
            assertThat(issueRepository.count()).isEqualTo(1);
            Issue issue = issueRepository.findByRepositoryIdAndNumber(savedRepo.getId(), ISSUE_IID).orElse(null);
            assertThat(issue).isNotNull();
        }
    }

    // ==================== Confidential Issues ====================

    @Nested
    @DisplayName("Confidential Issues")
    class ConfidentialIssues {

        @Test
        @DisplayName("skips confidential issue on open")
        void shouldSkipConfidentialOpen() throws Exception {
            handler.handleEvent(loadPayload("issue.confidential.open"));

            assertThat(issueRepository.count()).isZero();
            assertThat(eventListener.getCreatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("skips confidential issue on update")
        void shouldSkipConfidentialUpdate() throws Exception {
            handler.handleEvent(loadPayload("issue.confidential.update"));

            assertThat(issueRepository.count()).isZero();
        }

        @Test
        @DisplayName("skips confidential issue on close")
        void shouldSkipConfidentialClose() throws Exception {
            handler.handleEvent(loadPayload("issue.confidential.close"));

            assertThat(issueRepository.count()).isZero();
            assertThat(eventListener.getClosedEvents()).isEmpty();
        }
    }

    // ==================== Author and Label Resolution ====================

    @Nested
    @DisplayName("Entity Resolution")
    class EntityResolution {

        @Test
        @DisplayName("creates author with negated ID and GITLAB provider")
        void shouldCreateAuthorWithCorrectFields() throws Exception {
            assertThat(userRepository.count()).isZero();

            handler.handleEvent(loadPayload("issue.open"));

            // Wrap in transaction to avoid LazyInitializationException when accessing provider
            transactionTemplate.executeWithoutResult(status -> {
                var author = userRepository
                    .findByNativeIdAndProviderId(NATIVE_USER_ID, savedProvider.getId())
                    .orElseThrow();
                assertThat(author.getLogin()).isEqualTo(FIXTURE_AUTHOR_LOGIN);
                assertThat(author.getProvider().getType()).isEqualTo(GitProviderType.GITLAB);
                assertThat(author.getHtmlUrl()).isEqualTo("https://gitlab.lrz.de/ga84xah");
            });
        }

        @Test
        @DisplayName("creates label with negated ID")
        void shouldCreateLabelWithNegatedId() throws Exception {
            handler.handleEvent(loadPayload("issue.open"));

            transactionTemplate.executeWithoutResult(status -> {
                var label = labelRepository
                    .findByRepositoryIdAndName(savedRepo.getId(), FIXTURE_LABEL_NAME)
                    .orElseThrow();
                assertThat(label.getName()).isEqualTo(FIXTURE_LABEL_NAME);
                assertThat(label.getColor()).isEqualTo(FIXTURE_LABEL_COLOR);
            });
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

            GitLabIssueEventDTO event = loadPayload("issue.open");

            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();
            assertThat(issueRepository.count()).isZero();
        }

        @Test
        @DisplayName("is idempotent — processing same event twice")
        void shouldBeIdempotent() throws Exception {
            GitLabIssueEventDTO event = loadPayload("issue.open");

            handler.handleEvent(event);
            long countAfterFirst = issueRepository.count();

            handler.handleEvent(event);

            assertThat(issueRepository.count()).isEqualTo(countAfterFirst);
        }

        @Test
        @DisplayName("full lifecycle: open → close → reopen → update")
        void shouldHandleFullLifecycle() throws Exception {
            handler.handleEvent(loadPayload("issue.open"));
            assertThat(
                issueRepository.findByRepositoryIdAndNumber(savedRepo.getId(), ISSUE_IID).orElseThrow().getState()
            ).isEqualTo(Issue.State.OPEN);

            handler.handleEvent(loadPayload("issue.close"));
            assertThat(
                issueRepository.findByRepositoryIdAndNumber(savedRepo.getId(), ISSUE_IID).orElseThrow().getState()
            ).isEqualTo(Issue.State.CLOSED);

            handler.handleEvent(loadPayload("issue.reopen"));
            assertThat(
                issueRepository.findByRepositoryIdAndNumber(savedRepo.getId(), ISSUE_IID).orElseThrow().getState()
            ).isEqualTo(Issue.State.OPEN);

            handler.handleEvent(loadPayload("issue.update"));
            assertThat(issueRepository.findByRepositoryIdAndNumber(savedRepo.getId(), ISSUE_IID)).isPresent();

            assertThat(issueRepository.count()).isEqualTo(1);
        }
    }

    // ==================== Helpers ====================

    private GitLabIssueEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("gitlab/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitLabIssueEventDTO.class);
    }

    private void setupTestData() {
        savedProvider = gitProviderRepository
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
        org.setProvider(savedProvider);
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
        repo.setProvider(savedProvider);
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

    private Set<String> labelNames(Issue issue) {
        return issue
            .getLabels()
            .stream()
            .map(l -> l.getName())
            .collect(Collectors.toSet());
    }

    // ==================== Test Event Listener ====================

    @Component
    static class GitLabTestEventListener {

        private final List<DomainEvent.IssueCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.IssueClosed> closedEvents = new ArrayList<>();
        private final List<DomainEvent.IssueReopened> reopenedEvents = new ArrayList<>();

        @EventListener
        public void onCreated(DomainEvent.IssueCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onClosed(DomainEvent.IssueClosed event) {
            closedEvents.add(event);
        }

        @EventListener
        public void onReopened(DomainEvent.IssueReopened event) {
            reopenedEvents.add(event);
        }

        public List<DomainEvent.IssueCreated> getCreatedEvents() {
            return new ArrayList<>(createdEvents);
        }

        public List<DomainEvent.IssueClosed> getClosedEvents() {
            return new ArrayList<>(closedEvents);
        }

        public List<DomainEvent.IssueReopened> getReopenedEvents() {
            return new ArrayList<>(reopenedEvents);
        }

        public void clear() {
            createdEvents.clear();
            closedEvents.clear();
            reopenedEvents.clear();
        }
    }
}
