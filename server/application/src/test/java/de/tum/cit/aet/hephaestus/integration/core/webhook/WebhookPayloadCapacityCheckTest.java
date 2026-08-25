package de.tum.cit.aet.hephaestus.integration.core.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import de.tum.cit.aet.hephaestus.core.webhook.WebhookPropertiesFixture;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.nats.client.Connection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class WebhookPayloadCapacityCheckTest extends BaseUnitTest {

    private final WebhookProperties properties = WebhookPropertiesFixture.properties();

    @Test
    void reportsAReceiverThatAcceptsMoreThanTheBrokerWillTake(CapturedOutput output) {
        // NATS' shipped max_payload is 1 MB; the receiver admits 25 MiB. Everything between the two
        // passes signature verification and is then refused at publish.
        Connection connection = mock(Connection.class);
        when(connection.getMaxPayload()).thenReturn(1_048_576L);

        new WebhookPayloadCapacityCheck(connection, properties).verify();

        assertThat(output.getAll()).contains("accepts webhooks up to 26214400 bytes but this NATS server caps");
    }

    @Test
    void staysQuietWhenTheBrokerTakesEverythingTheReceiverAccepts(CapturedOutput output) {
        Connection connection = mock(Connection.class);
        when(connection.getMaxPayload()).thenReturn(26_214_400L);

        new WebhookPayloadCapacityCheck(connection, properties).verify();

        assertThat(output.getAll()).doesNotContain("caps a message at 26214400");
    }

    @Test
    void staysQuietWhenTheServerDeclaresNoLimit(CapturedOutput output) {
        Connection connection = mock(Connection.class);
        when(connection.getMaxPayload()).thenReturn(0L);

        new WebhookPayloadCapacityCheck(connection, properties).verify();

        assertThat(output.getAll()).doesNotContain("caps a message at 0");
    }
}
