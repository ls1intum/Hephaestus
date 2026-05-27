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
 * <p>Promoted in Phase 4 to an explicit OPEN {@link org.springframework.modulith.ApplicationModule}
 * sibling of {@code scm/}, {@code slack/}, {@code outline/}. This is what makes
 * {@code allowedDependencies = "integration::core"} a legal grant on the CLOSED vendor
 * modules — Modulith only resolves named-interface ownership across formally-declared
 * application modules. OPEN means existing {@code @NamedInterface} annotations on sub-
 * packages (spi, events, oauth, handler, consumer, webhook) keep working and the vendor
 * adapters can also reach into a few non-interfaced internals (the connection aggregate,
 * the framework bootstrap) without a wholesale interface inventory we don't need yet.
 *
 * <p>Nothing under {@code core/} may know about a specific {@code IntegrationKind} by
 * name. Vendor-specific implementations live under {@code integration/<kind>/...}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Integration · Core",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package de.tum.cit.aet.hephaestus.integration.core;
