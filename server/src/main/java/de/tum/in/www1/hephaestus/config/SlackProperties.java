package de.tum.in.www1.hephaestus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Slack integration.
 *
 * <p>Consolidates all Slack-related configuration under the {@code hephaestus.slack}
 * prefix. Both properties are optional; if not provided, Slack integration is disabled.
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * hephaestus:
 *   slack:
 *     token: ${SLACK_BOT_TOKEN}
 *     signing-secret: ${SLACK_SIGNING_SECRET}
 * }</pre>
 *
 * @param token         Slack bot token for API access (starts with xoxb-)
 * @param signingSecret Slack signing secret for webhook verification
 * @see <a href="https://api.slack.com/authentication/token-types">Slack Token Types</a>
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.slack")
public record SlackProperties(@Nullable String token, @Nullable String signingSecret) {
    /**
     * Checks if Slack integration is properly configured.
     *
     * @return {@code true} if both token and signing secret are provided and non-blank
     */
    public boolean isConfigured() {
        return token != null && !token.isBlank() && signingSecret != null && !signingSecret.isBlank();
    }
}
