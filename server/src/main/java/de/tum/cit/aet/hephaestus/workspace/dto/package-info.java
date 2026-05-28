/**
 * DTOs exposed by the workspace registry surface — shared by both the cross-module HTTP
 * routes (e.g. {@code /workspaces/gitlab/preflight}) and the workspace-side internal
 * services that produce them.
 *
 * <p>Marked as a {@link org.springframework.modulith.NamedInterface} so vendor adapters
 * (today: GitLab preflight controller in {@code integration.scm.gitlab.workspace}) can
 * depend on the DTO shapes without taking a dependency on the workspace module's root.
 */
@org.springframework.modulith.NamedInterface("dto")
package de.tum.cit.aet.hephaestus.workspace.dto;
