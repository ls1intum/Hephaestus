/**
 * Cross-vendor wire envelope.
 *
 * <p>Houses the CloudEvents 1.0.2 envelope and codec registry. Per-vendor
 * {@code DomainEvent} hierarchies live in the vendor or family-lib packages
 * (e.g. {@code integration/scm-lib/events/GitDomainEvent}); only the wire-format
 * envelope crosses module boundaries here.
 */
@org.springframework.modulith.NamedInterface("events")
package de.tum.cit.aet.hephaestus.integration.events;
