package de.tum.in.www1.hephaestus.practices.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for practice-aware PR review.
 *
 * <p>Binds to the {@code hephaestus.practice-review} prefix in application configuration.
 * Controls the detection and delivery gate behavior for the practice review agent system.
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * hephaestus:
 *   practice-review:
 *     run-for-all-users: false
 *     skip-drafts: true
 *     max-inline-notes: 5
 *     app-base-url: https://hephaestus.example.com
 * }</pre>
 *
 * @param runForAllUsers whether to run practice review for all PRs (true) or only for users
 *                       with the {@code run_practice_review} Keycloak role (false)
 * @param skipDrafts     whether to skip practice review for draft PRs
 * @param maxInlineNotes maximum number of inline diff comments per review
 * @param appBaseUrl     base URL of the Hephaestus application (for preferences footer link in comments);
 *                       empty string disables the footer link
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.practice-review")
public record PracticeReviewProperties(
    @DefaultValue("false") boolean runForAllUsers,
    @DefaultValue("true") boolean skipDrafts,
    @Min(value = 0, message = "maxInlineNotes must be >= 0")
    @Max(value = 50, message = "maxInlineNotes must be <= 50")
    @DefaultValue("5")
    int maxInlineNotes,
    @DefaultValue("") String appBaseUrl
) {}
