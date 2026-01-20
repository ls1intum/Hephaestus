package de.tum.in.www1.hephaestus.workspace;

/**
 * Event published when workspaces have been initialized (provisioned and loaded from database).
 * <p>
 * This event signals that workspace entities exist in the database and it is safe for
 * installation-level NATS consumers to start processing webhook events.
 * <p>
 * <b>Why this matters:</b> Installation events (like {@code installation.created} or
 * {@code installation_repositories.added}) only need workspaces to exist - they do NOT
 * need the full repository/issue/PR sync to complete first. Starting the installation
 * consumer earlier allows the application to respond to new installations immediately
 * after startup, rather than waiting for a potentially long GraphQL sync.
 * <p>
 * <b>Startup Sequence:</b>
 * <ol>
 *   <li>Workspace provisioning completes (workspaces created/loaded from database)</li>
 *   <li>This event is published</li>
 *   <li>Installation consumer starts (can now handle installation events)</li>
 *   <li>Full GraphQL sync runs (repos, issues, PRs) - in parallel with installation consumer</li>
 * </ol>
 * <p>
 * <b>Usage:</b>
 * <ul>
 *   <li>{@link WorkspaceStartupListener} publishes this event after workspace provisioning</li>
 *   <li>{@link de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService} waits for this
 *       event before starting the installation consumer</li>
 * </ul>
 *
 * @param workspaceCount number of workspaces that were initialized
 */
public record WorkspacesInitializedEvent(int workspaceCount) {}
