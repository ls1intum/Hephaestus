package de.tum.cit.aet.hephaestus.workspace.spi;

/** Startup bootstrap operations implemented by the connected integration. */
public interface WorkspaceProvisioning {
    void bootstrapDefaultPatWorkspace();

    void bootstrapDefaultGitLabPatWorkspace();
}
