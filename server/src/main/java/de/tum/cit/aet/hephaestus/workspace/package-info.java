/**
 * Workspace module — multi-tenancy root: workspace lifecycle, membership, context filter,
 * scoped HTTP routing, gitprovider SPI adapters.
 *
 * <p>The aggregate roots ({@code Workspace}, {@code WorkspaceMembership}) and the membership
 * service live in the implicit root API. Sub-packages expose narrow {@link
 * org.springframework.modulith.NamedInterface} contracts: {@code context} (tenancy
 * ThreadLocal + scoped controllers), {@code authorization} (method-security annotations),
 * {@code settings} (per-team overrides), {@code spi} (workspace-lifecycle inversion points).
 *
 * <p>Lifecycle events consumed cross-module live in {@code core.event} (e.g.,
 * {@code WorkspacesInitializedEvent}) — placing them in either feature module would form a
 * cycle with {@code gitprovider} (workspace adapters already implement gitprovider SPIs).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Workspace (Tenancy)")
package de.tum.cit.aet.hephaestus.workspace;
