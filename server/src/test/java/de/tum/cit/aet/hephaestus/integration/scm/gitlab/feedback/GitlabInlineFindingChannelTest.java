package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.FindingAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.FindingAnchor.DiffAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel.InlineFinding;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel.InlineResult;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.MrInfo;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

class GitlabInlineFindingChannelTest extends BaseUnitTest {

    @Mock
    private GitLabGraphQlClientProvider gitLabProvider;

    @Mock
    private GitlabMrResolver mrResolver;

    private GitlabInlineFindingChannel channel;

    @BeforeEach
    void setUp() {
        channel = new GitlabInlineFindingChannel(gitLabProvider, mrResolver);
    }

    @Test
    void emptyFindings() {
        assertThat(channel.postInlineFindings(gitlabTarget(), List.of())).isEqualTo(new InlineResult(0, 0));
    }

    @Test
    void rateLimitCriticalShortCircuits() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(true);
        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix", "marker"))
        );
        assertThat(result.posted()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void missingDiffRefsSkips() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        // diffRefs absent → headSha/startSha null
        when(mrResolver.resolve(1L, "group/project", 42)).thenReturn(
            new MrInfo("gid://gitlab/MR/42", null, null, null)
        );

        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix", "marker"))
        );
        assertThat(result.posted()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void postsDiffNotesSuccessfully() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42)).thenReturn(
            new MrInfo("gid://gitlab/MR/42", "base", "head", "start")
        );

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        // postInlineFindings is now pure-append (stale-note clearing moved to clearStaleFindings), so the only
        // GraphQL call is CreateDiffNote — success (no errors).
        ClientGraphQlResponse createResponse = mock(ClientGraphQlResponse.class);
        ClientResponseField errorsField = mock(ClientResponseField.class);
        when(createResponse.field("createDiffNote.errors")).thenReturn(errorsField);
        when(errorsField.getValue()).thenReturn(List.of());

        when(spec.execute()).thenReturn(Mono.just(createResponse));

        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix-this", "marker"))
        );

        assertThat(result.posted()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    @Test
    void clearStalePreservesHumanRepliedThreads() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);

        // Discussion A: a single marker-bearing bot note → safe to delete.
        // Discussion B: a bot note PLUS a human reply (no marker) → must be preserved.
        Map<String, Object> botNoteA = Map.of("id", "gid://Note/A", "body", "Issue here MARKER", "system", false);
        Map<String, Object> discA = Map.of("notes", Map.of("nodes", List.of(botNoteA)));
        Map<String, Object> botNoteB = Map.of("id", "gid://Note/B", "body", "Another issue MARKER", "system", false);
        Map<String, Object> humanReply = Map.of("id", "gid://Note/H", "body", "Thanks, fixed it!", "system", false);
        Map<String, Object> discB = Map.of("notes", Map.of("nodes", List.of(botNoteB, humanReply)));

        HttpGraphQlClient.RequestSpec discussionsSpec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName("GetMergeRequestDiscussions")).thenReturn(discussionsSpec);
        when(discussionsSpec.variable(any(), any())).thenReturn(discussionsSpec);
        ClientGraphQlResponse discussionsResponse = mock(ClientGraphQlResponse.class);
        ClientResponseField nodesField = mock(ClientResponseField.class);
        when(discussionsResponse.field("project.mergeRequest.discussions.nodes")).thenReturn(nodesField);
        when(nodesField.getValue()).thenReturn(List.of(discA, discB));
        when(discussionsSpec.execute()).thenReturn(Mono.just(discussionsResponse));

        HttpGraphQlClient.RequestSpec destroySpec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName("DestroyNote")).thenReturn(destroySpec);
        when(destroySpec.variable(eq("noteId"), any())).thenReturn(destroySpec);
        ClientGraphQlResponse destroyResponse = mock(ClientGraphQlResponse.class);
        ClientResponseField destroyErrors = mock(ClientResponseField.class);
        when(destroyResponse.field("destroyNote.errors")).thenReturn(destroyErrors);
        when(destroyErrors.getValue()).thenReturn(List.of());
        when(destroySpec.execute()).thenReturn(Mono.just(destroyResponse));

        channel.clearStaleFindings(gitlabTarget(), "MARKER");

        // The pure-bot thread is deleted; the human-replied thread is left intact.
        verify(destroySpec).variable("noteId", "gid://Note/A");
        verify(destroySpec, never()).variable("noteId", "gid://Note/B");
    }

    private static FeedbackTarget gitlabTarget() {
        return new FeedbackTarget(new IntegrationRef(IntegrationKind.GITLAB, 1L, null), "group/project!42", null);
    }
}
