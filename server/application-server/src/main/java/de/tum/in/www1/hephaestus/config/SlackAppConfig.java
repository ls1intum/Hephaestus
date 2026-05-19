package de.tum.in.www1.hephaestus.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Slack Bolt app bean.
 *
 * <p>Class-level {@link ConditionalOnProperty} keeps the bean (and the entire configuration class)
 * out of the context when {@code hephaestus.slack.token} is unset — consistent with the gating
 * style used in {@link SentryConfiguration}. Downstream consumers inject the {@link App} via
 * {@code ObjectProvider<App>} and short-circuit when not present.
 */
@Configuration
@ConditionalOnProperty(prefix = "hephaestus.slack", name = "token")
public class SlackAppConfig {

    private final SlackProperties slackProperties;

    public SlackAppConfig(SlackProperties slackProperties) {
        this.slackProperties = slackProperties;
    }

    @Bean
    public App initSlackApp() {
        return new App(
            AppConfig.builder()
                .singleTeamBotToken(slackProperties.token())
                .signingSecret(slackProperties.signingSecret())
                .build()
        );
    }
}
