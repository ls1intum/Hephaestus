package de.tum.cit.aet.hephaestus.integration.slack.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.slack.api.methods.SlackApiException;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.slack.credentials.SlackCredentialProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class SlackMessageServiceTest extends BaseUnitTest {

    @Mock
    private SlackCredentialProvider credentialProvider;

    private SlackMessageService service;

    @BeforeEach
    void setUp() {
        service = new SlackMessageService(credentialProvider);
    }

    @Test
    void sendForWorkspace_noToken_throwsSlackSendException() {
        when(credentialProvider.resolve(any(IntegrationRef.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendForWorkspace(7L, "C1ABCDEFGH", List.of(), "fallback"))
            .isInstanceOf(SlackSendException.class)
            .satisfies(ex -> {
                SlackSendException sse = (SlackSendException) ex;
                assertThat(sse.workspaceId()).isEqualTo(7L);
                assertThat(sse.channelId()).isEqualTo("C1ABCDEFGH");
                assertThat(sse.slackError()).isEqualTo("no_active_slack_connection");
            });
    }

    private static SlackApiException apiException(int code, String retryAfterHeader) {
        Request request = new Request.Builder().url("https://slack.com/api/chat.postMessage").build();
        Response.Builder builder = new Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test");
        if (retryAfterHeader != null) {
            builder.header("Retry-After", retryAfterHeader);
        }
        return new SlackApiException(builder.build(), "");
    }

    @Test
    void rateLimitRetryAfter_readsSecondsHeaderAsMillis() {
        // A 429 with Retry-After: 3 → the caller must back off 3000 ms (honoring Slack, not a fixed tick).
        assertThat(SlackMessageService.rateLimitRetryAfterMillis(apiException(429, "3"))).isEqualTo(3000L);
    }

    @Test
    void rateLimitRetryAfter_defaultsToOneSecondWhenHeaderMissing() {
        assertThat(SlackMessageService.rateLimitRetryAfterMillis(apiException(429, null))).isEqualTo(1000L);
    }

    @Test
    void rateLimitRetryAfter_nonRateLimitedResponse_returnsSentinel() {
        assertThat(SlackMessageService.rateLimitRetryAfterMillis(apiException(500, "7"))).isEqualTo(
            SlackSendException.NOT_RATE_LIMITED
        );
    }
}
