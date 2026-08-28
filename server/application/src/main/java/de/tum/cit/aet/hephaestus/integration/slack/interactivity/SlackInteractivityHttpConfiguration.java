package de.tum.cit.aet.hephaestus.integration.slack.interactivity;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnWebhookRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebhookRole
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
class SlackInteractivityHttpConfiguration {

    @Bean
    FilterRegistrationBean<SlackInteractivityRawBodyFilter> slackInteractivityRawBodyFilter() {
        FilterRegistrationBean<SlackInteractivityRawBodyFilter> registration = new FilterRegistrationBean<>(
            new SlackInteractivityRawBodyFilter()
        );
        registration.addUrlPatterns("/webhooks/slack/interactivity");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
