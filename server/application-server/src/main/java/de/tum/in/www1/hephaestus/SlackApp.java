package de.tum.in.www1.hephaestus;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.methods.SlackApiException;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlackApp {
    private App app;

    @Bean
    public void initSlackApp() {
        // app = new App();
        app = new App(AppConfig.builder()
                .clientId(System.getenv("SLACK_CLIENT_ID"))
                .clientSecret(System.getenv("SLACK_CLIENT_SECRET"))
                .scope(System.getenv("SLACK_SCOPES"))
                .signingSecret(System.getenv("SLACK_SIGNING_SECRET"))
                .appPath("/my-first-function")
                .oauthInstallPath("/install")
                .oauthRedirectUriPath("/oauth_redirect")
                .build()).asOAuthApp(true);
        app.command("/leaderboard", (req, ctx) -> {
            return ctx.ack("An up-to-date leaderboard can be found at https://hephaestus.ase.cit.tum.de/");
        });
    }

    // at 9 AM every Tuesday
    @Scheduled(cron = "0 0 9 ? * TUE")
    public void scheduledLeaderboardUpdate() throws IOException, SlackApiException {
        app.client().chatPostMessage(r -> r
                .channel("#artemisdev")
                .text("Hello, world!"));
    }
}