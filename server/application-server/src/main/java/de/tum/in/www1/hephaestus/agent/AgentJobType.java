package de.tum.in.www1.hephaestus.agent;

/**
 * Discriminator for {@code AgentJob} that dispatches to the appropriate {@code JobTypeHandler}.
 *
 * <p>Each value corresponds to a handler implementation that knows how to prepare the Docker
 * volume, parse output, and deliver results. Add new values as new handler types are built.
 */
public enum AgentJobType {
    PULL_REQUEST_REVIEW,
}
