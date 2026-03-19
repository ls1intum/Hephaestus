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
 * Controls the detection gate behavior for the practice review agent system.
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * hephaestus:
 *   practice-review:
 *     run-for-all-users: false
 *     skip-drafts: true
 *     max-inline-notes: 5
 * }</pre>
 *
 * @param runForAllUsers whether to run practice review for all PRs (true) or only for users
 *                       with the {@code run_practice_review} Keycloak role (false)
 * @param skipDrafts     whether to skip practice review for draft PRs
 * @param maxInlineNotes maximum number of inline diff comments per review
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.practice-review")
public record PracticeReviewProperties(
    @DefaultValue("false") boolean runForAllUsers,
    @DefaultValue("true") boolean skipDrafts,
    @Min(value = 0, message = "maxInlineNotes must be >= 0")
    @Max(value = 50, message = "maxInlineNotes must be <= 50")
    @DefaultValue("5")
    int maxInlineNotes
) {}
