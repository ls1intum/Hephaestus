/**
 * In-process {@link de.tum.cit.aet.hephaestus.integration.events.DomainEvent} family +
 * payload records published via Spring's {@code ApplicationEventPublisher}.
 * JVM-local pub/sub only — nothing here crosses a NATS subject. Wire-level
 * messages are raw bytes plus headers; see {@code integration/webhook/PublishRequest}.
 */
@org.springframework.modulith.NamedInterface("events")
package de.tum.cit.aet.hephaestus.integration.events;
