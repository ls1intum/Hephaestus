package de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab;

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
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookContextResolver;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookProject;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookUser;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab.dto.GitLabNoteEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab.dto.GitLabNoteEventDTO.EmbeddedIssue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab.dto.GitLabNoteEventDTO.EmbeddedMergeRequest;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab.dto.GitLabNoteEventDTO.NoteAttributes;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.gitlab.GitLabDiffNoteWebhookProcessor;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.nats.client.Message;
import java.io.IOException;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("unit")
@DisplayName("GitLabNoteMessageHandler")
class GitLabNoteMessageHandlerTest extends BaseUnitTest {

    private static final String PROJECT_PATH = "hephaestustest/demo-repository";
    private static final String NATS_SUBJECT = "gitlab.hephaestustest.demo-repository.note";

    @Mock
    private GitLabIssueCommentProcessor issueCommentProcessor;

    @Mock
    private GitLabDiffNoteWebhookProcessor diffNoteProcessor;

    @Mock
    private GitLabWebhookContextResolver contextResolver;

    @Mock
    private NatsMessageDeserializer deserializer;

    private TransactionTemplate transactionTemplate;
    private GitLabNoteMessageHandler handler;

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

        handler = new GitLabNoteMessageHandler(
            issueCommentProcessor,
            diffNoteProcessor,
            contextResolver,
            deserializer,
            transactionTemplate
        );

        lenient()
            .when(contextResolver.resolve(eq(PROJECT_PATH), any(), any()))
            .thenReturn(ProcessingContext.forWebhook(1L, setupRepository(), "create"));
    }

    @Test
    @DisplayName("returns NOTE event type")
    void getEventType_returnsNote() {
        assertThat(handler.getEventType()).isEqualTo(GitLabEventType.NOTE);
    }

    @Nested
    @DisplayName("Note routing")
    class NoteRouting {

        @Test
        @DisplayName("issue note routes to processIssueNote()")
        void issueNote_routesToProcessIssueNote() throws IOException {
            GitLabNoteEventDTO event = createIssueNoteEvent("create", false, false, false);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor).processIssueNote(eq(event), any(ProcessingContext.class));
            verify(issueCommentProcessor, never()).processMergeRequestNote(any(), any());
        }

        @Test
        @DisplayName("MR general note (no position) routes to processMergeRequestNote()")
        void mrGeneralNote_routesToProcessMergeRequestNote() throws IOException {
            GitLabNoteEventDTO event = createMergeRequestNoteEvent("create", false, false, null);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor).processMergeRequestNote(eq(event), any(ProcessingContext.class));
            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
        }

        @Test
        @DisplayName("MR diff note (with position) is skipped")
        void mrDiffNote_isSkipped() throws IOException {
            // position is non-null => diff note => deferred
            GitLabNoteEventDTO event = createMergeRequestNoteEvent("create", false, false, new Object());

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
            verify(issueCommentProcessor, never()).processMergeRequestNote(any(), any());
        }

        @Test
        @DisplayName("commit note is skipped")
        void commitNote_isSkipped() throws IOException {
            GitLabNoteEventDTO event = createCommitNoteEvent();

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
            verify(issueCommentProcessor, never()).processMergeRequestNote(any(), any());
        }
    }

    @Nested
    @DisplayName("Note filtering")
    class NoteFiltering {

        @Test
        @DisplayName("system note is skipped")
        void systemNote_isSkipped() throws IOException {
            GitLabNoteEventDTO event = createIssueNoteEvent("create", true, false, false);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
            verify(contextResolver, never()).resolve(any(), any(), any());
        }

        @Test
        @DisplayName("internal/confidential note is skipped")
        void internalNote_isSkipped() throws IOException {
            GitLabNoteEventDTO event = createIssueNoteEvent("create", false, true, false);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
            verify(contextResolver, never()).resolve(any(), any(), any());
        }

        @Test
        @DisplayName("note on confidential issue is skipped")
        void noteOnConfidentialIssue_isSkipped() throws IOException {
            GitLabNoteEventDTO event = createIssueNoteEvent("create", false, false, true);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
            verify(contextResolver, never()).resolve(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Payload validation")
    class PayloadValidation {

        @Test
        @DisplayName("missing object_attributes skips processing")
        void missingObjectAttributes_skipsProcessing() throws IOException {
            GitLabNoteEventDTO event = new GitLabNoteEventDTO(
                "note",
                "note",
                createUser(),
                createProject(),
                null,
                null,
                null
            );

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
        }

        @Test
        @DisplayName("missing project skips processing")
        void missingProject_skipsProcessing() throws IOException {
            NoteAttributes attrs = createNoteAttributes("Issue", "create", false, false, null);
            GitLabNoteEventDTO event = new GitLabNoteEventDTO(
                "note",
                "note",
                createUser(),
                null,
                attrs,
                createEmbeddedIssue(false),
                null
            );

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
        }

        @Test
        @DisplayName("non-note subject is rejected by base class")
        void nonNoteSubject_rejected() throws IOException {
            Message msg = mock(Message.class);
            when(msg.getSubject()).thenReturn("gitlab.org.proj.issue");

            handler.onMessage(msg);

            verify(deserializer, never()).deserialize(any(), any());
            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
        }
    }

    @Nested
    @DisplayName("Context resolution")
    class ContextResolution {

        @Test
        @DisplayName("skips when context resolver returns null")
        void contextResolverReturnsNull_skipsProcessing() throws IOException {
            when(contextResolver.resolve(eq(PROJECT_PATH), any(), any())).thenReturn(null);
            GitLabNoteEventDTO event = createIssueNoteEvent("create", false, false, false);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
        }
    }

    private Repository setupRepository() {
        Repository repo = new Repository();
        repo.setId(-246765L);
        repo.setNameWithOwner(PROJECT_PATH);
        return repo;
    }

    private GitLabNoteEventDTO createIssueNoteEvent(
        String action,
        boolean system,
        boolean internal,
        boolean confidentialIssue
    ) {
        NoteAttributes attrs = createNoteAttributes("Issue", action, system, internal, null);
        return new GitLabNoteEventDTO(
            "note",
            internal ? "confidential_note" : "note",
            createUser(),
            createProject(),
            attrs,
            createEmbeddedIssue(confidentialIssue),
            null
        );
    }

    private GitLabNoteEventDTO createMergeRequestNoteEvent(
        String action,
        boolean system,
        boolean internal,
        Object position
    ) {
        NoteAttributes attrs = createNoteAttributes("MergeRequest", action, system, internal, position);
        return new GitLabNoteEventDTO(
            "note",
            "note",
            createUser(),
            createProject(),
            attrs,
            null,
            createEmbeddedMergeRequest()
        );
    }

    private GitLabNoteEventDTO createCommitNoteEvent() {
        NoteAttributes attrs = createNoteAttributes("Commit", "create", false, false, null);
        return new GitLabNoteEventDTO("note", "note", createUser(), createProject(), attrs, null, null);
    }

    private NoteAttributes createNoteAttributes(
        String noteableType,
        String action,
        boolean system,
        boolean internal,
        Object position
    ) {
        return new NoteAttributes(
            4406174L,
            "Test note body",
            noteableType,
            system,
            internal,
            position,
            action,
            "https://gitlab.lrz.de/hephaestustest/demo-repository/-/issues/5#note_4406174",
            "2026-01-31 19:03:37 +0100",
            "2026-01-31 19:03:37 +0100"
        );
    }

    private EmbeddedIssue createEmbeddedIssue(boolean confidential) {
        return new EmbeddedIssue(
            422296L,
            5,
            "Feature: Add user authentication",
            "Implement OAuth2 authentication flow",
            "opened",
            confidential,
            "https://gitlab.lrz.de/hephaestustest/demo-repository/-/issues/5",
            "2026-01-31 19:03:35 +0100",
            "2026-01-31 19:03:35 +0100"
        );
    }

    private EmbeddedMergeRequest createEmbeddedMergeRequest() {
        return new EmbeddedMergeRequest(
            334047L,
            2,
            "Implement OAuth authentication",
            "This MR implements OAuth2 authentication.\n\nCloses #5",
            "opened",
            false,
            "feature/oauth",
            "main",
            "https://gitlab.lrz.de/hephaestustest/demo-repository/-/merge_requests/2",
            "2026-01-31 19:03:54 +0100",
            "2026-01-31 19:03:56 +0100"
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

    private Message mockMessage(GitLabNoteEventDTO event) throws IOException {
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn(NATS_SUBJECT);
        when(deserializer.deserialize(msg, GitLabNoteEventDTO.class)).thenReturn(event);
        return msg;
    }
}
