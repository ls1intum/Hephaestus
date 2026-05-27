/** Outline vendor adapter — webhook + connect + lifecycle. */
@org.springframework.modulith.ApplicationModule(
    displayName = "Integration · Outline",
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
package de.tum.cit.aet.hephaestus.integration.outline;
