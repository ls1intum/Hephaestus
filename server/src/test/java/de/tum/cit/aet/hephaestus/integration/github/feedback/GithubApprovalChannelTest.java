package de.tum.cit.aet.hephaestus.integration.github.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.github.common.GitHubGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

@DisplayName("GithubApprovalChannel")
class GithubApprovalChannelTest extends BaseUnitTest {

    @Mock
    private GitHubGraphQlClientProvider gitHubProvider;

    @Mock
    private GithubPrNodeIdResolver prNodeIdResolver;

    private GithubApprovalChannel channel;

    @BeforeEach
    void setUp() {
        channel = new GithubApprovalChannel(gitHubProvider, prNodeIdResolver);
    }

    @Test
    @DisplayName("approve invokes ApprovePullRequest mutation against resolved PR node id")
    void approveInvokesMutation() {
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
        when(client.documentName("ApprovePullRequest")).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ClientResponseField idField = mock(ClientResponseField.class);
        when(response.field("addPullRequestReview.pullRequestReview.id")).thenReturn(idField);
        when(idField.getValue()).thenReturn("PRR_xyz");
        lenient().when(response.getErrors()).thenReturn(List.of());
        when(spec.execute()).thenReturn(Mono.just(response));

        assertThatCode(() -> channel.approve(target, "looks good")).doesNotThrowAnyException();

        verify(client).documentName("ApprovePullRequest");
        verify(spec).variable(eq("pullRequestId"), eq("PR_node123"));
        verify(spec).variable(eq("body"), eq("looks good"));
    }

    @Test
    @DisplayName("approve throws when GitHub rate limit critical")
    void approveThrowsOnRateLimit() {
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/repo#42",
            null
        );
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(true);

        assertThatThrownBy(() -> channel.approve(target, "ok"))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("rate limit critical");
    }

    @Test
    @DisplayName("approve throws when mutation returns errors")
    void approveThrowsOnMutationErrors() {
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

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        when(response.getErrors()).thenReturn(List.of(mock(org.springframework.graphql.ResponseError.class)));
        when(spec.execute()).thenReturn(Mono.just(response));

        assertThatThrownBy(() -> channel.approve(target, "ok"))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("ApprovePullRequest failed");
    }
}
