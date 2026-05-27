package de.tum.cit.aet.hephaestus.integration.core.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class StreamBootstrapTest extends BaseUnitTest {

    private final WebhookProperties properties = new WebhookProperties(
        null,
        null,
        new WebhookProperties.TokenRotation(7, 90),
        new WebhookProperties.Publish(Duration.ofSeconds(9), 5, Duration.ofMillis(200)),
        new WebhookProperties.Stream(Duration.ofMinutes(2), Duration.ofDays(180), 2_000_000L),
        new WebhookProperties.Shutdown(Duration.ofSeconds(15)),
        new WebhookProperties.Http(26_214_400L)
    );

    @Test
    void createsStreamWhenMissing() throws Exception {
        JetStreamApiException notFound = apiException(404);
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        when(jsm.getStreamInfo(anyString())).thenThrow(notFound);

        new StreamBootstrap(jsm, properties).bootstrap();

        // One stream per registered integration kind (gitlab/github/slack/outline).
        ArgumentCaptor<StreamConfiguration> captor = ArgumentCaptor.forClass(StreamConfiguration.class);
        verify(jsm, times(4)).addStream(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(StreamConfiguration::getName)
            .containsExactlyInAnyOrder("gitlab", "github", "slack", "outline");
    }

    @Test
    void usesExistingStreamWhenPresent() throws Exception {
        StreamInfo info = mock(StreamInfo.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        when(jsm.getStreamInfo(anyString())).thenReturn(info);

        new StreamBootstrap(jsm, properties).bootstrap();

        verify(jsm, never()).addStream(any());
    }

    @Test
    void failsFastOnAddStreamError() throws Exception {
        JetStreamApiException notFound = apiException(404);
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        when(jsm.getStreamInfo(anyString())).thenThrow(notFound);
        when(jsm.addStream(any())).thenThrow(new IOException("broker unreachable"));

        StreamBootstrap bootstrap = new StreamBootstrap(jsm, properties);
        assertThatThrownBy(bootstrap::bootstrap)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to create JetStream stream");
    }

    @Test
    void failsFastOnNonNotFoundInspectError() throws Exception {
        JetStreamApiException serverError = apiException(503);
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        when(jsm.getStreamInfo("gitlab")).thenThrow(serverError);

        StreamBootstrap bootstrap = new StreamBootstrap(jsm, properties);
        assertThatThrownBy(bootstrap::bootstrap)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to inspect JetStream stream");
    }

    @Test
    void warnsWhenLiveMaxMessagesIsUnlimited(CapturedOutput output) throws Exception {
        StreamConfiguration config = mock(StreamConfiguration.class);
        when(config.getDuplicateWindow()).thenReturn(Duration.ofMinutes(2));
        when(config.getMaxAge()).thenReturn(Duration.ofDays(180));
        when(config.getMaxMsgs()).thenReturn(-1L); // unlimited; expected = 2_000_000
        when(config.getStorageType()).thenReturn(io.nats.client.api.StorageType.File);
        StreamInfo info = mock(StreamInfo.class);
        when(info.getConfiguration()).thenReturn(config);
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        when(jsm.getStreamInfo(anyString())).thenReturn(info);

        new StreamBootstrap(jsm, properties).bootstrap();

        assertThat(output.getAll()).contains("maxMessages=-1 differs from expected=2000000");
        verify(jsm, never()).addStream(any());
    }

    /** Mocks {@link JetStreamApiException}; its constructors require an {@code ApiResponse}. */
    private static JetStreamApiException apiException(int code) {
        JetStreamApiException e = mock(JetStreamApiException.class);
        when(e.getErrorCode()).thenReturn(code);
        return e;
    }
}
