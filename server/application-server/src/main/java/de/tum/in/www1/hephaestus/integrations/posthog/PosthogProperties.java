package de.tum.in.www1.hephaestus.integrations.posthog;

import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * PostHog analytics integration properties under {@code hephaestus.posthog}.
 *
 * <p>The {@code enabled} property is consumed by {@link PosthogClient}'s
 * {@code @ConditionalOnProperty(name = "enabled", havingValue = "true")} — it does not need a
 * binding here because the bean is absent unless it is set, and {@link PosthogClient}'s
 * constructor enforces the credential requirements when the bean is instantiated.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.posthog")
public record PosthogProperties(
    @Nullable
    @URL(message = "PostHog API host must be a valid URL if provided")
    @DefaultValue("https://app.posthog.com")
    String apiHost,
    @Nullable String projectId,
    @Nullable String personalApiKey
) {}
