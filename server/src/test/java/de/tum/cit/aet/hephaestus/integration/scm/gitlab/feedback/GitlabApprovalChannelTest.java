package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.FeedbackContent;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabTokenService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.web.reactive.function.client.WebClient;

class GitlabApprovalChannelTest extends BaseUnitTest {

    @Mock
    private GitLabGraphQlClientProvider gitLabProvider;

    @Mock
    private GitLabTokenService tokenService;

    @Mock
    private GitlabFeedbackChannel feedbackChannel;

    private MockWebServer gitlab;
    private GitlabApprovalChannel channel;

    @BeforeEach
    void setUp() throws IOException {
        gitlab = new MockWebServer();
        gitlab.start();
        channel = new GitlabApprovalChannel(gitLabProvider, tokenService, feedbackChannel, WebClient.builder());
    }

    @AfterEach
    void tearDown() throws IOException {
        gitlab.close();
    }

    private void stubScope() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        // Trailing slash on purpose: the URL builder must not emit "//api/v4".
        when(tokenService.resolveServerUrl(1L)).thenReturn(gitlab.url("/").toString());
        when(tokenService.getAccessToken(1L)).thenReturn("glpat-secret");
    }

    /**
     * The endpoint contract, pinned: GitLab addresses a project by its percent-encoded full path, so the
     * request line must carry {@code %2F} — not a bare slash (a different, non-existent route) and not
     * {@code %252F} (double encoding, which resolves to no project). A live GitLab 19.1 instance answers 201
     * to exactly this request; the previous {@code mergeRequestApprove} GraphQL mutation does not exist there.
     */
    @Test
    void approvePostsToThePercentEncodedProjectPath() throws InterruptedException {
        stubScope();
        gitlab.enqueue(new MockResponse.Builder().code(201).body("{\"id\":1}").build());

        assertThatCode(() -> channel.approve(gitlabTarget(), null)).doesNotThrowAnyException();

        RecordedRequest request = gitlab.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getTarget()).isEqualTo("/api/v4/projects/group%2Fproject/merge_requests/42/approve");
        assertThat(request.getHeaders().get("Authorization")).isEqualTo("Bearer glpat-secret");
    }

    /**
     * GitLab answers 401 when the token's user may not approve the merge request — most often because it
     * authored it. That is a permanent condition, and the failure must say so rather than surface a bare 401.
     */
    @Test
    void approveSurfacesTheReasonOnUnauthorized() {
        stubScope();
        gitlab.enqueue(new MockResponse.Builder().code(401).body("{\"message\":\"401 Unauthorized\"}").build());

        assertThatThrownBy(() -> channel.approve(gitlabTarget(), null))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("group/project!42")
            .hasMessageContaining("may not approve");
    }

    @Test
    void approveThrowsOnServerError() {
        stubScope();
        gitlab.enqueue(new MockResponse.Builder().code(500).build());

        assertThatThrownBy(() -> channel.approve(gitlabTarget(), null)).isInstanceOf(FeedbackDeliveryException.class);
    }

    /** A failed approval must not leave a note claiming the merge request was approved. */
    @Test
    void approveDoesNotPostTheMessageWhenTheApprovalFailed() {
        stubScope();
        gitlab.enqueue(new MockResponse.Builder().code(401).build());

        assertThatThrownBy(() -> channel.approve(gitlabTarget(), "looks good")).isInstanceOf(
            FeedbackDeliveryException.class
        );

        verify(feedbackChannel, never()).postSummary(any(), any());
    }

    /** GitLab's approve endpoint carries no body, so a message rides as a separate note. */
    @Test
    void approvePostsTheMessageAsASeparateNote() {
        stubScope();
        gitlab.enqueue(new MockResponse.Builder().code(201).body("{\"id\":1}").build());

        channel.approve(gitlabTarget(), "looks good");

        ArgumentCaptor<FeedbackContent> content = ArgumentCaptor.forClass(FeedbackContent.class);
        verify(feedbackChannel).postSummary(any(), content.capture());
        assertThat(content.getValue().body()).isEqualTo("looks good");
    }

    @Test
    void approveThrowsOnRateLimit() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(true);

        assertThatThrownBy(() -> channel.approve(gitlabTarget(), null))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("rate limit critical");
    }

    private static FeedbackTarget gitlabTarget() {
        return new FeedbackTarget(new IntegrationRef(IntegrationKind.GITLAB, 1L, null), "group/project!42", null);
    }
}
