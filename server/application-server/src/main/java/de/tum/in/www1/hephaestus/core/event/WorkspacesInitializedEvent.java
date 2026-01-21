package de.tum.in.www1.hephaestus.core.event;

/**
 * Event published when workspaces have been initialized (provisioned and loaded from database).
 * <p>
 * This event is in the core package to avoid cyclic dependencies between workspace and gitprovider modules.
 * The workspace module publishes this event, and the gitprovider module can listen to it.
 * <p>
 * This event signals that workspace entities exist in the database and it is safe for
 * installation-level NATS consumers to start processing webhook events.
 *
 * @param workspaceCount number of workspaces that were initialized
 */
public record WorkspacesInitializedEvent(int workspaceCount) {}
