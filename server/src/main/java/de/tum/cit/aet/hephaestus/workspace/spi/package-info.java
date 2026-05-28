/**
 * Workspace lifecycle SPIs. Today contains {@link WorkspacePurgeContributor}: feature
 * modules implement it to delete their workspace-scoped data when a workspace is
 * purged; {@code WorkspaceLifecycleService} invokes the contributors in order.
 *
 * <p>Lifecycle EVENTS (e.g. {@code WorkspaceCreatedEvent}) live next door in
 * {@code workspace.events} — separate named interface so the {@code List<X>} SPI
 * stereotype is not mixed with broadcast event records in a single API surface.
 */
@org.springframework.modulith.NamedInterface("spi")
package de.tum.cit.aet.hephaestus.workspace.spi;
