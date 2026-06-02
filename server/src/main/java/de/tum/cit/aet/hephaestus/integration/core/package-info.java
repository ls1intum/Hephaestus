/**
 * Vendor-neutral integration core — substrate every vendor adapter consumes.
 *
 * <p>Houses {@code webhook/}, {@code consumer/}, {@code events/}, {@code spi/},
 * {@code handler/}, {@code connection/}, {@code oauth/}, {@code feedback/}, and
 * {@code framework/}. OPEN so vendor adapters can reach non-interfaced internals
 * (connection aggregate, framework bootstrap) without a wholesale named-interface
 * inventory. Nothing here may know an {@code IntegrationKind} by name.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Integration · Core",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package de.tum.cit.aet.hephaestus.integration.core;
