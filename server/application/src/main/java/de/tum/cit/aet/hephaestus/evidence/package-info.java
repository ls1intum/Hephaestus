/**
 * Versioned contract for the evidence an automated practice evaluation may observe.
 *
 * <p>The module owns only stable vocabulary and contract lookup. Collection, workspace materialisation,
 * practice configuration, and persistence remain in their respective feature modules.
 */
@ApplicationModule(
        displayName = "Evidence Contract",
        allowedDependencies = {})
@NullMarked
package de.tum.cit.aet.hephaestus.evidence;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
