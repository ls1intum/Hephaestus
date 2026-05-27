/**
 * Slack vendor adapter — webhook + connect + lifecycle.
 *
 * <p>CLOSED module: feature modules outside the integration namespace MUST NOT reach
 * into Slack internals. Cross-module callers go through the SPI ({@code integration::core}).
 * The {@code allowedDependencies} list below is the empirically-derived dependency
 * footprint and is checked at build time by {@code ModulithVerificationTest}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Integration · Slack",
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    allowedDependencies = {
        "integration.core",
        "integration.core::events",
        "integration.core::spi",
        "integration.core::handler",
        "integration.core::oauth",
        "integration.core::consumer",
        "integration.core::webhook",
    }
)
package de.tum.cit.aet.hephaestus.integration.slack;
