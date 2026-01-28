package de.tum.in.www1.hephaestus.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackAppConfig {

    private final SlackProperties slackProperties;

    public SlackAppConfig(SlackProperties slackProperties) {
        this.slackProperties = slackProperties;
    }

    @Bean
    public App initSlackApp() {
        if (!slackProperties.isConfigured()) {
            return new App();
        }
        return new App(
            AppConfig.builder()
                .singleTeamBotToken(slackProperties.token())
                .signingSecret(slackProperties.signingSecret())
                .build()
        );
    }
}
