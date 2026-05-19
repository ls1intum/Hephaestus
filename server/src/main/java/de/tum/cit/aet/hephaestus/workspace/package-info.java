/**
 * Workspace module — multi-tenancy root: workspace lifecycle, membership, context filter,
 * scoped HTTP routing, gitprovider SPI adapters.
 *
 * <p>{@code workspace.context} is the cross-cutting tenancy machinery (ThreadLocal context
 * holder, request filter, scoped controller meta-annotation). Feature modules consume it
 * to bind operations to a workspace.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Workspace (Tenancy / shared kernel)",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package de.tum.cit.aet.hephaestus.workspace;
