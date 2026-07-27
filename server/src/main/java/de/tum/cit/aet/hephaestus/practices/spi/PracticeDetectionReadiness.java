package de.tum.cit.aet.hephaestus.practices.spi;

/**
 * Whether a workspace can actually run practice detection. An SPI so practices does not depend on
 * agent, which would close the cycle practices → agent → practices.
 */
public interface PracticeDetectionReadiness {
    /**
     * Whether the workspace has an enabled {@code PRACTICE_DETECTION} agent binding whose model is
     * available now — that is, whether a detection job would actually be submitted.
     */
    boolean hasRunnableAgent(Long workspaceId);
}
