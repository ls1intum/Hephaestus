package de.tum.cit.aet.hephaestus.practices.model;

/**
 * The artifact a practice targets — the single discriminator that routes the whole detection
 * pipeline: the trigger gate, the case-context builder, the {@code AgentJobType}/handler, and the
 * delivery surface. A practice with {@code focus_artifact=PULL_REQUEST} observes a code diff and
 * delivers in-PR; {@code ISSUE} observes the issue body/thread/timeline and delivers as an issue
 * comment. PR was historically the only value.
 *
 * <p>Stored UPPER_CASE via {@code @Enumerated(EnumType.STRING)}; closed set extended in lockstep
 * with the runtime that can actually build that artifact's context.
 */
public enum FocusArtifact {
    /** A pull/merge request — code diff + commits + review thread. */
    PULL_REQUEST,
    /** An issue — title, body, labels, assignees, comment thread, state-transition timeline (no diff). */
    ISSUE,
}
