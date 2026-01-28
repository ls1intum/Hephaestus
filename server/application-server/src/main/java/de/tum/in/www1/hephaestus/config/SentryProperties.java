package de.tum.in.www1.hephaestus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Sentry error tracking integration.
 *
 * <p>Consolidates Sentry configuration under the {@code hephaestus.sentry} prefix.
 * When the DSN is not provided or blank, Sentry integration is disabled.
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * hephaestus:
 *   sentry:
 *     dsn: ${SENTRY_DSN}
 * }</pre>
 *
 * @param dsn Sentry Data Source Name for error reporting; leave blank to disable
 * @see <a href="https://docs.sentry.io/platforms/java/configuration/">Sentry Java Configuration</a>
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.sentry")
public record SentryProperties(@Nullable String dsn) {
    /**
     * Checks if Sentry is configured with a valid DSN.
     *
     * @return {@code true} if DSN is provided and non-blank
     */
    public boolean isConfigured() {
        return dsn != null && !dsn.isBlank();
    }
}
