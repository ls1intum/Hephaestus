package de.tum.in.www1.hephaestus.integrations.posthog;

import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for PostHog analytics integration.
 *
 * <p>Binds to the {@code hephaestus.posthog} prefix in application configuration.
 * Controls whether PostHog analytics are enabled and connection details.
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * hephaestus:
 *   posthog:
 *     enabled: true
 *     api-host: https://app.posthog.com
 *     project-id: 12345
 *     personal-api-key: phx_xxx
 * }</pre>
 *
 * @param enabled        whether PostHog integration is enabled
 * @param apiHost        PostHog API host URL
 * @param projectId      PostHog project ID
 * @param personalApiKey PostHog personal API key for server-side operations
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.posthog")
public record PosthogProperties(
    @DefaultValue("false") boolean enabled,
    @Nullable
    @URL(message = "PostHog API host must be a valid URL if provided")
    @DefaultValue("https://app.posthog.com")
    String apiHost,
    @Nullable String projectId,
    @Nullable String personalApiKey
) {}
