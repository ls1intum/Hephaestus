package de.tum.in.www1.hephaestus.config;

import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for general application settings.
 *
 * <p>Binds to the {@code hephaestus} prefix in application configuration.
 * Contains application-wide settings that don't belong to a specific feature.
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * hephaestus:
 *   host-url: https://hephaestus.example.com
 *   webapp:
 *     url: https://app.example.com
 * }</pre>
 *
 * @param hostUrl the public URL of this Hephaestus instance (used for links in notifications)
 * @param webapp  webapp configuration
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus")
public record ApplicationProperties(
    @Nullable
    @URL(message = "Host URL must be a valid URL if provided")
    @DefaultValue("http://localhost:8080")
    String hostUrl,
    Webapp webapp
) {
    /**
     * Compact constructor ensuring nested records are never null.
     */
    public ApplicationProperties {
        if (webapp == null) {
            webapp = new Webapp("http://localhost:4200");
        }
    }

    /**
     * Webapp configuration.
     *
     * @param url the public URL of the webapp (used for links in emails)
     */
    public record Webapp(
        @Nullable @URL(message = "Webapp URL must be a valid URL if provided") @DefaultValue(
            "http://localhost:4200"
        ) String url
    ) {}
}
