package de.tum.cit.aet.hephaestus.integration.core.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookPropertiesFixture;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

class WebhookGracefulShutdownTest extends BaseUnitTest {

    @Test
    void drainsAfterWebServerShutdown() {
        WebhookGracefulShutdown shutdown =
                new WebhookGracefulShutdown(mock(JetStreamPublisher.class), WebhookPropertiesFixture.properties());

        assertThat(shutdown.getPhase()).isLessThan(WebServerApplicationContext.GRACEFUL_SHUTDOWN_PHASE);
    }
}
