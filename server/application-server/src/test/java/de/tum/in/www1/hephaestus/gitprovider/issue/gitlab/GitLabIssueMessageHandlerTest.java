package de.tum.in.www1.hephaestus.gitprovider.issue.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookLabel;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookProject;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookUser;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RepositoryScopeFilter;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.issue.gitlab.dto.GitLabIssueEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.nats.client.Message;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("GitLabIssueMessageHandler")
class GitLabIssueMessageHandlerTest extends BaseUnitTest {

    private static final String PROJECT_PATH = "hephaestustest/demo-repository";
    private static final String NATS_SUBJECT = "gitlab.hephaestustest.demo-repository.issue";

    @Mock
    private GitLabIssueProcessor issueProcessor;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private RepositoryScopeFilter repositoryScopeFilter;

    @Mock
    private ScopeIdResolver scopeIdResolver;

    @Mock
    private NatsMessageDeserializer deserializer;

    private TransactionTemplate transactionTemplate;
    private GitLabIssueMessageHandler handler;

    @BeforeEach
    void setUp() {
        transactionTemplate = mock(TransactionTemplate.class);
        lenient()
            .doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<TransactionStatus> callback = invocation.getArgument(0);
                callback.accept(null);
                return null;
            })
            .when(transactionTemplate)
            .executeWithoutResult(any());

        handler = new GitLabIssueMessageHandler(
            issueProcessor,
            repositoryRepository,
            repositoryScopeFilter,
            scopeIdResolver,
            deserializer,
            transactionTemplate
        );

        // Default: allow all repositories
        lenient().when(repositoryScopeFilter.isRepositoryAllowed(any())).thenReturn(true);
    }

    @Test
    @DisplayName("returns ISSUE event type")
    void getEventType_returnsIssue() {
        assertThat(handler.getEventType()).isEqualTo(GitLabEventType.ISSUE);
    }

    // ========================================================================
    // Action Routing
    // ========================================================================

    @Nested
    @DisplayName("Action routing")
    class ActionRouting {

        @Test
        @DisplayName("open action routes to process()")
        void openAction_routesToProcess() throws IOException {
            GitLabIssueEventDTO event = createEvent("open", "opened", false);
            Repository repo = setupRepository();

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueProcessor).process(eq(event), any(ProcessingContext.class));
            verify(issueProcessor, never()).processClosed(any(), any());
            verify(issueProcessor, never()).processReopened(any(), any());
        }

        @Test
        @DisplayName("update action routes to process()")
        void updateAction_routesToProcess() throws IOException {
            GitLabIssueEventDTO event = createEvent("update", "closed", false);
            Repository repo = setupRepository();

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueProcessor).process(eq(event), any(ProcessingContext.class));
        }

        @Test
        @DisplayName("close action routes to processClosed()")
        void closeAction_routesToProcessClosed() throws IOException {
            GitLabIssueEventDTO event = createEvent("close", "closed", false);
            Repository repo = setupRepository();

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueProcessor).processClosed(eq(event), any(ProcessingContext.class));
            verify(issueProcessor, never()).process(any(), any());
        }

        @Test
        @DisplayName("reopen action routes to processReopened()")
        void reopenAction_routesToProcessReopened() throws IOException {
            GitLabIssueEventDTO event = createEvent("reopen", "opened", false);
            Repository repo = setupRepository();

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueProcessor).processReopened(eq(event), any(ProcessingContext.class));
            verify(issueProcessor, never()).process(any(), any());
        }

        @Test
        @DisplayName("unknown action skips processing")
        void unknownAction_skipsProcessing() throws IOException {
            GitLabIssueEventDTO event = createEvent("unknown_action", "opened", false);
            Repository repo = setupRepository();

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueProcessor, never()).process(any(), any());
            verify(issueProcessor, never()).processClosed(any(), any());
            verify(issueProcessor, never()).processReopened(any(), any());
        }
    }

    // ========================================================================
    // Confidential Issue Handling
    // ========================================================================

    @Nested
    @DisplayName("Confidential issues")
    class ConfidentialIssues {

        @Test
        @DisplayName("confidential issue event is skipped")
        void confidentialIssue_skipsProcessing() throws IOException {
            GitLabIssueEventDTO event = createEvent("open", "opened", true);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueProcessor, never()).process(any(), any());
            verify(repositoryRepository, never()).findByNameWithOwnerWithOrganization(any());
        }

        @Test
        @DisplayName("confidential_issue event_type is skipped")
        void confidentialIssueEventType_skipsProcessing() throws IOException {
            var attrs = new GitLabIssueEventDTO.ObjectAttributes(
                422297L,
                6,
                "Security issue",
                "desc",
                "opened",
                "open",
                true,
                18024L,
                null,
                "2026-01-31 19:03:35 +0100",
                "2026-01-31 19:03:35 +0100",
                null,
                "https://gitlab.lrz.de/hephaestustest/demo-repository/-/issues/6"
            );
            GitLabIssueEventDTO event = new GitLabIssueEventDTO(
                "issue",
                "confidential_issue",
                createUser(),
                createProject(),
                attrs,
                null,
                null
            );

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueProcessor, never()).process(any(), any());
        }
    }

    // ========================================================================
    // Validation
    // ========================================================================

    @Nested
    @DisplayName("Payload validation")
    class PayloadValidation {

        @Test
        @DisplayName("missing object_attributes skips processing")
        void missingObjectAttributes_skipsProcessing() throws IOException {
            GitLabIssueEventDTO event = new GitLabIssueEventDTO(
                "issue",
                "issue",
                createUser(),
                createProject(),
                null,
                null,
                null
            );

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueProcessor, never()).process(any(), any());
        }

        @Test
        @DisplayName("missing project skips processing")
        void missingProject_skipsProcessing() throws IOException {
            var attrs = new GitLabIssueEventDTO.ObjectAttributes(
                422296L,
                5,
                "Title",
                "desc",
                "opened",
                "open",
                false,
                18024L,
                null,
                "2026-01-31 19:03:35 +0100",
                "2026-01-31 19:03:35 +0100",
                null,
                "https://example.com"
            );
            GitLabIssueEventDTO event = new GitLabIssueEventDTO(
                "issue",
                "issue",
                createUser(),
                null,
                attrs,
                null,
                null
            );

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueProcessor, never()).process(any(), any());
        }

        @Test
        @DisplayName("non-issue subject is rejected by base class")
        void nonIssueSubject_rejected() throws IOException {
            Message msg = mock(Message.class);
            when(msg.getSubject()).thenReturn("gitlab.org.proj.merge_request");

            handler.onMessage(msg);

            verify(deserializer, never()).deserialize(any(), any());
            verify(issueProcessor, never()).process(any(), any());
        }
    }

    // ========================================================================
    // Context Resolution
    // ========================================================================

    @Nested
    @DisplayName("Context resolution")
    class ContextResolution {

        @Test
        @DisplayName("skips when repository is filtered")
        void repositoryFiltered_skipsProcessing() throws IOException {
            when(repositoryScopeFilter.isRepositoryAllowed(PROJECT_PATH)).thenReturn(false);
            GitLabIssueEventDTO event = createEvent("open", "opened", false);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueProcessor, never()).process(any(), any());
            verify(repositoryRepository, never()).findByNameWithOwnerWithOrganization(any());
        }

        @Test
        @DisplayName("skips when repository not found in DB")
        void repositoryNotFound_skipsProcessing() throws IOException {
            when(repositoryRepository.findByNameWithOwnerWithOrganization(PROJECT_PATH)).thenReturn(Optional.empty());
            GitLabIssueEventDTO event = createEvent("open", "opened", false);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueProcessor, never()).process(any(), any());
        }

        @Test
        @DisplayName("resolves scope from organization")
        void resolvesScopeFromOrganization() throws IOException {
            Organization org = new Organization();
            org.setLogin("hephaestustest");

            Repository repo = new Repository();
            repo.setId(-246765L);
            repo.setNameWithOwner(PROJECT_PATH);
            repo.setOrganization(org);

            when(repositoryRepository.findByNameWithOwnerWithOrganization(PROJECT_PATH)).thenReturn(Optional.of(repo));
            when(scopeIdResolver.findScopeIdByOrgLogin("hephaestustest")).thenReturn(Optional.of(42L));

            GitLabIssueEventDTO event = createEvent("open", "opened", false);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            ArgumentCaptor<ProcessingContext> ctxCaptor = ArgumentCaptor.forClass(ProcessingContext.class);
            verify(issueProcessor).process(eq(event), ctxCaptor.capture());

            ProcessingContext ctx = ctxCaptor.getValue();
            assertThat(ctx.scopeId()).isEqualTo(42L);
            assertThat(ctx.repository()).isSameAs(repo);
        }

        @Test
        @DisplayName("falls back to repo name for scope when org not found")
        void fallsBackToRepoNameForScope() throws IOException {
            Repository repo = new Repository();
            repo.setId(-246765L);
            repo.setNameWithOwner(PROJECT_PATH);
            // No organization

            when(repositoryRepository.findByNameWithOwnerWithOrganization(PROJECT_PATH)).thenReturn(Optional.of(repo));
            when(scopeIdResolver.findScopeIdByRepositoryName(PROJECT_PATH)).thenReturn(Optional.of(99L));

            GitLabIssueEventDTO event = createEvent("open", "opened", false);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            ArgumentCaptor<ProcessingContext> ctxCaptor = ArgumentCaptor.forClass(ProcessingContext.class);
            verify(issueProcessor).process(eq(event), ctxCaptor.capture());

            assertThat(ctxCaptor.getValue().scopeId()).isEqualTo(99L);
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Repository setupRepository() {
        Repository repo = new Repository();
        repo.setId(-246765L);
        repo.setNameWithOwner(PROJECT_PATH);

        when(repositoryRepository.findByNameWithOwnerWithOrganization(PROJECT_PATH)).thenReturn(Optional.of(repo));
        when(scopeIdResolver.findScopeIdByRepositoryName(PROJECT_PATH)).thenReturn(Optional.of(1L));

        return repo;
    }

    private GitLabIssueEventDTO createEvent(String action, String state, boolean confidential) {
        var attrs = new GitLabIssueEventDTO.ObjectAttributes(
            422296L,
            5,
            "Feature: Add user authentication",
            "Implement OAuth2 authentication flow",
            state,
            action,
            confidential,
            18024L,
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
            18024L,
            "ga84xah",
            "Felix Dietrich",
            "https://gitlab.lrz.de/uploads/-/system/user/avatar/18024/avatar.png",
            null
        );
    }

    private GitLabWebhookProject createProject() {
        return new GitLabWebhookProject(
            246765L,
            "demo-repository",
            "https://gitlab.lrz.de/hephaestustest/demo-repository",
            PROJECT_PATH
        );
    }

    private Message mockMessage(GitLabIssueEventDTO event) throws IOException {
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn(NATS_SUBJECT);
        when(deserializer.deserialize(msg, GitLabIssueEventDTO.class)).thenReturn(event);
        return msg;
    }
}
