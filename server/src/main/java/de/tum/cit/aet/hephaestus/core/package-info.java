/**
 * Core — cross-cutting infrastructure: logging utilities, exceptions, security primitives,
 * tenancy enforcement (statement inspector, @WorkspaceAgnostic aspect), runtime role
 * configuration, event-bus helpers.
 *
 * <p>Marked {@code Type.OPEN} as a shared kernel. Every feature module may depend on it.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Core (shared kernel)",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package de.tum.cit.aet.hephaestus.core;
