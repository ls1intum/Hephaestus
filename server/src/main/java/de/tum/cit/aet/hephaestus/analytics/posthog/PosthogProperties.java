package de.tum.cit.aet.hephaestus.analytics.posthog;

import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * PostHog analytics integration properties under {@code hephaestus.posthog}. The {@code enabled}
 * property is read directly by {@code @ConditionalOnProperty} on {@link PosthogClient} — no
 * record component for it, the bean is just absent when the flag is unset.
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
