package de.tum.cit.aet.hephaestus.integration.gitlab.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.gitlab.feedback.GitlabMrResolver.MrInfo;
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

@DisplayName("GitlabApprovalChannel")
class GitlabApprovalChannelTest extends BaseUnitTest {

    @Mock
    private GitLabGraphQlClientProvider gitLabProvider;

    @Mock
    private GitlabMrResolver mrResolver;

    @Mock
    private GitlabFeedbackChannel feedbackChannel;

    private GitlabApprovalChannel channel;

    @BeforeEach
    void setUp() {
        channel = new GitlabApprovalChannel(gitLabProvider, mrResolver, feedbackChannel);
    }

    @Test
    @DisplayName("approve invokes ApproveMergeRequest mutation against resolved MR global gid")
    void approveInvokesMutation() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42))
            .thenReturn(new MrInfo("gid://gitlab/MR/42", "base", "head", "start"));

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName("ApproveMergeRequest")).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ClientResponseField errorsField = mock(ClientResponseField.class);
        when(response.field("mergeRequestApprove.errors")).thenReturn(errorsField);
        when(errorsField.getValue()).thenReturn(List.of());
        lenient().when(response.getErrors()).thenReturn(List.of());
        when(spec.execute()).thenReturn(Mono.just(response));

        assertThatCode(() -> channel.approve(target, null)).doesNotThrowAnyException();

        verify(client).documentName("ApproveMergeRequest");
        verify(spec).variable(eq("mergeRequestId"), eq("gid://gitlab/MR/42"));
    }

    @Test
    @DisplayName("approve throws on rate limit critical")
    void approveThrowsOnRateLimit() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(true);
        assertThatThrownBy(() -> channel.approve(target, null))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("rate limit critical");
    }

    @Test
    @DisplayName("approve throws on mergeRequestApprove errors")
    void approveThrowsOnMutationErrors() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42))
            .thenReturn(new MrInfo("gid://gitlab/MR/42", "base", "head", "start"));

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ClientResponseField errorsField = mock(ClientResponseField.class);
        when(response.field("mergeRequestApprove.errors")).thenReturn(errorsField);
        when(errorsField.getValue()).thenReturn(List.of("not authorized"));
        when(spec.execute()).thenReturn(Mono.just(response));

        assertThatThrownBy(() -> channel.approve(target, null))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("mergeRequestApprove failed");
    }

    private static FeedbackTarget gitlabTarget() {
        return new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITLAB, 1L, null),
            "group/project!42",
            null
        );
    }
}
