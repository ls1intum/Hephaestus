package de.tum.cit.aet.hephaestus.integration.core.webhook;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.Connection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

/**
 * Makes the NATS <em>producer</em> cluster (JetStream, stream bootstrap, publisher, graceful
 * drain) available to the Slack Events endpoint when it runs on a pod where the webhook runtime
 * role is OFF — i.e. the production {@code application-server}, which hosts the Slack mentor stack +
 * the {@code IntegrationNatsConsumer} but deploys with {@code HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED=false}
 * (webhook reception is isolated on the dedicated {@code webhook-server}, ADR 0008).
 *
 * <p><strong>Why a second config instead of broadening {@link WebhookConfiguration}.</strong>
 * {@code WebhookConfiguration}'s {@code @ConditionalOnProperty(webhook)} gate is pinned by
 * {@code RuntimeRoleBoundaryTest} (role-isolation guard) and must not change. This config is
 * gated {@code @ConditionalOnExpression("!webhook and slack")} so it and
 * {@code WebhookConfiguration} are <strong>mutually exclusive by construction</strong>: exactly
 * one of them imports {@link WebhookProducerBeans}, so the {@link JetStreamPublisher} bean is
 * contributed once and {@code WebhookIngestPipeline}'s single {@code @Nullable JetStreamPublisher}
 * injection never sees a duplicate. Topology matrix:
 * <ul>
 *   <li>monolith / webhook-server (webhook on) → {@code WebhookConfiguration} owns the cluster; this is inert.</li>
 *   <li>application-server (webhook off, slack on) → this owns the cluster; {@code WebhookConfiguration} is inert.</li>
 *   <li>worker (webhook off, slack off) → neither; no publisher (nothing needs one).</li>
 * </ul>
 *
 * <p>Requires the {@code natsConnection} bean ({@code hephaestus.sync.nats.enabled=true}); if
 * NATS is disabled the publisher is simply absent and the Slack events endpoint replies 503 on
 * the channel branch (Slack redelivers) rather than dropping content silently. The unauthenticated
 * {@code /slack/*} payload-size guard is NOT registered here — it lives in
 * {@code SlackWebSecurityConfiguration} (gated only on {@code slack.enabled}) so it is present even
 * in this NATS-off topology where this config does not load.
 *
 * @see WebhookConfiguration the webhook-role owner of the same {@link WebhookProducerBeans}
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
    "!${hephaestus.runtime.webhook.enabled:true} and ${hephaestus.integration.slack.enabled:false}"
)
@ConditionalOnBean(Connection.class)
@Import(WebhookProducerBeans.class)
public class SlackNatsPublisherConfiguration {

    @Bean
    FilterRegistrationBean<WebhookPayloadSizeFilter> webhookPayloadSizeFilter(
        WebhookProperties properties,
        MeterRegistry meterRegistry
    ) {
        FilterRegistrationBean<WebhookPayloadSizeFilter> registration = new FilterRegistrationBean<>(
            new WebhookPayloadSizeFilter(properties, meterRegistry)
        );
        // Only /webhooks/* here (kept symmetric with WebhookConfiguration). The /slack/* guard is registered
        // unconditionally-when-Slack by SlackWebSecurityConfiguration, so it survives the NATS-off topology.
        registration.addUrlPatterns("/webhooks/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
