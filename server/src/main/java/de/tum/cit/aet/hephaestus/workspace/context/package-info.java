/**
 * Tenancy context — request-scoped workspace identity ({@code WorkspaceContext},
 * {@code WorkspaceContextHolder}), HTTP filter, scoped-controller meta-annotation,
 * argument resolver. Used everywhere a request needs to know its workspace.
 */
@org.springframework.modulith.NamedInterface("context")
package de.tum.cit.aet.hephaestus.workspace.context;
