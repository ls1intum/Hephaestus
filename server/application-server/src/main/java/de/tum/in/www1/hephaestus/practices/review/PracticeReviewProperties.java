package de.tum.in.www1.hephaestus.practices.review;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for practice-aware PR review.
 *
 * <p>Binds to the {@code hephaestus.practice-review} prefix in application configuration.
 *
 * @param runForAllUsers  whether to run practice review for all PRs (true) or only for users
 *                        with the {@code run_practice_review} Keycloak role (false)
 * @param skipDrafts      whether to skip practice review for draft PRs
 * @param deliverToMerged whether to deliver feedback to already-merged PRs
 * @param appBaseUrl      base URL of the Hephaestus application (for preferences footer link);
 *                        empty string disables the footer link
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.practice-review")
public record PracticeReviewProperties(
    @DefaultValue("false") boolean runForAllUsers,
    @DefaultValue("true") boolean skipDrafts,
    @DefaultValue("false") boolean deliverToMerged,
    @Pattern(regexp = "^$|^https?://.*", message = "appBaseUrl must be empty or a valid http(s) URL")
    @DefaultValue("")
    String appBaseUrl
) {}
