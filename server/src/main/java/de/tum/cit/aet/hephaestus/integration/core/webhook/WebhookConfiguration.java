package de.tum.cit.aet.hephaestus.integration.core.webhook;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import de.tum.cit.aet.hephaestus.integration.core.webhook.WebhookPayloadSizeFilter;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Wires the webhook receiver. Gated by {@code hephaestus.runtime.webhook.enabled} (default on)
 * AND requires the {@link Connection} bean from {@code config.NatsConfig} — the publisher
 * reuses that connection rather than opening a second one. See ADR 0008.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = RuntimeRole.WEBHOOK_PROPERTY, havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(Connection.class)
public class WebhookConfiguration {

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
    WebhookHealthIndicator webhookHealthIndicator(
        @Qualifier("natsConnection") Connection connection,
        JetStreamManagement jsm
    ) {
        return new WebhookHealthIndicator(connection, jsm);
    }

    @Bean
    WebhookGracefulShutdown webhookGracefulShutdown(JetStreamPublisher publisher, WebhookProperties properties) {
        return new WebhookGracefulShutdown(publisher, properties);
    }

    @Bean
    FilterRegistrationBean<WebhookPayloadSizeFilter> webhookPayloadSizeFilter(
        WebhookProperties properties,
        MeterRegistry meterRegistry
    ) {
        FilterRegistrationBean<WebhookPayloadSizeFilter> registration = new FilterRegistrationBean<>(
            new WebhookPayloadSizeFilter(properties, meterRegistry)
        );
        registration.addUrlPatterns("/webhooks/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
