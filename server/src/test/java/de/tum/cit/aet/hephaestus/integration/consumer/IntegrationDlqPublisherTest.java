package de.tum.cit.aet.hephaestus.integration.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.gitprovider.webhook.StreamBootstrap;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PublishOptions;
import io.nats.client.impl.Headers;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("IntegrationDlqPublisher — poison publish-before-ACK")
class IntegrationDlqPublisherTest extends BaseUnitTest {

    private static Message msg(String subject, byte[] body) {
        Message m = mock(Message.class);
        org.mockito.Mockito.lenient().when(m.getSubject()).thenReturn(subject);
        org.mockito.Mockito.lenient().when(m.getData()).thenReturn(body);
        return m;
    }

    @Test
    @DisplayName("publish: builds INTEGRATION_DLQ subject from kind + original subject + carries body")
    void publishesToDlqSubject() throws Exception {
        JetStream js = mock(JetStream.class);
        IntegrationDlqPublisher publisher = new IntegrationDlqPublisher(js, new SimpleMeterRegistry());
        byte[] body = "{\"installation\":{\"id\":42}}".getBytes();
        Message m = msg("github.acme.foo.installation", body);

        boolean ok = publisher.publish(m, "github", "handler-threw", 10L, 12345L);

        assertThat(ok).isTrue();
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Headers> headers = ArgumentCaptor.forClass(Headers.class);
        ArgumentCaptor<byte[]> bodyCap = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<PublishOptions> opts = ArgumentCaptor.forClass(PublishOptions.class);
        verify(js).publish(subject.capture(), headers.capture(), bodyCap.capture(), opts.capture());

        assertThat(subject.getValue())
            .as("subject must start with the DLQ prefix and embed the original subject")
            .isEqualTo(StreamBootstrap.DLQ_SUBJECT_PREFIX + ".github.github.acme.foo.installation");
        assertThat(headers.getValue().get(IntegrationDlqPublisher.HEADER_ORIGINAL_SUBJECT))
            .containsExactly("github.acme.foo.installation");
        assertThat(headers.getValue().get(IntegrationDlqPublisher.HEADER_FAILURE_REASON))
            .containsExactly("handler-threw");
        assertThat(headers.getValue().get(IntegrationDlqPublisher.HEADER_DELIVERED_COUNT))
            .containsExactly("10");
        assertThat(headers.getValue().get(IntegrationDlqPublisher.HEADER_STREAM_SEQUENCE))
            .containsExactly("12345");
        assertThat(bodyCap.getValue()).isEqualTo(body);
        assertThat(opts.getValue().getExpectedStream()).isEqualTo(StreamBootstrap.DLQ_STREAM);
    }

    @Test
    @DisplayName("publish: JetStream throws → returns false (poison handler MUST NAK on false)")
    void jetStreamFailureReturnsFalse() throws Exception {
        JetStream js = mock(JetStream.class);
        when(js.publish(any(String.class), any(Headers.class), any(byte[].class), any(PublishOptions.class)))
            .thenThrow(new IOException("nats down"));
        IntegrationDlqPublisher publisher = new IntegrationDlqPublisher(js, new SimpleMeterRegistry());

        boolean ok = publisher.publish(msg("slack.T1.C1.message", new byte[0]),
            "slack", "any", 5L, 99L);

        assertThat(ok).isFalse();
    }

    @Test
    @DisplayName("publish: null JetStream (no consumer on this pod) → returns false, never throws")
    void noJetStreamWired_returnsFalse() {
        IntegrationDlqPublisher publisher = new IntegrationDlqPublisher(null, new SimpleMeterRegistry());

        boolean ok = publisher.publish(msg("gitlab.acme~grp.proj.push", new byte[0]),
            "gitlab", "any", 5L, 99L);

        assertThat(ok).isFalse();
    }

    @Test
    @DisplayName("DLQ counter increments per kind on success")
    void counterIncrementsPerKind() throws Exception {
        JetStream js = mock(JetStream.class);
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        IntegrationDlqPublisher publisher = new IntegrationDlqPublisher(js, reg);

        publisher.publish(msg("github.x.y.installation", new byte[0]), "github", "r", 10, 1);
        publisher.publish(msg("github.x.y.installation", new byte[0]), "github", "r", 10, 2);
        publisher.publish(msg("gitlab.x.y.push", new byte[0]), "gitlab", "r", 10, 3);

        assertThat(reg.counter(IntegrationDlqPublisher.DLQ_COUNTER, "kind", "github").count()).isEqualTo(2.0);
        assertThat(reg.counter(IntegrationDlqPublisher.DLQ_COUNTER, "kind", "gitlab").count()).isEqualTo(1.0);
    }
}
