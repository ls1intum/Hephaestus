/**
 * GitHub lifecycle entry-points exposed to the workspace provisioning module.
 *
 * <p>Named interface: {@code lifecycle}. The {@code workspace} module's
 * {@code WorkspaceProvisioningService} + {@code WorkspaceProvisioningAdapter} call into
 * {@code GithubLifecycleListener} for installation create/update/suspend/delete and repo-
 * selection changes — that coupling is real (workspace owns the Workspace aggregate;
 * GitHub owns the install state and the NATS consumer lifecycle), so we expose it
 * explicitly rather than pretending it doesn't exist. The CLOSED nature of
 * {@code integration.scm.github} still blocks reaches into {@code sync/}, {@code common/},
 * {@code app/} etc — only this surface is legal cross-module.
 */
@org.springframework.modulith.NamedInterface("lifecycle")
package de.tum.cit.aet.hephaestus.integration.scm.github.lifecycle;
