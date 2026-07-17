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
import java.util.stream.Stream;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

class SlackMessageServiceTest extends BaseUnitTest {

    @Mock
    private SlackCredentialProvider credentialProvider;

    private SlackMessageService service;

    private boolean silentModeEngaged;

    @BeforeEach
    void setUp() {
        silentModeEngaged = false;
        service = new SlackMessageService(credentialProvider, () -> silentModeEngaged);
    }

    @Test
    void sendForWorkspace_silentMode_throwsBeforeTokenResolution() {
        silentModeEngaged = true;

        assertThatThrownBy(() -> service.sendForWorkspace(7L, "C1ABCDEFGH", List.of(), "fallback"))
            .isInstanceOf(SlackSendException.class)
            .satisfies(ex -> assertThat(((SlackSendException) ex).slackError()).isEqualTo("silent_mode_engaged"));
        org.mockito.Mockito.verifyNoInteractions(credentialProvider);
    }

    @Test
    void startStream_silentMode_throws() {
        silentModeEngaged = true;

        assertThatThrownBy(() -> service.startStream(7L, "C1ABCDEFGH", "171234.5678", "hi"))
            .isInstanceOf(SlackSendException.class)
            .satisfies(ex -> assertThat(((SlackSendException) ex).slackError()).isEqualTo("silent_mode_engaged"));
        org.mockito.Mockito.verifyNoInteractions(credentialProvider);
    }

    @Test
    void sendEphemeralForWorkspace_silentMode_throws() {
        silentModeEngaged = true;

        assertThatThrownBy(() -> service.sendEphemeralForWorkspace(7L, "C1ABCDEFGH", "U123", List.of(), "fallback"))
            .isInstanceOf(SlackSendException.class)
            .satisfies(ex -> assertThat(((SlackSendException) ex).slackError()).isEqualTo("silent_mode_engaged"));
        org.mockito.Mockito.verifyNoInteractions(credentialProvider);
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

    private static Stream<Arguments> rateLimitCases() {
        return Stream.of(
            // A 429 with Retry-After: 3 → the caller must back off 3000 ms (honoring Slack, not a fixed tick).
            Arguments.of(429, "3", 3000L),
            Arguments.of(429, null, 1000L),
            Arguments.of(500, "7", SlackSendException.NOT_RATE_LIMITED)
        );
    }

    @ParameterizedTest(name = "code={0} retryAfterHeader={1} -> {2}ms")
    @MethodSource("rateLimitCases")
    void rateLimitRetryAfterMillis(int code, String retryAfterHeader, long expected) {
        assertThat(SlackMessageService.rateLimitRetryAfterMillis(apiException(code, retryAfterHeader))).isEqualTo(
            expected
        );
    }
}
