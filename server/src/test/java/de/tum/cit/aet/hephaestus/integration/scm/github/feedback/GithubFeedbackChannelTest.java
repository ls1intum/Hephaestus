package de.tum.cit.aet.hephaestus.integration.scm.github.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
    void issueAndPrSubjectIds_useOwnerRepoHashNumber_andRejectMalformedRepos() {
        assertThat(channel.formatIssueSubjectId("owner/repo", 7)).isEqualTo("owner/repo#7");
        assertThat(channel.formatPullRequestSubjectId("owner/repo", 7)).isEqualTo("owner/repo#7");
        // GitHub requires a two-segment owner/repo — fail fast, not late in node-id resolution.
        assertThatThrownBy(() -> channel.formatIssueSubjectId("nope", 7))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("owner/repo");
        assertThatThrownBy(() -> channel.formatIssueSubjectId("a/b/c", 7)).isInstanceOf(IllegalArgumentException.class);
    }
}
