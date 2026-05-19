/**
 * Code-health module — AI-driven practice detection and contributor findings.
 *
 * <p>Listens for {@code DomainEvent}s from {@link de.tum.cit.aet.hephaestus.gitprovider}
 * processors, gates new reviews via {@code PracticeReviewDetectionGate}, dispatches agent
 * jobs through {@link de.tum.cit.aet.hephaestus.agent}, and persists results as
 * {@link de.tum.cit.aet.hephaestus.practices.finding.PracticeFinding}. Contributor feedback
 * lives in the same module.
 *
 * <p>Distinct bounded context from {@link de.tum.cit.aet.hephaestus.activity} (which
 * gamifies developer actions rather than analyzing code quality).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Practices (Code Health)",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package de.tum.cit.aet.hephaestus.practices;
