package de.tum.cit.aet.hephaestus.workspace.spi;

/**
 * Blocks workspace purge while a module still has work that can write workspace data.
 */
public interface WorkspacePurgeGuard {
    void verifyQuiescent(Long workspaceId);
}
