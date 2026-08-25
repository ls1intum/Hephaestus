/**
 * Workspace lifecycle SPIs. Feature modules provide guards for unsafe deletion and
 * contributors that erase their workspace-scoped data.
 *
 * <p>Lifecycle EVENTS (e.g. {@code WorkspaceCreatedEvent}) live next door in
 * {@code workspace.events} — separate named interface so the {@code List<X>} SPI
 * stereotype is not mixed with broadcast event records in a single API surface.
 */
@org.springframework.modulith.NamedInterface("spi")
@org.jspecify.annotations.NullMarked
package de.tum.cit.aet.hephaestus.workspace.spi;
