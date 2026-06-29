package de.tum.cit.aet.hephaestus.integration.scm.github.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.FeedbackContent;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.SummaryHandle;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

class GithubFeedbackChannelTest extends BaseUnitTest {

    @Mock
    private GitHubGraphQlClientProvider gitHubProvider;

    @Mock
    private GithubPrNodeIdResolver prNodeIdResolver;

    private GithubFeedbackChannel channel;

    @BeforeEach
    void setUp() {
        channel = new GithubFeedbackChannel(gitHubProvider, prNodeIdResolver);
    }

    @Test
    void postSummaryReturnsCommentNodeId() {
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/repo#42",
            null
        );

        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(prNodeIdResolver.resolve(1L, "owner", "repo", 42)).thenReturn("PR_node123");

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitHubProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mockGraphQlResponse("addComment.commentEdge.node.id", "IC_comment456");
        when(spec.execute()).thenReturn(Mono.just(response));

        SummaryHandle handle = channel.postSummary(target, new FeedbackContent("body", "marker"));

        assertThat(handle).isNotNull();
        assertThat(handle.externalId()).isEqualTo("IC_comment456");
    }

    @Test
    void postSummaryThrowsOnRateLimit() {
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/repo#42",
            null
        );
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(true);

        assertThatThrownBy(() -> channel.postSummary(target, new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("rate limit critical");
    }

    @Test
    void postSummaryThrowsOnMalformedSubjectId() {
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner-repo-without-hash",
            null
        );
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);

        assertThatThrownBy(() -> channel.postSummary(target, new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("Invalid GitHub PR subjectExternalId");
    }

    @Test
    void postSummaryThrowsOnMutationErrors() {
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/repo#42",
            null
        );

        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(prNodeIdResolver.resolve(1L, "owner", "repo", 42)).thenReturn("PR_node123");

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitHubProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse errorResponse = mock(ClientGraphQlResponse.class);
        when(errorResponse.getErrors()).thenReturn(List.of(mock(org.springframework.graphql.ResponseError.class)));
        when(spec.execute()).thenReturn(Mono.just(errorResponse));

        assertThatThrownBy(() -> channel.postSummary(target, new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("addComment failed");
    }

    @Test
    void updateSummary_editsInPlace_returnsEdited() {
        // GitHub re-review must edit the persistent summary in place (updateIssueComment) instead of
        // posting a duplicate. A successful mutation returns EDITED carrying the comment node id.
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/repo#42",
            null
        );
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitHubProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);
        ClientGraphQlResponse response = mockGraphQlResponse("updateIssueComment.issueComment.id", "IC_edited");
        when(spec.execute()).thenReturn(Mono.just(response));

        var outcome = channel.updateSummary(target, "IC_prior", new FeedbackContent("new body", "marker"));

        assertThat(outcome.kind()).isEqualTo(
            de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.UpdateOutcome.Kind.EDITED
        );
        assertThat(outcome.handle().externalId()).isEqualTo("IC_edited");
        verify(spec).variable("id", "IC_prior");
    }

    @Test
    void updateSummary_blankExternalId_throws() {
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/repo#42",
            null
        );
        assertThatThrownBy(() -> channel.updateSummary(target, "  ", new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("external comment id is missing");
    }

    @Test
    void updateSummary_rateLimitCritical_isTransient_notRepost() {
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/repo#42",
            null
        );
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(true);

        var outcome = channel.updateSummary(target, "IC_prior", new FeedbackContent("body", "marker"));

        assertThat(outcome.kind()).isEqualTo(
            de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.UpdateOutcome.Kind.TRANSIENT
        );
    }

    @Test
    void updateSummary_deletedComment_isGone_soCallerReposts() {
        // A human-deleted comment surfaces as a NOT_FOUND top-level error → GONE (re-post), not TRANSIENT.
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/repo#42",
            null
        );
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitHubProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse errorResponse = mock(ClientGraphQlResponse.class);
        org.springframework.graphql.ResponseError err = mock(org.springframework.graphql.ResponseError.class);
        when(err.getMessage()).thenReturn("Could not resolve to a node with the global id of 'IC_prior'");
        when(errorResponse.getErrors()).thenReturn(List.of(err));
        when(spec.execute()).thenReturn(Mono.just(errorResponse));

        var outcome = channel.updateSummary(target, "IC_prior", new FeedbackContent("body", "marker"));

        assertThat(outcome.kind()).isEqualTo(
            de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.UpdateOutcome.Kind.GONE
        );
    }

    @Test
    void postSummary_nullResponse_throwsNullResponse() {
        // createComment must fail loudly on a null mutation response (partial-delivery guard), not return a
        // SummaryHandle with a null id that the ledger would later try to edit.
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/repo#42",
            null
        );
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(prNodeIdResolver.resolve(1L, "owner", "repo", 42)).thenReturn("PR_node123");

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitHubProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);
        when(spec.execute()).thenReturn(Mono.empty());

        assertThatThrownBy(() -> channel.postSummary(target, new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("Null response from AddPullRequestComment");
    }

    @Test
    void postSummary_nullCommentId_throwsNoCommentId() {
        // A mutation that returns no comment node id is a malformed-model/partial response — fail, don't
        // hand back a SummaryHandle wrapping a null external ref.
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/repo#42",
            null
        );
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(prNodeIdResolver.resolve(1L, "owner", "repo", 42)).thenReturn("PR_node123");

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitHubProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);
        ClientGraphQlResponse response = mockGraphQlResponse("addComment.commentEdge.node.id", null);
        when(spec.execute()).thenReturn(Mono.just(response));

        assertThatThrownBy(() -> channel.postSummary(target, new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("No comment ID in AddPullRequestComment response");
    }

    @Test
    void postSummary_malformedIssueSubject_throwsInvalidIssueSubject() {
        // An ISSUE subject that does not match owner/repo/issues/N must fail with the issue-specific message,
        // not be mis-routed to the PR resolver.
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/issues/x",
            null
        );
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);

        assertThatThrownBy(() -> channel.postSummary(target, new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("Invalid GitHub PR subjectExternalId");
    }

    @Test
    void parseIssueSubjectExternalId_rejectsSubjectWithoutNumber() {
        // A subject that is not the owner/repo/issues/<number> form must throw the issue-specific error so a
        // malformed model output fails loudly rather than resolving to a wrong node.
        assertThatThrownBy(() -> GithubFeedbackChannel.parseIssueSubjectExternalId("owner/issues/x"))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("Invalid GitHub issue subjectExternalId");
    }

    @SuppressWarnings("unchecked")
    private ClientGraphQlResponse mockGraphQlResponse(String fieldPath, String value) {
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ClientResponseField field = mock(ClientResponseField.class);
        when(response.field(fieldPath)).thenReturn(field);
        when(field.getValue()).thenReturn(value);
        lenient().when(response.getErrors()).thenReturn(List.of());
        return response;
    }

    @Test
    void issueAndPrSubjectIds_diverge_soTheChannelCanRouteThem_andRejectMalformedRepos() {
        // PRs and issues share owner/repo#N on GitHub, so the internal issue subject MUST diverge or the
        // channel cannot tell them apart and an issue would be sent to the PR resolver (and fail to post).
        assertThat(channel.formatIssueSubjectId("owner/repo", 7)).isEqualTo("owner/repo/issues/7");
        assertThat(channel.formatPullRequestSubjectId("owner/repo", 7)).isEqualTo("owner/repo#7");
        // GitHub requires a two-segment owner/repo — fail fast, not late in node-id resolution.
        assertThatThrownBy(() -> channel.formatIssueSubjectId("nope", 7))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("owner/repo");
        assertThatThrownBy(() -> channel.formatIssueSubjectId("a/b/c", 7)).isInstanceOf(IllegalArgumentException.class);
        // A blank owner or repo segment must also fail fast, not yield "owner/#42" / "owner//issues/42".
        assertThatThrownBy(() -> channel.formatPullRequestSubjectId("owner/", 7))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("owner/repo");
        assertThatThrownBy(() -> channel.formatIssueSubjectId("/repo", 7))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("owner/repo");
    }

    @Test
    void postSummary_forAnIssueSubject_resolvesViaIssueNodeId_notThePrResolver() {
        // Regression: an ISSUE subject must route to resolveIssue (repository.issue), never resolve
        // (repository.pullRequest) which returns null for an issue and fails the whole delivery.
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/repo/issues/42",
            null
        );

        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(prNodeIdResolver.resolveIssue(1L, "owner", "repo", 42)).thenReturn("I_node789");

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitHubProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);
        ClientGraphQlResponse response = mockGraphQlResponse("addComment.commentEdge.node.id", "IC_issuecmt");
        when(spec.execute()).thenReturn(Mono.just(response));

        SummaryHandle handle = channel.postSummary(target, new FeedbackContent("body", "marker"));

        assertThat(handle.externalId()).isEqualTo("IC_issuecmt");
        verify(prNodeIdResolver).resolveIssue(1L, "owner", "repo", 42);
        verify(prNodeIdResolver, never()).resolve(anyLong(), any(), any(), anyInt());
    }
}
