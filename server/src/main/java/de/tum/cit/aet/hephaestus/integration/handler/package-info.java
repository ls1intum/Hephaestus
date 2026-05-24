/**
 * Unified message-handler registry.
 *
 * <p>One {@link de.tum.cit.aet.hephaestus.integration.spi.EventTypeKey} → one
 * {@link de.tum.cit.aet.hephaestus.integration.handler.IntegrationMessageHandler}, across
 * every {@link de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind}. Vendor-specific
 * handlers live under {@code integration/<kind>/handler/...} and are auto-wired into the
 * registry via Spring DI.
 *
 * <p>This package only ships the contract and the registry. The dispatcher that maps NATS
 * subjects to keys lives in {@code integration/consumer/}.
 */
@org.springframework.modulith.NamedInterface("handler")
package de.tum.cit.aet.hephaestus.integration.handler;
