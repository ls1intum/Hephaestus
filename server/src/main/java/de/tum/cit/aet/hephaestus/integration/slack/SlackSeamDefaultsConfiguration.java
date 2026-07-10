package de.tum.cit.aet.hephaestus.integration.slack;

import de.tum.cit.aet.hephaestus.integration.slack.events.KeywordSlackMentorInputGuard;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackMentorInputGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the overridable default bean for the Slack mentor input guard.
 *
 * <p>The default uses {@code @ConditionalOnMissingBean} so a richer implementation can replace it without touching
 * the mentor flow. It lives here, on a {@code @Bean} factory method, rather than as a component-scanned
 * {@code @Component}:
 * {@code @ConditionalOnMissingBean} is only evaluated deterministically for {@code @Bean} methods, so on a
 * scanned component it left the default unregistered and broke context startup.
 */
@Configuration
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
class SlackSeamDefaultsConfiguration {

    @Bean
    @ConditionalOnMissingBean(SlackMentorInputGuard.class)
    SlackMentorInputGuard slackMentorInputGuard() {
        return new KeywordSlackMentorInputGuard();
    }
}
