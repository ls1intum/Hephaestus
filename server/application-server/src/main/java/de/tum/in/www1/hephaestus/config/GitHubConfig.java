package de.tum.in.www1.hephaestus.config;

import java.io.IOException;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class GitHubConfig {

    private static final Logger logger = LoggerFactory.getLogger(GitHubConfig.class);

    @Bean
    public GitHub gitHubClient(Environment environment, @Value("${github.auth-token}") String ghAuthToken) {
        if (environment.matchesProfiles("specs", "test")) {
            logger.info("GitHub client is disabled in specs/test profile");
            return GitHub.offline();
        }

        if (ghAuthToken == null || ghAuthToken.isEmpty()) {
            logger.error("GitHub auth token is not provided!");
            throw new IllegalArgumentException("GitHub auth token is required");
        }
        try {
            GitHub github = new GitHubBuilder().withOAuthToken(ghAuthToken).build();
            if (!github.isCredentialValid()) {
                logger.error("Invalid GitHub credentials!");
                throw new IllegalStateException("Invalid GitHub credentials");
            }
            return github;
        } catch (IOException e) {
            logger.error("Failed to initialize GitHub client: {}", e.getMessage());
            throw new RuntimeException("GitHub client initialization failed", e);
        }
    }
}
