package de.tum.cit.aet.hephaestus.practices.model;

import java.util.Set;

/**
 * The reviewer-audience practices: the practices in the {@code constructive-code-review} area whose
 * finding is filed against the REVIEWER whose comments were assessed, NOT the pull-request author.
 *
 * <p>Every practice here triggers only on {@code ReviewSubmitted} and targets {@code PULL_REQUEST}. A
 * finding for one of these slugs is attributed to the reviewer it is about (resolved server-side from
 * the review authors — the server owns identity, never the model), and it is PERSISTED but never posted
 * back to the author's PR (the reviewer-craft firewall in {@code PullRequestReviewHandler}).
 *
 * <p>A hardcoded slug set — matching the existing precedents {@code METADATA_LEVEL_PRACTICES} and
 * {@code PracticeCatalogInjector.defectDetectorSlugs} — referenced by both the delivery service (which
 * routes attribution) and the handler (which enforces the firewall) so the two can never drift.
 */
public final class ReviewerAudiencePractices {

    private ReviewerAudiencePractices() {}

    /** Slugs whose finding subject is the reviewer, not the PR author. */
    public static final Set<String> REVIEWER_AUDIENCE_SLUGS = Set.of(
        "leaves-useful-specific-review-comments",
        "reviews-respectfully-asks-rather-than-demands",
        "reviews-substantively-with-understanding"
    );

    /** Whether the given practice slug is filed against the reviewer rather than the PR author. */
    public static boolean isReviewerAudience(String practiceSlug) {
        return REVIEWER_AUDIENCE_SLUGS.contains(practiceSlug);
    }
}
