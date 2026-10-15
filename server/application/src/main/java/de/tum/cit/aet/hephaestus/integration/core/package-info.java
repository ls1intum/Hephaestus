/**
 * Shared integration lifecycle, events and item contracts. Provider adapters consume this module;
 * its code cannot depend on provider implementations.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Integration · Core",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
@org.jspecify.annotations.NullMarked
package de.tum.cit.aet.hephaestus.integration.core;
