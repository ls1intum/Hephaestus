/**
 * Versioned contract for the evidence an automated practice evaluation may observe.
 *
 * <p>The module owns only stable vocabulary and contract lookup. Collection, workspace materialisation,
 * practice configuration, and persistence remain in their respective feature modules.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Evidence Contract",
        allowedDependencies = {})
@org.jspecify.annotations.NullMarked
package de.tum.cit.aet.hephaestus.evidence;
