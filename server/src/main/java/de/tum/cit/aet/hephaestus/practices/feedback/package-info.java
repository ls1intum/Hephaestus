/**
 * Delivered-feedback ledger (ADR 0021) — the {@code Feedback} unit, its {@code FeedbackObservation} fusions
 * and {@code FeedbackPlacement} rows, their enums, and the repositories that persist them. The agent
 * delivery layer writes this ledger after posting a review and reads it back to edit a prior summary in
 * place on re-review. Exposed as a named interface so {@code agent} may depend on it, mirroring the
 * sibling {@code finding} / {@code model} / {@code review} interfaces.
 */
@org.springframework.modulith.NamedInterface("feedback")
package de.tum.cit.aet.hephaestus.practices.feedback;
