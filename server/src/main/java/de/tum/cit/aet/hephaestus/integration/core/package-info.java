/**
 * Vendor-neutral integration core — the cross-cutting traits used by every adapter.
 *
 * <p>Houses the receive-side substrate ({@code webhook/}), the JetStream consumer wiring
 * ({@code consumer/}), the in-process domain-event bus ({@code events/}), the universal SPI
 * contracts ({@code spi/}), the dispatcher registry ({@code handler/}), the connection
 * aggregate ({@code connection/}), the OAuth callback endpoint ({@code oauth/}), the
 * feedback-post tracking aggregate ({@code feedback/}), and the framework bootstrap
 * ({@code framework/}).
 *
 * <p>Deliberately not annotated with {@link org.springframework.modulith.ApplicationModule}:
 * core/ is part of the {@code integration} application module (declared one level up), not a
 * sub-module. The {@code @NamedInterface} annotations on individual {@code core/<x>/}
 * packages (spi, events, oauth, handler, consumer, webhook, …) still register those packages
 * as named interfaces of the parent {@code integration} module — Modulith resolves
 * named-interface ownership by walking up to the nearest {@code @ApplicationModule}.
 *
 * <p>Nothing under {@code core/} may know about a specific {@code IntegrationKind}.
 * Vendor-specific implementations live under {@code integration/<kind>/...}.
 */
package de.tum.cit.aet.hephaestus.integration.core;
