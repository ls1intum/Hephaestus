package de.tum.cit.aet.hephaestus.integration.scm.gitlab.issuecomment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabEventType;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabWebhookContextResolver;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.dto.GitLabWebhookProject;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.dto.GitLabWebhookUser;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.issuecomment.dto.GitLabNoteEventDTO;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.issuecomment.dto.GitLabNoteEventDTO.EmbeddedIssue;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.issuecomment.dto.GitLabNoteEventDTO.EmbeddedMergeRequest;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.issuecomment.dto.GitLabNoteEventDTO.NoteAttributes;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.pullrequest.GitLabMergeRequestProcessor;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.pullrequestreviewcomment.GitLabDiffNoteWebhookProcessor;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.nats.client.Message;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("unit")
class GitLabNoteMessageHandlerTest extends BaseUnitTest {

    private static final String PROJECT_PATH = "hephaestustest/demo-repository";
    private static final String NATS_SUBJECT = "gitlab.hephaestustest.demo-repository.note";

    @Mock
    private GitLabIssueCommentProcessor issueCommentProcessor;

    @Mock
    private GitLabDiffNoteWebhookProcessor diffNoteProcessor;

    @Mock
    private GitLabMergeRequestProcessor mergeRequestProcessor;

    @Mock
    private GitLabWebhookContextResolver contextResolver;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NatsMessageDeserializer deserializer;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
            mergeRequestProcessor,
            contextResolver,
            pullRequestRepository,
            userRepository,
            deserializer,
            transactionTemplate,
            eventPublisher
        );

        lenient()
            .when(contextResolver.resolve(eq(PROJECT_PATH), any(), any()))
            .thenReturn(ProcessingContext.forWebhook(1L, setupRepository(), "create"));
    }

    @Test
    void key_returnsNote() {
        assertThat(handler.key().eventType()).isEqualTo("note");
    }

    @Nested
    class NoteRouting {

        @Test
        void issueNote_routesToProcessIssueNote() throws IOException {
            GitLabNoteEventDTO event = createIssueNoteEvent("create", false, false, false);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor).processIssueNote(eq(event), any(ProcessingContext.class));
            verify(issueCommentProcessor, never()).processMergeRequestNote(any(), any());
        }

        @Test
        void mrGeneralNote_routesToProcessMergeRequestNote() throws IOException {
            GitLabNoteEventDTO event = createMergeRequestNoteEvent("create", false, false, null);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor).processMergeRequestNote(eq(event), any(ProcessingContext.class));
            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
        }

        @Test
        void mrDiffNote_isSkipped() throws IOException {
            // position is non-null => diff note => deferred
            GitLabNoteEventDTO event = createMergeRequestNoteEvent("create", false, false, new Object());

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
            verify(issueCommentProcessor, never()).processMergeRequestNote(any(), any());
        }

        @Test
        void commitNote_isSkipped() throws IOException {
            GitLabNoteEventDTO event = createCommitNoteEvent();

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
            verify(issueCommentProcessor, never()).processMergeRequestNote(any(), any());
        }
    }

    @Nested
    class NoteFiltering {

        @Test
        void systemNote_isSkipped() throws IOException {
            GitLabNoteEventDTO event = createIssueNoteEvent("create", true, false, false);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
            verify(contextResolver, never()).resolve(any(), any(), any());
        }

        @Test
        void internalNote_isSkipped() throws IOException {
            GitLabNoteEventDTO event = createIssueNoteEvent("create", false, true, false);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
            verify(contextResolver, never()).resolve(any(), any(), any());
        }

        @Test
        void noteOnConfidentialIssue_isSkipped() throws IOException {
            GitLabNoteEventDTO event = createIssueNoteEvent("create", false, false, true);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
            verify(contextResolver, never()).resolve(any(), any(), any());
        }
    }

    @Nested
    class PayloadValidation {

        @Test
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
        void nonNoteSubject_rejected() throws IOException {
            Message msg = mock(Message.class);
            when(msg.getSubject()).thenReturn("gitlab.org.proj.issue");

            handler.onMessage(msg);

            verify(deserializer, never()).deserialize(any(), any());
            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
        }
    }

    @Nested
    class ContextResolution {

        @Test
        void contextResolverReturnsNull_skipsProcessing() throws IOException {
            when(contextResolver.resolve(eq(PROJECT_PATH), any(), any())).thenReturn(null);
            GitLabNoteEventDTO event = createIssueNoteEvent("create", false, false, false);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(issueCommentProcessor, never()).processIssueNote(any(), any());
        }
    }

    @Nested
    class RequestChangesDetection {

        @Test
        void detectedRequestedChanges_delegatesToProcessor() throws IOException {
            var mr = createEmbeddedMergeRequestWithStatus("requested_changes");
            NoteAttributes attrs = createNoteAttributes("MergeRequest", "create", false, false, null);
            var event = new GitLabNoteEventDTO("note", "note", createUser(), createProject(), attrs, null, mr);

            PullRequest pr = new PullRequest();
            pr.setId(100L);
            pr.setNativeId(334047L);
            User prAuthor = new User();
            prAuthor.setId(999L);
            prAuthor.setNativeId(99999L);
            pr.setAuthor(prAuthor);

            User reviewer = new User();
            reviewer.setId(2L);
            reviewer.setNativeId(18024L);

            when(pullRequestRepository.findByRepositoryIdAndNumber(-246765L, 2)).thenReturn(Optional.of(pr));
            when(userRepository.findByNativeIdAndProviderId(eq(18024L), any())).thenReturn(Optional.of(reviewer));

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(mergeRequestProcessor).processRequestedChangesFromNote(eq(pr), eq(reviewer), any());
        }

        @Test
        void mergeableStatus_skipsDetection() throws IOException {
            var mr = createEmbeddedMergeRequestWithStatus("mergeable");
            NoteAttributes attrs = createNoteAttributes("MergeRequest", "create", false, false, null);
            var event = new GitLabNoteEventDTO("note", "note", createUser(), createProject(), attrs, null, mr);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(mergeRequestProcessor, never()).processRequestedChangesFromNote(any(), any(), any());
        }

        @Test
        void nullStatus_skipsDetection() throws IOException {
            var mr = createEmbeddedMergeRequestWithStatus(null);
            NoteAttributes attrs = createNoteAttributes("MergeRequest", "create", false, false, null);
            var event = new GitLabNoteEventDTO("note", "note", createUser(), createProject(), attrs, null, mr);

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(mergeRequestProcessor, never()).processRequestedChangesFromNote(any(), any(), any());
        }

        @Test
        void selfReview_skipsDetection() throws IOException {
            var mr = createEmbeddedMergeRequestWithStatus("requested_changes");
            NoteAttributes attrs = createNoteAttributes("MergeRequest", "create", false, false, null);
            // User ID 18024 matches the PR author's nativeId
            var event = new GitLabNoteEventDTO("note", "note", createUser(), createProject(), attrs, null, mr);

            PullRequest pr = new PullRequest();
            pr.setId(100L);
            pr.setNativeId(334047L);
            User prAuthor = new User();
            prAuthor.setId(2L);
            prAuthor.setNativeId(18024L); // Same as event user
            pr.setAuthor(prAuthor);

            when(pullRequestRepository.findByRepositoryIdAndNumber(-246765L, 2)).thenReturn(Optional.of(pr));

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(mergeRequestProcessor, never()).processRequestedChangesFromNote(any(), any(), any());
        }

        @Test
        void prNotFound_skipsDetection() throws IOException {
            var mr = createEmbeddedMergeRequestWithStatus("requested_changes");
            NoteAttributes attrs = createNoteAttributes("MergeRequest", "create", false, false, null);
            var event = new GitLabNoteEventDTO("note", "note", createUser(), createProject(), attrs, null, mr);

            when(pullRequestRepository.findByRepositoryIdAndNumber(-246765L, 2)).thenReturn(Optional.empty());

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(mergeRequestProcessor, never()).processRequestedChangesFromNote(any(), any(), any());
        }

        @Test
        void reviewerNotFound_skipsDetection() throws IOException {
            var mr = createEmbeddedMergeRequestWithStatus("requested_changes");
            NoteAttributes attrs = createNoteAttributes("MergeRequest", "create", false, false, null);
            var event = new GitLabNoteEventDTO("note", "note", createUser(), createProject(), attrs, null, mr);

            PullRequest pr = new PullRequest();
            pr.setId(100L);
            pr.setNativeId(334047L);
            User prAuthor = new User();
            prAuthor.setId(999L);
            prAuthor.setNativeId(99999L);
            pr.setAuthor(prAuthor);

            when(pullRequestRepository.findByRepositoryIdAndNumber(-246765L, 2)).thenReturn(Optional.of(pr));
            when(userRepository.findByNativeIdAndProviderId(eq(18024L), any())).thenReturn(Optional.empty());

            Message msg = mockMessage(event);
            handler.onMessage(msg);

            verify(mergeRequestProcessor, never()).processRequestedChangesFromNote(any(), any(), any());
        }

        private EmbeddedMergeRequest createEmbeddedMergeRequestWithStatus(String detailedMergeStatus) {
            return new EmbeddedMergeRequest(
                334047L,
                2,
                "Test MR",
                "Description",
                "opened",
                false,
                "feature/test",
                "main",
                "https://gitlab.lrz.de/test/-/merge_requests/2",
                "2026-01-31 19:03:54 +0100",
                "2026-01-31 19:03:56 +0100",
                detailedMergeStatus
            );
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
            "2026-01-31 19:03:37 +0100",
            "abc123def456",
            null
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
            "2026-01-31 19:03:56 +0100",
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

    private Message mockMessage(GitLabNoteEventDTO event) throws IOException {
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn(NATS_SUBJECT);
        when(deserializer.deserialize(msg, GitLabNoteEventDTO.class)).thenReturn(event);
        return msg;
    }
}
