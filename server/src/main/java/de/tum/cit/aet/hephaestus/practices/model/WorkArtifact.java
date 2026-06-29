package de.tum.cit.aet.hephaestus.practices.model;

/**
 * A unit of software-engineering work a practice can observe and a finding can target — the single
 * discriminator that routes the whole detection pipeline: the trigger gate, the case-context builder,
 * the {@code AgentJobType}/handler, and the delivery surface.
 *
 * <p>Named for the <em>work</em> being reviewed, not for any one tool: the set covers
 * {@link #PULL_REQUEST} (code diff + commits + review thread, delivered in-PR) and {@link #ISSUE}
 * (title, body, labels, thread, timeline — no diff), and extends to other modeling surfaces
 * (a design doc / wiki page) and communication surfaces (a chat message / thread) once a runtime can
 * build that artifact's context — hence not the over-broad "Artifact" (which also reads as a build
 * artifact) nor a vendor-specific name.
 *
 * <p>Stored UPPER_CASE via {@code @Enumerated(EnumType.STRING)}; the closed set is extended in lockstep
 * with the runtime that can actually materialise that artifact's context.
 */
public enum WorkArtifact {
    /** A pull/merge request — code diff + commits + review thread. */
    PULL_REQUEST,
    /** An issue — title, body, labels, assignees, comment thread, state-transition timeline (no diff). */
    ISSUE,
}
