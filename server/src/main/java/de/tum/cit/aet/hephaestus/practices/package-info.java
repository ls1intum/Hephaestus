/**
 * Code-health module — AI-driven practice detection and contributor findings.
 *
 * <p>Listens for {@code DomainEvent}s from {@link de.tum.cit.aet.hephaestus.gitprovider}
 * processors, gates new reviews via {@code PracticeReviewDetectionGate}, dispatches agent
 * jobs through {@link de.tum.cit.aet.hephaestus.agent}, and persists results as
 * {@link de.tum.cit.aet.hephaestus.practices.finding.PracticeFinding}. Contributor
 * feedback lives in the same module.
 *
 * <p>Sub-packages expose narrow APIs via {@link org.springframework.modulith.NamedInterface}:
 * {@code model}, {@code spi}, {@code review}, {@code finding}. Internal types (controllers,
 * adapters, request DTOs) remain module-private.
 *
 * <p>Distinct bounded context from {@link de.tum.cit.aet.hephaestus.activity} (which
 * gamifies developer actions rather than analyzing code quality).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Practices (Code Health)")
package de.tum.cit.aet.hephaestus.practices;
