package de.tum.cit.aet.hephaestus.integration.github.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.github.common.GitHubGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.spi.FindingAnchor;
import de.tum.cit.aet.hephaestus.integration.spi.FindingAnchor.DiffAnchor;
import de.tum.cit.aet.hephaestus.integration.spi.InlineFindingChannel.InlineFinding;
import de.tum.cit.aet.hephaestus.integration.spi.InlineFindingChannel.InlineResult;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

@DisplayName("GithubInlineFindingChannel")
class GithubInlineFindingChannelTest extends BaseUnitTest {

    @Mock
    private GitHubGraphQlClientProvider gitHubProvider;

    @Mock
    private GithubPrNodeIdResolver prNodeIdResolver;

    private GithubInlineFindingChannel channel;

    @BeforeEach
    void setUp() {
        channel = new GithubInlineFindingChannel(gitHubProvider, prNodeIdResolver);
    }

    @Test
    @DisplayName("empty findings returns (0, 0)")
    void emptyFindingsReturnsZero() {
        FeedbackTarget target = githubTarget();
        assertThat(channel.postInlineFindings(target, List.of())).isEqualTo(new InlineResult(0, 0));
    }

    @Test
    @DisplayName("posts DiffAnchor findings as single review with correct count")
    void postsDiffAnchorsAsBatch() {
        FeedbackTarget target = githubTarget();
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(prNodeIdResolver.resolve(1L, "owner", "repo", 42)).thenReturn("PR_node123");

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitHubProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        lenient().when(response.getErrors()).thenReturn(List.of());
        when(spec.execute()).thenReturn(Mono.just(response));

        InlineResult result = channel.postInlineFindings(
            target,
            List.of(
                new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix1", "marker"),
                new InlineFinding(new DiffAnchor("src/Bar.java", 20, null), "fix2", "marker")
            )
        );

        assertThat(result.posted()).isEqualTo(2);
        assertThat(result.failed()).isZero();
    }

    @Test
    @DisplayName("rate-limit critical short-circuits and counts all as failed")
    void rateLimitCriticalShortCircuits() {
        FeedbackTarget target = githubTarget();
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(true);

        InlineResult result = channel.postInlineFindings(
            target,
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix", "marker"))
        );

        assertThat(result.posted()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    private static FeedbackTarget githubTarget() {
        return new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/repo#42",
            "commit-sha-abc"
        );
    }
}
