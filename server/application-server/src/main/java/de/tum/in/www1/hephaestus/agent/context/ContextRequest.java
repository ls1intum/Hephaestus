package de.tum.in.www1.hephaestus.agent.context;

import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import java.util.Objects;

/**
 * Polymorphic request handed to {@link WorkspaceContextBuilder}. Providers narrow on the
 * variant via a sealed switch — no {@code instanceof} chains in the orchestrator.
 */
public sealed interface ContextRequest permits ContextRequest.PracticeReviewRequest {
    /** Agent job carrying metadata and workspace. Providers may read but must not mutate. */
    AgentJob job();

    /**
     * Build the materialised PR-review context: metadata, comments, diff, contributor history.
     */
    record PracticeReviewRequest(AgentJob job) implements ContextRequest {
        public PracticeReviewRequest {
            Objects.requireNonNull(job, "job must not be null");
        }
    }
}
