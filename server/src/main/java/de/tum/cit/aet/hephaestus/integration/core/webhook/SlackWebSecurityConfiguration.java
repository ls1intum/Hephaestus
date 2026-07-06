package de.tum.cit.aet.hephaestus.integration.core.webhook;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the {@code /slack/*} payload-size guard whenever the Slack integration is enabled,
 * independent of the webhook runtime role AND of whether NATS is on.
 *
 * <p><strong>Why this must be its own always-on-when-Slack config.</strong> The Slack Events and
 * Interactivity endpoints ({@code POST /slack/events}, {@code POST /slack/interactivity}) read an
 * unauthenticated {@code @RequestBody byte[]} <em>before</em> the v0 HMAC check, over the public
 * tunnel — so an oversized body would be buffered before verification (memory-exhaustion vector).
 * The producer configs that used to register this guard ({@code WebhookConfiguration},
 * {@code SlackNatsPublisherConfiguration}) are both gated on NATS/webhook-role, so in the documented
 * {@code slack.enabled=true} + {@code sync.nats.enabled=false} topology neither loads and the guard
 * would be missing. Binding it here — gated only on {@code hephaestus.integration.slack.enabled} —
 * makes the guard present in every role/NATS combination where {@code /slack/*} can be reached, and
 * keeps exactly one {@code /slack/*} registration across all combos.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
class SlackWebSecurityConfiguration {

    @Bean
    FilterRegistrationBean<WebhookPayloadSizeFilter> slackPayloadSizeFilter(
        WebhookProperties properties,
        MeterRegistry meterRegistry
    ) {
        FilterRegistrationBean<WebhookPayloadSizeFilter> registration = new FilterRegistrationBean<>(
            new WebhookPayloadSizeFilter(properties, meterRegistry)
        );
        // Covers both /slack/events and /slack/interactivity. HIGHEST_PRECEDENCE so the Content-Length cap
        // runs before Spring buffers the body and before the signature verifier.
        registration.addUrlPatterns("/slack/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
