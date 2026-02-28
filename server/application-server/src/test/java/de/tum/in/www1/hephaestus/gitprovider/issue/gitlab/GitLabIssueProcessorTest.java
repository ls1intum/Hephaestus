package de.tum.in.www1.hephaestus.gitprovider.issue.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookLabel;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookUser;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RepositoryScopeFilter;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.gitlab.dto.GitLabIssueEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("GitLabIssueProcessor")
class GitLabIssueProcessorTest extends BaseUnitTest {

    private static final long REPO_ID = -246765L;
    private static final long RAW_ISSUE_ID = 422296L;
    private static final long ENTITY_ISSUE_ID = -422296L;
    private static final int ISSUE_IID = 5;
    private static final long RAW_USER_ID = 18024L;
    private static final long ENTITY_USER_ID = -18024L;
    private static final Long PROVIDER_ID = 2L;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private ScopeIdResolver scopeIdResolver;

    @Mock
    private RepositoryScopeFilter repositoryScopeFilter;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private GitLabIssueProcessor processor;
    private Repository testRepo;
    private GitProvider gitLabProvider;

    @BeforeEach
    void setUp() {
        GitLabProperties properties = new GitLabProperties(
            "https://gitlab.lrz.de",
            Duration.ofSeconds(30),
            Duration.ofSeconds(60),
            Duration.ofMillis(200),
            Duration.ofMinutes(5)
        );

        processor = new GitLabIssueProcessor(
            issueRepository,
            userRepository,
            labelRepository,
            repositoryRepository,
            scopeIdResolver,
            repositoryScopeFilter,
            properties,
            eventPublisher
        );

        gitLabProvider = new GitProvider();
        gitLabProvider.setId(PROVIDER_ID);
        gitLabProvider.setType(GitProviderType.GITLAB);
        gitLabProvider.setServerUrl("https://gitlab.lrz.de");

        testRepo = new Repository();
        testRepo.setId(REPO_ID);
        testRepo.setNameWithOwner("hephaestustest/demo-repository");
        testRepo.setProvider(gitLabProvider);

        // Default: upsertCore succeeds
        lenient()
            .when(
                issueRepository.upsertCore(
                    anyLong(),
                    anyLong(),
                    anyInt(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(boolean.class),
                    any(),
                    anyInt(),
                    any(),
                    any(),
                    any(),
                    any(),
                    anyLong(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            )
            .thenReturn(1);

        // upsertUser is void — no stubbing needed
    }

    // ========================================================================
    // Confidential Filtering
    // ========================================================================

    @Nested
    @DisplayName("Confidential filtering")
    class ConfidentialFiltering {

        @Test
        @DisplayName("process() skips confidential issue")
        void processSkipsConfidential() {
            GitLabIssueEventDTO event = createEvent("open", "opened", true);
            ProcessingContext ctx = createContext();

            Issue result = processor.process(event, ctx);

            assertThat(result).isNull();
            verify(issueRepository, never()).upsertCore(
                anyLong(),
                anyLong(),
                anyInt(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(boolean.class),
                any(),
                anyInt(),
                any(),
                any(),
                any(),
                any(),
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("processFromSync() skips confidential issue")
        void processFromSyncSkipsConfidential() {
            var syncData = new GitLabIssueProcessor.SyncIssueData(
                "gid://gitlab/Issue/422296",
                "5",
                "Title",
                "desc",
                "opened",
                true,
                "https://example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null
            );
            Issue result = processor.processFromSync(syncData, testRepo);

            assertThat(result).isNull();
            verify(issueRepository, never()).upsertCore(
                anyLong(),
                anyLong(),
                anyInt(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(boolean.class),
                any(),
                anyInt(),
                any(),
                any(),
                any(),
                any(),
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }
    }

    // ========================================================================
    // State Mapping
    // ========================================================================

    @Nested
    @DisplayName("State mapping")
    class StateMapping {

        @Test
        @DisplayName("opened state maps to OPEN")
        void openedMapsToOpen() {
            Issue issue = createIssueEntity();
            when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, ISSUE_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(issue));

            GitLabIssueEventDTO event = createEvent("open", "opened", false);
            processor.process(event, createContext());

            verify(issueRepository).upsertCore(
                eq(RAW_ISSUE_ID),
                eq(PROVIDER_ID),
                eq(ISSUE_IID),
                any(),
                any(),
                eq("OPEN"),
                any(),
                any(),
                any(boolean.class),
                any(),
                anyInt(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("closed state maps to CLOSED")
        void closedMapsToClosed() {
            Issue issue = createIssueEntity();
            when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, ISSUE_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(issue));

            GitLabIssueEventDTO event = createEvent("close", "closed", false);
            processor.process(event, createContext());

            verify(issueRepository).upsertCore(
                eq(RAW_ISSUE_ID),
                eq(PROVIDER_ID),
                eq(ISSUE_IID),
                any(),
                any(),
                eq("CLOSED"),
                any(),
                any(),
                any(boolean.class),
                any(),
                anyInt(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("null state defaults to OPEN")
        void nullStateDefaultsToOpen() {
            Issue issue = createIssueEntity();
            when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, ISSUE_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(issue));

            GitLabIssueEventDTO event = createEvent("open", null, false);
            processor.process(event, createContext());

            verify(issueRepository).upsertCore(
                eq(RAW_ISSUE_ID),
                eq(PROVIDER_ID),
                eq(ISSUE_IID),
                any(),
                any(),
                eq("OPEN"),
                any(),
                any(),
                any(boolean.class),
                any(),
                anyInt(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }
    }

    // ========================================================================
    // Webhook Event Processing
    // ========================================================================

    @Nested
    @DisplayName("Webhook event processing")
    class WebhookProcessing {

        @Test
        @DisplayName("process() creates new issue and publishes IssueCreated")
        void processCreatesNewIssue() {
            Issue issue = createIssueEntity();
            // First call: check if exists → empty (new issue)
            // Second call: after upsert → find the issue
            when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, ISSUE_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(issue));

            User author = createUserEntity();
            when(userRepository.findByNativeIdAndProviderId(RAW_USER_ID, PROVIDER_ID)).thenReturn(Optional.of(author));

            GitLabIssueEventDTO event = createEvent("open", "opened", false);
            Issue result = processor.process(event, createContext());

            assertThat(result).isNotNull();
            assertThat(result.getProvider()).isEqualTo(gitLabProvider);

            ArgumentCaptor<DomainEvent.IssueCreated> eventCaptor = ArgumentCaptor.forClass(
                DomainEvent.IssueCreated.class
            );
            verify(eventPublisher).publishEvent(eventCaptor.capture());
        }

        @Test
        @DisplayName("process() with missing id and iid skips processing")
        void processMissingIdSkips() {
            var attrs = new GitLabIssueEventDTO.ObjectAttributes(
                null,
                null,
                "Title",
                "desc",
                "opened",
                "open",
                false,
                18024L,
                null,
                null,
                null,
                null,
                null
            );
            GitLabIssueEventDTO event = new GitLabIssueEventDTO(
                "issue",
                "issue",
                createUser(),
                createProject(),
                attrs,
                null,
                null
            );

            Issue result = processor.process(event, createContext());

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("processClosed() publishes IssueClosed event")
        void processClosedPublishesEvent() {
            Issue issue = createIssueEntity();
            when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, ISSUE_IID))
                .thenReturn(Optional.of(issue))
                .thenReturn(Optional.of(issue));

            User author = createUserEntity();
            when(userRepository.findByNativeIdAndProviderId(RAW_USER_ID, PROVIDER_ID)).thenReturn(Optional.of(author));

            GitLabIssueEventDTO event = createEvent("close", "closed", false);
            Issue result = processor.processClosed(event, createContext());

            assertThat(result).isNotNull();

            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            // processClosed calls process() which may publish IssueCreated, then publishes IssueClosed
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isInstanceOf(DomainEvent.IssueClosed.class);
        }

        @Test
        @DisplayName("processReopened() publishes IssueReopened event")
        void processReopenedPublishesEvent() {
            Issue issue = createIssueEntity();
            when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, ISSUE_IID))
                .thenReturn(Optional.of(issue))
                .thenReturn(Optional.of(issue));

            User author = createUserEntity();
            when(userRepository.findByNativeIdAndProviderId(RAW_USER_ID, PROVIDER_ID)).thenReturn(Optional.of(author));

            GitLabIssueEventDTO event = createEvent("reopen", "opened", false);
            Issue result = processor.processReopened(event, createContext());

            assertThat(result).isNotNull();

            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isInstanceOf(DomainEvent.IssueReopened.class);
        }
    }

    // ========================================================================
    // GraphQL Sync Processing
    // ========================================================================

    @Nested
    @DisplayName("GraphQL sync processing")
    class SyncProcessing {

        @Test
        @DisplayName("processFromSync() creates issue with negated ID")
        void processFromSyncCreatesIssue() {
            Issue issue = createIssueEntity();
            when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, ISSUE_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(issue));

            User author = createUserEntity();
            when(userRepository.findByNativeIdAndProviderId(RAW_USER_ID, PROVIDER_ID)).thenReturn(Optional.of(author));

            var syncData = new GitLabIssueProcessor.SyncIssueData(
                "gid://gitlab/Issue/422296",
                "5",
                "Feature: Add user authentication",
                "Implement OAuth2 authentication flow",
                "opened",
                false,
                "https://gitlab.lrz.de/hephaestustest/demo-repository/-/issues/5",
                "2026-01-31T18:03:35Z",
                "2026-01-31T18:03:35Z",
                null,
                "gid://gitlab/User/18024",
                "ga84xah",
                "Felix Dietrich",
                "https://gitlab.lrz.de/uploads/avatar.png",
                "https://gitlab.lrz.de/ga84xah",
                0,
                null,
                null
            );
            Issue result = processor.processFromSync(syncData, testRepo);

            assertThat(result).isNotNull();
            assertThat(result.getProvider()).isEqualTo(gitLabProvider);

            verify(issueRepository).upsertCore(
                eq(RAW_ISSUE_ID),
                eq(PROVIDER_ID),
                eq(ISSUE_IID),
                any(),
                any(),
                eq("OPEN"),
                any(),
                any(),
                any(boolean.class),
                any(),
                anyInt(),
                any(),
                any(),
                any(),
                any(),
                eq(REPO_ID),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("processFromSync() with invalid globalId skips processing")
        void processFromSyncInvalidGlobalId() {
            var syncData = new GitLabIssueProcessor.SyncIssueData(
                "invalid-id",
                "5",
                "Title",
                null,
                "opened",
                false,
                "https://example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null
            );
            Issue result = processor.processFromSync(syncData, testRepo);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("processFromSync() with invalid iid skips processing")
        void processFromSyncInvalidIid() {
            var syncData = new GitLabIssueProcessor.SyncIssueData(
                "gid://gitlab/Issue/422296",
                "not-a-number",
                "Title",
                null,
                "opened",
                false,
                "https://example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null
            );
            Issue result = processor.processFromSync(syncData, testRepo);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("processFromSync() publishes IssueCreated for new issue")
        void processFromSyncPublishesCreatedEvent() {
            Issue issue = createIssueEntity();
            when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, ISSUE_IID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(issue));

            var syncData = new GitLabIssueProcessor.SyncIssueData(
                "gid://gitlab/Issue/422296",
                "5",
                "Title",
                null,
                "opened",
                false,
                "https://example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null
            );
            processor.processFromSync(syncData, testRepo);

            ArgumentCaptor<DomainEvent.IssueCreated> eventCaptor = ArgumentCaptor.forClass(
                DomainEvent.IssueCreated.class
            );
            verify(eventPublisher).publishEvent(eventCaptor.capture());
        }

        @Test
        @DisplayName("processFromSync() does not publish event for existing issue")
        void processFromSyncNoEventForExisting() {
            Issue issue = createIssueEntity();
            // Issue already exists
            when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, ISSUE_IID))
                .thenReturn(Optional.of(issue))
                .thenReturn(Optional.of(issue));

            var syncData = new GitLabIssueProcessor.SyncIssueData(
                "gid://gitlab/Issue/422296",
                "5",
                "Title",
                null,
                "opened",
                false,
                "https://example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null
            );
            processor.processFromSync(syncData, testRepo);

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private ProcessingContext createContext() {
        return ProcessingContext.forWebhook(1L, testRepo, "open");
    }

    private Issue createIssueEntity() {
        Issue issue = new Issue();
        issue.setId(ENTITY_ISSUE_ID);
        issue.setNumber(ISSUE_IID);
        issue.setTitle("Feature: Add user authentication");
        issue.setState(Issue.State.OPEN);
        issue.setRepository(testRepo);
        issue.setLabels(new HashSet<>());
        issue.setAssignees(new HashSet<>());
        return issue;
    }

    private User createUserEntity() {
        User user = new User();
        user.setId(ENTITY_USER_ID);
        user.setLogin("ga84xah");
        user.setName("Felix Dietrich");
        return user;
    }

    private GitLabIssueEventDTO createEvent(String action, String state, boolean confidential) {
        var attrs = new GitLabIssueEventDTO.ObjectAttributes(
            RAW_ISSUE_ID,
            ISSUE_IID,
            "Feature: Add user authentication",
            "Implement OAuth2 authentication flow",
            state,
            action,
            confidential,
            RAW_USER_ID,
            null,
            "2026-01-31 19:03:35 +0100",
            "2026-01-31 19:03:35 +0100",
            null,
            "https://gitlab.lrz.de/hephaestustest/demo-repository/-/issues/5"
        );
        return new GitLabIssueEventDTO(
            "issue",
            confidential ? "confidential_issue" : "issue",
            createUser(),
            createProject(),
            attrs,
            List.of(new GitLabWebhookLabel(85907L, "enhancement", "#a2eeef")),
            null
        );
    }

    private GitLabWebhookUser createUser() {
        return new GitLabWebhookUser(
            RAW_USER_ID,
            "ga84xah",
            "Felix Dietrich",
            "https://gitlab.lrz.de/uploads/-/system/user/avatar/18024/avatar.png",
            null
        );
    }

    private de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookProject createProject() {
        return new de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookProject(
            246765L,
            "demo-repository",
            "https://gitlab.lrz.de/hephaestustest/demo-repository",
            "hephaestustest/demo-repository"
        );
    }
}
