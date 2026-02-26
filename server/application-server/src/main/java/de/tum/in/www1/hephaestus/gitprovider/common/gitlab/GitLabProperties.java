package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for GitLab synchronization operations.
 *
 * <p>Controls various aspects of the GitLab sync behavior including GraphQL operation
 * timeouts, API throttling, and token validation caching.
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * hephaestus:
 *   gitlab:
 *     default-server-url: https://gitlab.com
 *     graphql-timeout: 30s
 *     extended-graphql-timeout: 60s
 *     pagination-throttle: 200ms
 *     token-validation-cache-duration: 5m
 * }</pre>
 *
 * @param defaultServerUrl              default GitLab server URL; used when workspace does not
 *                                      specify a custom server URL (default: https://gitlab.com)
 * @param graphqlTimeout                default timeout for GraphQL operations (default: 30 seconds)
 * @param extendedGraphqlTimeout        extended timeout for complex operations (default: 60 seconds)
 * @param paginationThrottle            delay between pagination requests to avoid exceeding
 *                                      GitLab's rate limits (default: 200 milliseconds)
 * @param tokenValidationCacheDuration  how long to cache token validation results before
 *                                      re-validating against {@code /api/v4/user} (default: 5 minutes)
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.gitlab")
public record GitLabProperties(
    @NotNull @DefaultValue("https://gitlab.com") String defaultServerUrl,
    @NotNull @DurationUnit(SECONDS) @DefaultValue("30s") Duration graphqlTimeout,
    @NotNull @DurationUnit(SECONDS) @DefaultValue("60s") Duration extendedGraphqlTimeout,
    @NotNull @DurationUnit(MILLIS) @DefaultValue("200ms") Duration paginationThrottle,
    @NotNull @DurationUnit(SECONDS) @DefaultValue("5m") Duration tokenValidationCacheDuration
) {}
