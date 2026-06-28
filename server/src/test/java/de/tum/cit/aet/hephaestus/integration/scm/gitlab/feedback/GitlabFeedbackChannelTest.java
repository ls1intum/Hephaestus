package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.FeedbackContent;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.SummaryHandle;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.MrInfo;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

class GitlabFeedbackChannelTest extends BaseUnitTest {

    @Mock
    private GitLabGraphQlClientProvider gitLabProvider;

    @Mock
    private GitlabMrResolver mrResolver;

    private GitlabFeedbackChannel channel;

    @BeforeEach
    void setUp() {
        channel = new GitlabFeedbackChannel(gitLabProvider, mrResolver);
    }

    @Test
    void postSummaryReturnsNoteId() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42)).thenReturn(
            new MrInfo("gid://gitlab/MR/42", "base", "head", "start")
        );

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mockGitlabResponse("gid://gitlab/Note/789");
        when(spec.execute()).thenReturn(Mono.just(response));

        SummaryHandle handle = channel.postSummary(target, new FeedbackContent("hello", "marker"));

        assertThat(handle).isNotNull();
        assertThat(handle.externalId()).isEqualTo("gid://gitlab/Note/789");
    }

    @Test
    void postSummaryEscapesSlashCommands() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42)).thenReturn(
            new MrInfo("gid://gitlab/MR/42", "base", "head", "start")
        );

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mockGitlabResponse("gid://gitlab/Note/789");
        when(spec.execute()).thenReturn(Mono.just(response));

        channel.postSummary(target, new FeedbackContent("/approve please\nsome body", "marker"));

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(spec).variable(eq("body"), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue().toString()).contains("`/approve`");
    }

    @Test
    void updateSummaryEscapesSlashCommands() {
        // The edit path must escape just like the create path: a leading slash command in the new body
        // reaches updateNote's `body` variable backtick-wrapped, so an in-place edit can't smuggle an action.
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ClientResponseField idField = mock(ClientResponseField.class);
        when(response.field("updateNote.note.id")).thenReturn(idField);
        when(idField.getValue()).thenReturn("gid://gitlab/Note/1");
        ClientResponseField errorsField = mock(ClientResponseField.class);
        lenient().when(response.field("updateNote.errors")).thenReturn(errorsField);
        lenient().when(errorsField.getValue()).thenReturn(List.of());
        when(response.getErrors()).thenReturn(List.of());
        when(spec.execute()).thenReturn(Mono.just(response));

        channel.updateSummary(target, "gid://gitlab/Note/1", new FeedbackContent("/merge now\nrest", "marker"));

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(spec).variable(eq("body"), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue().toString()).contains("`/merge`");
    }

    @Test
    void escapeSlashCommands_leavesMidLineCommandsUntouched() {
        // The ^...MULTILINE anchoring means only a LINE-START slash command is an action; a mid-line
        // "/approve" is ordinary text and must be left alone. This pins the anchoring against a regex edit.
        assertThat(GitlabFeedbackChannel.escapeSlashCommands("Please ask them to /approve it")).isEqualTo(
            "Please ask them to /approve it"
        );
        // A multi-line body escapes each line-start command independently.
        assertThat(GitlabFeedbackChannel.escapeSlashCommands("/approve\n/merge")).isEqualTo("`/approve`\n`/merge`");
    }

    @Test
    void slashCommandPattern_matchesTheCanonicalPullRequestPosterLiteral() {
        // GitlabFeedbackChannel.GITLAB_SLASH_COMMAND is a deliberate duplicate of
        // PullRequestCommentPoster.GITLAB_SLASH_COMMAND (which is private). Pin the channel's copy against the
        // canonical literal so editing one copy without the other fails CI rather than silently degrading the
        // belt-and-suspenders guarantee.
        String canonical =
            "^(\\s*/(?:approve|merge|close|reopen|assign|unassign|label|unlabel|lock|unlock|" +
            "milestone|estimate|spend|award|subscribe|unsubscribe|todo|done|wip|draft|ready|" +
            "due|remove_due_date|weight|epic|copy_metadata|move|confidential|shrug|tableflip)\\b)";
        assertThat(GitlabFeedbackChannel.GITLAB_SLASH_COMMAND.pattern()).isEqualTo(canonical);
    }

    @Test
    void throwsOnRateLimit() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(true);
        assertThatThrownBy(() -> channel.postSummary(target, new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("rate limit critical");
    }

    @Test
    void postSummaryWrapsTransportErrorAsFeedbackDeliveryException() {
        // A transport/timeout RuntimeException from .block() must surface as the channel's typed exception
        // (consistent with updateSummary) so PullRequestCommentPoster's catch(FeedbackDeliveryException)
        // wraps it uniformly instead of a raw RuntimeException bypassing that wrap.
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42)).thenReturn(
            new MrInfo("gid://gitlab/MR/42", "base", "head", "start")
        );

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);
        when(spec.execute()).thenReturn(Mono.error(new RuntimeException("connection reset")));

        assertThatThrownBy(() -> channel.postSummary(target, new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("createNote transport error");
    }

    @Test
    void throwsOnMutationErrors() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42)).thenReturn(
            new MrInfo("gid://gitlab/MR/42", "base", "head", "start")
        );

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ClientResponseField errorsField = mock(ClientResponseField.class);
        when(response.field("createNote.errors")).thenReturn(errorsField);
        when(errorsField.getValue()).thenReturn(List.of("not allowed"));
        when(spec.execute()).thenReturn(Mono.just(response));

        assertThatThrownBy(() -> channel.postSummary(target, new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("createNote failed");
    }

    @Test
    void postSummaryRoutesIssueSubjectToIssueGid() {
        // An issue subject ("path#iid") resolves the issue gid (not the MR path) and posts via createNote.
        FeedbackTarget issueTarget = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITLAB, 1L, null),
            "group/project#7",
            null
        );
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolveIssueGid(1L, "group/project", 7)).thenReturn("gid://gitlab/Issue/7");

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);
        ClientGraphQlResponse response = mockGitlabResponse("gid://gitlab/Note/555");
        when(spec.execute()).thenReturn(Mono.just(response));

        SummaryHandle handle = channel.postSummary(issueTarget, new FeedbackContent("hi", "marker"));

        assertThat(handle.externalId()).isEqualTo("gid://gitlab/Note/555");
        verify(mrResolver).resolveIssueGid(1L, "group/project", 7);
        verify(spec).variable(eq("noteableId"), eq("gid://gitlab/Issue/7"));
    }

    @Test
    void updateSummaryEditsNoteInPlace() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ClientResponseField idField = mock(ClientResponseField.class);
        when(response.field("updateNote.note.id")).thenReturn(idField);
        when(idField.getValue()).thenReturn("gid://gitlab/Note/789");
        ClientResponseField errorsField = mock(ClientResponseField.class);
        lenient().when(response.field("updateNote.errors")).thenReturn(errorsField);
        lenient().when(errorsField.getValue()).thenReturn(List.of());
        when(response.getErrors()).thenReturn(List.of());
        when(spec.execute()).thenReturn(Mono.just(response));

        FeedbackChannel.UpdateOutcome outcome = channel.updateSummary(
            target,
            "gid://gitlab/Note/789",
            new FeedbackContent("updated body", "marker")
        );

        assertThat(outcome.kind()).isEqualTo(FeedbackChannel.UpdateOutcome.Kind.EDITED);
        assertThat(outcome.handle().externalId()).isEqualTo("gid://gitlab/Note/789");
        // No MR/issue resolution — the note id addresses the comment directly.
        verify(spec).variable(eq("id"), eq("gid://gitlab/Note/789"));
    }

    @Test
    void updateSummaryReturnsGoneOnNotFoundError() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        stubMutationErrors(List.of("note not found"));

        FeedbackChannel.UpdateOutcome outcome = channel.updateSummary(
            target,
            "gid://gitlab/Note/gone",
            new FeedbackContent("body", "marker")
        );

        // A deleted note → GONE so the caller posts fresh (NOT a throw, NOT a transient double-post).
        assertThat(outcome.kind()).isEqualTo(FeedbackChannel.UpdateOutcome.Kind.GONE);
    }

    @Test
    void updateSummaryReturnsTransientOnGenericError() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        stubMutationErrors(List.of("something went wrong"));

        FeedbackChannel.UpdateOutcome outcome = channel.updateSummary(
            target,
            "gid://gitlab/Note/1",
            new FeedbackContent("body", "marker")
        );

        // An unknown vendor error → TRANSIENT: keep the prior summary, do NOT re-post (no double-post).
        assertThat(outcome.kind()).isEqualTo(FeedbackChannel.UpdateOutcome.Kind.TRANSIENT);
    }

    @Test
    void updateSummaryReturnsTransientOnRateLimitCritical() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(true);

        FeedbackChannel.UpdateOutcome outcome = channel.updateSummary(
            target,
            "gid://gitlab/Note/1",
            new FeedbackContent("body", "marker")
        );

        assertThat(outcome.kind()).isEqualTo(FeedbackChannel.UpdateOutcome.Kind.TRANSIENT);
    }

    @Test
    void updateSummaryThrowsOnBlankExternalId() {
        FeedbackTarget target = gitlabTarget();
        // A blank id is a data bug, not recoverable — still a hard error (checked before any rate-limit/transport).
        assertThatThrownBy(() -> channel.updateSummary(target, "  ", new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("external note id is missing");
    }

    private void stubMutationErrors(List<String> errors) {
        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        when(response.getErrors()).thenReturn(List.of());
        ClientResponseField errorsField = mock(ClientResponseField.class);
        when(response.field("updateNote.errors")).thenReturn(errorsField);
        when(errorsField.getValue()).thenReturn(errors);
        when(spec.execute()).thenReturn(Mono.just(response));
    }

    /**
     * A deleted note has no {@code updateNote} payload at all — GitLab reports it as a TOP-LEVEL GraphQL error
     * (the global id resolves to nothing). This is the orphaned-summary case observed live after a mirror
     * re-import; it MUST classify as GONE so the caller re-posts a fresh summary rather than silently dropping it.
     */
    @Test
    void updateSummaryReturnsGoneOnTopLevelNotFoundError() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ResponseError notFound = mock(ResponseError.class);
        when(notFound.getMessage()).thenReturn(
            "The resource that you are attempting to access does not exist or you don't have permission to perform this action"
        );
        when(response.getErrors()).thenReturn(List.of(notFound));
        when(spec.execute()).thenReturn(Mono.just(response));

        FeedbackChannel.UpdateOutcome outcome = channel.updateSummary(
            target,
            "gid://gitlab/Note/4825166",
            new FeedbackContent("body", "marker")
        );

        assertThat(outcome.kind()).isEqualTo(FeedbackChannel.UpdateOutcome.Kind.GONE);
    }

    private static FeedbackTarget gitlabTarget() {
        return new FeedbackTarget(new IntegrationRef(IntegrationKind.GITLAB, 1L, null), "group/project!42", null);
    }

    @SuppressWarnings("unchecked")
    private ClientGraphQlResponse mockGitlabResponse(String noteId) {
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        lenient().when(response.getErrors()).thenReturn(List.of());
        ClientResponseField idField = mock(ClientResponseField.class);
        when(response.field("createNote.note.id")).thenReturn(idField);
        when(idField.getValue()).thenReturn(noteId);
        ClientResponseField errorsField = mock(ClientResponseField.class);
        lenient().when(response.field("createNote.errors")).thenReturn(errorsField);
        lenient().when(errorsField.getValue()).thenReturn(List.of());
        return response;
    }

    @Test
    void postSummarySurfacesTopLevelError() {
        // A read-only GitLab instance returns NO createNote payload, only a top-level GraphQL error. The
        // channel must surface that reason — not a generic "No note ID" — so the failure is diagnosable.
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42)).thenReturn(
            new MrInfo("gid://gitlab/MR/42", "base", "head", "start")
        );

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ResponseError readOnly = mock(ResponseError.class);
        when(readOnly.getMessage()).thenReturn("You cannot perform write operations on a read-only instance");
        when(response.getErrors()).thenReturn(List.of(readOnly));
        when(spec.execute()).thenReturn(Mono.just(response));

        assertThatThrownBy(() -> channel.postSummary(target, new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("read-only instance");
    }
}
