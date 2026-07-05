package de.tum.cit.aet.hephaestus.integration.slack;

import de.tum.cit.aet.hephaestus.integration.slack.events.ObviousAbuseFastPathSlackSafetyClassifier;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackSafetyClassifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the overridable default bean for the Slack message-classification seam.
 *
 * <p>The default uses {@code @ConditionalOnMissingBean} so a richer implementation (a model-backed
 * moderation/safety classifier) can replace it without touching the mentor flow. The default classifier is only an
 * obvious-abuse keyword fast-path — see {@link ObviousAbuseFastPathSlackSafetyClassifier} for why it is not, on its
 * own, crisis/safety detection.
 * It lives here, on a {@code @Bean} factory method, rather than as a component-scanned {@code @Component}:
 * {@code @ConditionalOnMissingBean} is only evaluated deterministically for {@code @Bean} methods, so on a
 * scanned component it left the default unregistered and broke context startup ({@code SlackMentorService}
 * requires this seam at construction).
 */
@Configuration
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
class SlackSeamDefaultsConfiguration {

    @Bean
    @ConditionalOnMissingBean(SlackSafetyClassifier.class)
    SlackSafetyClassifier obviousAbuseFastPathSlackSafetyClassifier() {
        return new ObviousAbuseFastPathSlackSafetyClassifier();
    }
}
