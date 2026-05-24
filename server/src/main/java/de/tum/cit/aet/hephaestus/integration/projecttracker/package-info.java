/**
 * Project-tracker family library — SCAFFOLDED in #1198.
 *
 * <p>Sealed SPI shape ready for the Linear / Jira / Asana epic; no concrete
 * implementations ship in #1198. Holds {@code ProjectTrackerFeedbackChannel},
 * canonical {@code StatusMapping} / {@code PriorityMapping} / {@code WorkflowTransitioner}
 * SPIs, and {@code IssueRef} / {@code ProjectTrackerDomainEvent}.
 */
@org.springframework.modulith.NamedInterface({"api", "events"})
package de.tum.cit.aet.hephaestus.integration.projecttracker;
