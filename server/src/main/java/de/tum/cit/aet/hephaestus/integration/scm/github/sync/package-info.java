/**
 * GitHub sync service entry-points exposed to the workspace provisioning module.
 *
 * <p>Named interface: {@code sync}. {@code WorkspaceProvisioningAdapter} injects
 * {@code GithubDataSyncService} (via {@code ObjectProvider}) to trigger a one-shot sync
 * on first install — pre-warming the workspace before the user lands on it. This is the
 * only cross-module surface from {@code sync/}; everything else (per-event handlers,
 * pagers, processors) stays internal to the GitHub adapter.
 */
@org.springframework.modulith.NamedInterface("sync")
package de.tum.cit.aet.hephaestus.integration.scm.github.sync;
