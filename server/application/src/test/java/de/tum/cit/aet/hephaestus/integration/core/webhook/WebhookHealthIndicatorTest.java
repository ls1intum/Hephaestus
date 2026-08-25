package de.tum.cit.aet.hephaestus.integration.core.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamInfo;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class WebhookHealthIndicatorTest extends BaseUnitTest {

    @Test
    void upWhenConnectedAndStreamsReachable() throws Exception {
        Connection connection = mock(Connection.class);
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        when(connection.getStatus()).thenReturn(Connection.Status.CONNECTED);
        when(jsm.getStreamInfo(anyString())).thenReturn(mock(StreamInfo.class));

        Health health = new WebhookHealthIndicator(connection, jsm).health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("natsStatus", "CONNECTED");
        verify(jsm).getStreamInfo("gitlab");
        verify(jsm).getStreamInfo("github");
        verify(jsm).getStreamInfo("slack");
        // Stream message counts must NOT be exposed in the /actuator/health detail.
        assertThat(health.getDetails().keySet()).noneMatch(k -> k.contains(".messages"));
    }

    @Test
    void downWhenConnectionStatusNotConnected() {
        Connection connection = mock(Connection.class);
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        when(connection.getStatus()).thenReturn(Connection.Status.DISCONNECTED);

        Health health = new WebhookHealthIndicator(connection, jsm).health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("natsStatus", "DISCONNECTED");
    }

    @Test
    void downWhenStreamProbeFailsAndLeaksOnlyExceptionClassName() throws Exception {
        Connection connection = mock(Connection.class);
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        when(connection.getStatus()).thenReturn(Connection.Status.CONNECTED);
        when(jsm.getStreamInfo("gitlab")).thenThrow(new IOException("super-secret JetStream internal"));

        Health health = new WebhookHealthIndicator(connection, jsm).health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("stream.gitlab.error", "IOException");
        // The original exception message must NOT leak into the actuator response.
        assertThat(health.getDetails().values().toString()).doesNotContain("super-secret");
    }
}
