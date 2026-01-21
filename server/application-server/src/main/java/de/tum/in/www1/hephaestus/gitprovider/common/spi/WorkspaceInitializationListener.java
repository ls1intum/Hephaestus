package de.tum.in.www1.hephaestus.gitprovider.common.spi;

/**
 * SPI for receiving workspace initialization notifications.
 * <p>
 * This interface enables dependency inversion: the gitprovider module defines
 * what it needs to be notified about, and the workspace module provides the
 * implementation that publishes these notifications.
 * <p>
 * This avoids a direct dependency from gitprovider → workspace while still
 * allowing gitprovider to react when workspaces are initialized.
 *
 * @see de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService
 */
public interface WorkspaceInitializationListener {

    /**
     * Called when workspaces have been initialized and are ready.
     * <p>
     * This callback signals that workspace entities exist in the database
     * and it is safe for installation-level NATS consumers to start
     * processing webhook events.
     *
     * @param workspaceCount number of workspaces that were initialized
     */
    void onWorkspacesInitialized(int workspaceCount);
}
