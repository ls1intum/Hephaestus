package de.tum.cit.aet.hephaestus.practices.model;

/**
 * Whose conduct a {@link Practice} evaluates — the contribution author, or the reviewer of that
 * contribution (ADR 0021, C2).
 *
 * <p>Audience role is the firewall that keeps reviewer-side observations from leaking to the author and
 * vice versa. It drives the {@code subject_user_id} a finding is filed against and the
 * {@code recipient_user_id}/{@code suppression_reason=REVIEWER_SIDE} that {@code Feedback} delivery
 * uses, so a "your review missed the unhandled error" lesson reaches the reviewer and never the PR
 * author:
 *
 * <ul>
 *   <li>{@code AUTHOR} — judges the person who produced the artifact (wrote the PR, opened the issue).
 *       The subject is the developer; this is the overwhelming majority of catalogued practices.</li>
 *   <li>{@code REVIEWER} — judges the person who reviewed the artifact (review thoroughness, tone,
 *       actionable suggestions). The subject is the reviewer, distinct from the developer.</li>
 * </ul>
 *
 * <p>Stored UPPER_CASE via {@code @Enumerated(EnumType.STRING)}; defaults to {@code AUTHOR} — every
 * catalogued practice today is author-side, so the column is behaviour-preserving with zero backfill.
 */
public enum SubjectRole {
    /** The contribution author (subject == developer). */
    AUTHOR,
    /** The reviewer of the contribution (subject == reviewer, distinct from the developer). */
    REVIEWER,
}
