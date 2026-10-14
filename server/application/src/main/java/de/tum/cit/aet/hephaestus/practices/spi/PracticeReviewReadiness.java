package de.tum.cit.aet.hephaestus.practices.spi;

/** The runtime reports whether the workspace's review model can execute now. */
public interface PracticeReviewReadiness {
    boolean hasRunnableAgent(Long workspaceId);
}
