package de.tum.cit.aet.hephaestus.integration.slack;

import de.tum.cit.aet.hephaestus.integration.slack.events.HeuristicSlackSafetyClassifier;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackSafetyClassifier;
import de.tum.cit.aet.hephaestus.integration.slack.health.NoopSlackAuthLivenessClient;
import de.tum.cit.aet.hephaestus.integration.slack.health.SlackAuthLivenessClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the overridable default beans for the Slack duty-of-care and token-liveness seams (S9).
 *
 * <p>Both defaults use {@code @ConditionalOnMissingBean} so a richer implementation (a model-backed
 * safety classifier, a live {@code auth.test} client) can replace them without touching the mentor flow.
 * They live here, on {@code @Bean} factory methods, rather than as component-scanned {@code @Component}s:
 * {@code @ConditionalOnMissingBean} is only evaluated deterministically for {@code @Bean} methods, so on a
 * scanned component it left the default unregistered and broke context startup ({@code SlackMentorService}
 * and {@code SlackTokenHealthProbe} both require these seams at construction).
 */
@Configuration
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
class SlackSeamDefaultsConfiguration {

    @Bean
    @ConditionalOnMissingBean(SlackSafetyClassifier.class)
    SlackSafetyClassifier heuristicSlackSafetyClassifier() {
        return new HeuristicSlackSafetyClassifier();
    }

    @Bean
    @ConditionalOnMissingBean(SlackAuthLivenessClient.class)
    SlackAuthLivenessClient noopSlackAuthLivenessClient() {
        return new NoopSlackAuthLivenessClient();
    }
}
