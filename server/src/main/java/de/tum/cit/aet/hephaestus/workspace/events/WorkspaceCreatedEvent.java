package de.tum.cit.aet.hephaestus.workspace.events;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;

/**
 * Fired after {@code WorkspaceService.createWorkspaceWithInitialization} commits the
 * new workspace row. Vendor adapters subscribe to kick off their async bootstrap
 * flows (GitLab: group discovery + webhook registration). Subscribers MUST be
 * fire-and-forget — the HTTP response has already been sent.
 */
public record WorkspaceCreatedEvent(long workspaceId, IntegrationKind kind) {}
