/**
 * In-process {@link de.tum.cit.aet.hephaestus.integration.events.DomainEvent} family +
 * payload records consumed by event handlers via Spring's
 * {@code ApplicationEventPublisher}.
 *
 * <p><b>NOT the CloudEvents wire envelope</b> — that lives in
 * {@code integration/webhook/} ({@code JetStreamPublisher}, {@code PublishRequest}).
 * This package is the JVM-local pub/sub fabric: in-process events that vendor
 * processors raise and integration consumers react to. Nothing here crosses a
 * NATS subject or hits the wire.
 */
@org.springframework.modulith.NamedInterface("events")
package de.tum.cit.aet.hephaestus.integration.events;
