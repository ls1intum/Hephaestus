package de.tum.cit.aet.hephaestus.integration.core.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamInfo;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Topology guard: across the {@code webhook}×{@code slack}×{@code nats} axes, at most ONE
 * {@link JetStreamPublisher} is ever wired. {@link WebhookConfiguration} (webhook role) and
 * {@link SlackNatsPublisherConfiguration} (server role, webhook off, slack on) both {@code @Import}
 * the shared {@link WebhookProducerBeans}, so a broadened condition that let both contribute would
 * yield a {@code NoUniqueBeanDefinitionException} at the single {@code @Nullable JetStreamPublisher}
 * injection. The load-bearing assertion is therefore "never &gt; 1 and the context still starts"; the
 * exact 0/1 counts pin which topology owns the producer cluster.
 *
 * <p>The {@code nats} axis is modeled as presence/absence of the {@code natsConnection} bean, matching
 * both configs' {@code @ConditionalOnBean(Connection.class)} gate.
 */
@Tag("unit")
class JetStreamPublisherTopologyTest {

    private final WebhookProperties properties = new WebhookProperties(
        null,
        null,
        new WebhookProperties.TokenRotation(7, 90),
        new WebhookProperties.Publish(Duration.ofSeconds(2), 3, Duration.ofMillis(10)),
        new WebhookProperties.Stream(Duration.ofMinutes(10), Duration.ofDays(180), 2_000_000L),
        new WebhookProperties.Shutdown(Duration.ofSeconds(15)),
        new WebhookProperties.Http(26_214_400L)
    );

    /** Base runner with both producer host configs + the properties/registry they need. */
    private ApplicationContextRunner baseRunner() {
        return new ApplicationContextRunner()
            .withUserConfiguration(WebhookConfiguration.class, SlackNatsPublisherConfiguration.class)
            .withBean(WebhookProperties.class, () -> properties)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);
    }

    /** NATS-on: register the {@code natsConnection} the producer beans inject via {@code @Qualifier}. */
    private ApplicationContextRunner withNats(ApplicationContextRunner runner) {
        return runner.withBean("natsConnection", Connection.class, this::newNatsConnection);
    }

    /**
     * A Connection whose JetStream management handle answers {@code getStreamInfo} with an existing
     * stream, so {@link StreamBootstrap}'s {@code @PostConstruct} completes (no addStream, no NPE) and
     * the context starts cleanly. Deep stubs give non-null (but harmlessly drifting) config fields.
     */
    private Connection newNatsConnection() {
        try {
            Connection connection = mock(Connection.class);
            JetStream jetStream = mock(JetStream.class);
            JetStreamManagement jsm = mock(JetStreamManagement.class);
            StreamInfo info = mock(StreamInfo.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            when(jsm.getStreamInfo(anyString())).thenReturn(info);
            when(connection.jetStream()).thenReturn(jetStream);
            when(connection.jetStreamManagement()).thenReturn(jsm);
            return connection;
        } catch (Exception e) {
            throw new IllegalStateException("failed to build NATS connection stub", e);
        }
    }

    private void assertPublisherCount(AssertableApplicationContext context, int expected) {
        assertThat(context).hasNotFailed();
        assertThat(context.getBeanNamesForType(JetStreamPublisher.class)).hasSize(expected);
        // The invariant, restated independently of the exact count: never a duplicate.
        assertThat(context.getBeanNamesForType(JetStreamPublisher.class).length).isLessThanOrEqualTo(1);
    }

    @Test
    void webhookOn_natsOn_slackOff_webhookConfigOwnsTheSinglePublisher() {
        withNats(baseRunner())
            .withPropertyValues("hephaestus.runtime.webhook.enabled=true", "hephaestus.integration.slack.enabled=false")
            .run(context -> assertPublisherCount(context, 1));
    }

    @Test
    void webhookOn_natsOn_slackOn_stillExactlyOnePublisher() {
        withNats(baseRunner())
            .withPropertyValues("hephaestus.runtime.webhook.enabled=true", "hephaestus.integration.slack.enabled=true")
            .run(context -> assertPublisherCount(context, 1));
    }

    @Test
    void webhookOff_slackOn_natsOn_slackConfigOwnsTheSinglePublisher() {
        withNats(baseRunner())
            .withPropertyValues("hephaestus.runtime.webhook.enabled=false", "hephaestus.integration.slack.enabled=true")
            .run(context -> assertPublisherCount(context, 1));
    }

    @Test
    void webhookOff_slackOff_natsOn_noPublisher() {
        withNats(baseRunner())
            .withPropertyValues(
                "hephaestus.runtime.webhook.enabled=false",
                "hephaestus.integration.slack.enabled=false"
            )
            .run(context -> assertPublisherCount(context, 0));
    }

    @Test
    void natsOff_neverWiresAPublisher_acrossEveryRoleSlackCombo() {
        String[][] combos = { { "true", "true" }, { "true", "false" }, { "false", "true" }, { "false", "false" } };
        for (String[] combo : combos) {
            baseRunner()
                .withPropertyValues(
                    "hephaestus.runtime.webhook.enabled=" + combo[0],
                    "hephaestus.integration.slack.enabled=" + combo[1]
                )
                .run(context -> assertPublisherCount(context, 0));
        }
    }
}
