package de.tum.cit.aet.hephaestus.integration.core.webhook;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Makes the NATS <em>producer</em> cluster (JetStream, stream bootstrap, publisher, graceful
 * drain, payload-size filter) available to the Slack Events endpoint when it runs on a pod
 * where the webhook runtime role is OFF — i.e. the production {@code application-server}, which
 * hosts the Slack mentor stack + the {@code IntegrationNatsConsumer} but deploys with
 * {@code HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED=false} (webhook reception is isolated on the
 * dedicated {@code webhook-server}, ADR 0008).
 *
 * <p><strong>Why a second config instead of broadening {@link WebhookConfiguration}.</strong>
 * {@code WebhookConfiguration}'s {@code @ConditionalOnProperty(webhook)} gate is pinned by
 * {@code RuntimeRoleBoundaryTest} (role-isolation guard) and must not change. This config is
 * gated {@code @ConditionalOnExpression("!webhook and slack")} so it and
 * {@code WebhookConfiguration} are <strong>mutually exclusive by construction</strong>: exactly
 * one of them contributes the {@link JetStreamPublisher} bean, so
 * {@code WebhookIngestPipeline}'s single {@code @Nullable JetStreamPublisher} injection never
 * sees a duplicate. Topology matrix:
 * <ul>
 *   <li>monolith / webhook-server (webhook on) → {@code WebhookConfiguration} owns the cluster; this is inert.</li>
 *   <li>application-server (webhook off, slack on) → this owns the cluster; {@code WebhookConfiguration} is inert.</li>
 *   <li>worker (webhook off, slack off) → neither; no publisher (nothing needs one).</li>
 * </ul>
 *
 * <p>Requires the {@code natsConnection} bean ({@code hephaestus.sync.nats.enabled=true}); if
 * NATS is disabled the publisher is simply absent and the Slack events endpoint replies 503 on
 * the channel branch (Slack redelivers) rather than dropping content silently.
 *
 * @see SlackNatsPublisherConfiguration mirrors the producer beans in {@link WebhookConfiguration}
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
    "!${hephaestus.runtime.webhook.enabled:true} and ${hephaestus.integration.slack.enabled:false}"
)
@ConditionalOnBean(Connection.class)
public class SlackNatsPublisherConfiguration {

    @Bean
    JetStream webhookJetStream(@Qualifier("natsConnection") Connection natsConnection) throws IOException {
        return natsConnection.jetStream();
    }

    @Bean
    JetStreamManagement webhookJetStreamManagement(@Qualifier("natsConnection") Connection natsConnection)
        throws IOException {
        return natsConnection.jetStreamManagement();
    }

    @Bean
    Retry webhookPublishRetry(WebhookProperties properties) {
        WebhookProperties.Publish p = properties.publish();
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(p.maxRetries())
            .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(p.retryBaseDelay(), 2.0, 0.25))
            .ignoreExceptions(IllegalArgumentException.class)
            .failAfterMaxAttempts(true)
            .build();
        return Retry.of("webhook-publish", config);
    }

    @Bean
    JetStreamPublisher jetStreamPublisher(
        JetStream jetStream,
        Retry webhookPublishRetry,
        WebhookProperties properties,
        MeterRegistry meterRegistry
    ) {
        return new JetStreamPublisher(jetStream, webhookPublishRetry, properties, meterRegistry);
    }

    @Bean
    StreamBootstrap webhookStreamBootstrap(JetStreamManagement jsm, WebhookProperties properties) {
        return new StreamBootstrap(jsm, properties);
    }

    @Bean
    WebhookGracefulShutdown webhookGracefulShutdown(JetStreamPublisher publisher, WebhookProperties properties) {
        return new WebhookGracefulShutdown(publisher, properties);
    }

    @Bean
    FilterRegistrationBean<WebhookPayloadSizeFilter> slackPayloadSizeFilter(
        WebhookProperties properties,
        MeterRegistry meterRegistry
    ) {
        FilterRegistrationBean<WebhookPayloadSizeFilter> registration = new FilterRegistrationBean<>(
            new WebhookPayloadSizeFilter(properties, meterRegistry)
        );
        registration.addUrlPatterns("/webhooks/*", "/slack/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
