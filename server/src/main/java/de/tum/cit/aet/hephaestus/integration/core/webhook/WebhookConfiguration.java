package de.tum.cit.aet.hephaestus.integration.core.webhook;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

/**
 * Wires the webhook receiver. Gated by {@code hephaestus.runtime.webhook.enabled} (default on)
 * AND requires the {@link Connection} bean from {@code config.NatsConfig} — the publisher
 * reuses that connection rather than opening a second one. See ADR 0008.
 *
 * <p>The NATS producer beans (JetStream, publisher, stream bootstrap, graceful drain) are shared
 * with {@link SlackNatsPublisherConfiguration} via {@link WebhookProducerBeans}; the two importers
 * are mutually exclusive by condition, so exactly one contributes them.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = RuntimeRole.WEBHOOK_PROPERTY, havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(Connection.class)
@Import(WebhookProducerBeans.class)
public class WebhookConfiguration {

    @Bean
    WebhookHealthIndicator webhookHealthIndicator(
        @Qualifier("natsConnection") Connection connection,
        JetStreamManagement jsm
    ) {
        return new WebhookHealthIndicator(connection, jsm);
    }

    @Bean
    FilterRegistrationBean<WebhookPayloadSizeFilter> webhookPayloadSizeFilter(
        WebhookProperties properties,
        MeterRegistry meterRegistry
    ) {
        FilterRegistrationBean<WebhookPayloadSizeFilter> registration = new FilterRegistrationBean<>(
            new WebhookPayloadSizeFilter(properties, meterRegistry)
        );
        // Guards the GitHub/GitLab HMAC receiver. The /slack/* endpoints are size-capped separately by
        // SlackWebSecurityConfiguration (always-on when Slack is enabled, regardless of role/NATS), so the
        // Slack guard survives even the NATS-off topology where neither webhook nor Slack-NATS config loads.
        registration.addUrlPatterns("/webhooks/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
