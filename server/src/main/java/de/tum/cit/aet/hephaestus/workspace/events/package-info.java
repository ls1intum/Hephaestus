/**
 * Workspace-lifecycle domain events. Published by {@code WorkspaceService} /
 * {@code WorkspaceLifecycleService} and consumed by vendor-adapters and feature
 * modules that bootstrap on workspace creation / activation / purge.
 *
 * <p>Distinct from {@code workspace.spi}: events are immutable broadcast records
 * delivered through {@link org.springframework.context.ApplicationEventPublisher};
 * the SPI named-interface is for in-process service contracts (e.g.
 * {@code WorkspacePurgeContributor}) injected as {@code List<X>}. Mixing them in
 * a single named interface is the stereotype-grouping anti-pattern Spring
 * Modulith documentation calls out.
 */
@org.springframework.modulith.NamedInterface("events")
package de.tum.cit.aet.hephaestus.workspace.events;
