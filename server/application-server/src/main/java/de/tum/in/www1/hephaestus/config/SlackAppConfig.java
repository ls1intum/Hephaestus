package de.tum.in.www1.hephaestus.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
