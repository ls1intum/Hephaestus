package de.tum.in.www1.hephaestus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;

@Configuration
public class SlackAppConfig {
    @Value("${slack.token}")
    private String botToken;

    @Value("${slack.signing-secret}")
    private String signingSecret;

    @Bean
    public App initSlackApp() {
        if (botToken == null || signingSecret == null || botToken.isEmpty() || signingSecret.isEmpty()) {
            return new App();
        }
        return new App(AppConfig.builder().singleTeamBotToken(botToken).signingSecret(signingSecret).build());
    }
}