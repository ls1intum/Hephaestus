package de.tum.cit.aet.hephaestus.practices.model;

/**
 * A unit of software-engineering work a practice can observe and a finding can target — the single
 * discriminator that routes the whole detection pipeline: the trigger gate, the case-context builder,
 * the {@code AgentJobType}/handler, and the delivery surface.
 *
 * <p>Stored UPPER_CASE via {@code @Enumerated(EnumType.STRING)}; the closed set is extended in lockstep
 * with the runtime that can actually materialise that artifact's context.
 */
public enum WorkArtifact {
    /** A pull/merge request — code diff + commits + review thread. */
    PULL_REQUEST,
    /** An issue — title, body, labels, assignees, comment thread, state-transition timeline (no diff). */
    ISSUE,
    /** A chat conversation thread — the human turns of a settled discussion (no diff, no code). */
    CONVERSATION_THREAD;

    /**
     * Whether this artifact carries a diff-anchored inline-comment lane. Only a pull request does: a
     * finding can be posted there as a positional diff note, while every other artifact expands its
     * findings in the summary/thread surface. The delivery pipeline branches on this capability, not on
     * artifact identity, so a new artifact type states its lane here instead of growing another
     * {@code == PULL_REQUEST} check.
     */
    public boolean hasInlineLane() {
        return this == PULL_REQUEST;
    }
}
