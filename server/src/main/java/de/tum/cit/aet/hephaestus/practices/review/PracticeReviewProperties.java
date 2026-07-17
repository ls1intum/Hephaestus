package de.tum.cit.aet.hephaestus.practices.review;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for practice-aware PR review.
 *
 * <p>Binds to the {@code hephaestus.practice-review} prefix in application configuration.
 *
 * @param runForAllUsers      whether to run practice review for all PRs (true) or only for users
 *                            with the {@code run_practice_review} feature flag (false)
 * @param skipDrafts          whether to skip practice review for draft PRs
 * @param deliverToMerged     whether to deliver feedback to already-merged PRs
 * @param appBaseUrl          base URL of the Hephaestus application (for preferences footer link);
 *                            empty string disables the footer link
 * @param cooldownMinutes     minimum minutes between reviews for the same PR. 0 disables cooldown.
 * @param progressFooter      append the cross-run progress-delta footer (B1/B3) and post the re-review
 *                            notifying reply (A4). Off by default; needs ≥2 runs on a target to render.
 * @param reactionSuppression drop re-nagging a locus the student already DISPUTED / marked NOT_APPLICABLE
 *                            (B2). Off by default; inert until a reaction exists for a recurring locus.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.practice-review")
public record PracticeReviewProperties(
    @DefaultValue("false") boolean runForAllUsers,
    @DefaultValue("true") boolean skipDrafts,
    @DefaultValue("false") boolean deliverToMerged,
    @Pattern(regexp = "^$|^https?://.*", message = "appBaseUrl must be empty or a valid http(s) URL")
    @DefaultValue("")
    String appBaseUrl,
    @Min(0) @DefaultValue("15") int cooldownMinutes,
    @DefaultValue("false") boolean progressFooter,
    @DefaultValue("false") boolean reactionSuppression
) {}
