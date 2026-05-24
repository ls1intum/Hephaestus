/**
 * NATS consumer wiring for the unified integration framework.
 *
 * <p>Routes JetStream messages from per-kind subject namespaces ({@code github.*},
 * {@code gitlab.*}, {@code slack.*}, {@code outline.*}) to the right
 * {@link de.tum.cit.aet.hephaestus.integration.handler.IntegrationMessageHandler} via the
 * vendor-agnostic registry. Subject parsing is delegated to the per-kind
 * {@link de.tum.cit.aet.hephaestus.integration.spi.SubjectParser} implementations.
 */
@org.springframework.modulith.NamedInterface("consumer")
package de.tum.cit.aet.hephaestus.integration.consumer;
