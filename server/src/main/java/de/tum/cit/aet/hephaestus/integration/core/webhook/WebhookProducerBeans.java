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
import org.springframework.context.annotation.Bean;

/**
 * The NATS <em>producer</em> cluster shared verbatim by {@link WebhookConfiguration} (webhook role)
 * and {@link SlackNatsPublisherConfiguration} (server role, webhook off, Slack on): JetStream +
 * management handles, the publish-retry policy, the {@link JetStreamPublisher}, the
 * {@link StreamBootstrap}, and the {@link WebhookGracefulShutdown}.
 *
 * <p><strong>Not component-scanned.</strong> This is a plain class with {@code @Bean} factory
 * methods (no {@code @Configuration}/{@code @Component} stereotype), so it is only ever contributed
 * via {@code @Import} from exactly one of the two host configs. Because those hosts are mutually
 * exclusive by condition ({@code webhook} vs {@code !webhook and slack}), only one imports these
 * beans, so there is never a duplicate {@link JetStreamPublisher}. Keeping the producer beans in one
 * place removes the copy that had drifted between the two configs.
 */
class WebhookProducerBeans {

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
            // Subject misconfiguration (unknown stream prefix) is permanent; don't burn retries on it.
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
}
