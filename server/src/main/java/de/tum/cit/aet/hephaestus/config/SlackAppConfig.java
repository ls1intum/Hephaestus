package de.tum.cit.aet.hephaestus.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// @ConditionalOnProperty(name = "token") matches an empty string — application.yml defaults the
// token to "" when SLACK_BOT_TOKEN is unset, so the bean would always exist. Match only when the
// token is non-blank.
@Configuration
@ConditionalOnExpression("!'${hephaestus.slack.token:}'.isBlank()")
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
