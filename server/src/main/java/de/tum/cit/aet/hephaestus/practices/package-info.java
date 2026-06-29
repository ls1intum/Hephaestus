/**
 * Code-health module — AI-driven practice detection and developer findings.
 *
 * <p>Owns the review gate ({@code review.PracticeReviewDetectionGate}) and persists results as
 * {@link de.tum.cit.aet.hephaestus.practices.model.Observation}. This module has no outbound
 * dependency on {@link de.tum.cit.aet.hephaestus.agent}: it is the {@code agent} orchestrator that
 * subscribes to {@code ScmDomainEvent}s, consults the gate here, dispatches the agent job, and
 * writes findings back through this module's named interfaces. Developer feedback lives in the
 * same module.
 *
 * <p>Sub-packages expose narrow APIs via {@link org.springframework.modulith.NamedInterface}:
 * {@code model}, {@code spi}, {@code review}, {@code observation}, {@code feedback}, and
 * {@code observation.reaction}. The latter two let the {@code agent} delivery layer write the
 * delivered-feedback ledger and read reactions for re-nag suppression; the {@code observation.reaction}
 * boundary is also pinned reaction-blind for the detection context by {@code DetectionReactionFirewallTest}
 * (ADR 0021 F-9). Internal types (controllers, adapters, request DTOs) remain module-private.
 *
 * <p>Distinct bounded context from {@link de.tum.cit.aet.hephaestus.activity} (which
 * gamifies developer actions rather than analyzing code quality).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Practices (Code Health)")
package de.tum.cit.aet.hephaestus.practices;
