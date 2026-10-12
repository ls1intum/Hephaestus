package de.tum.cit.aet.hephaestus.agent.handler.spi;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;

/**
 * A submission the handler has verified against the evidence captured for its job, waiting to be
 * recorded as the results of that review once the attempt is confirmed to still own the job.
 */
@FunctionalInterface
public interface PreparedResults {
    /** Records the verified results; called once, inside the admission's ownership fence. */
    void record(AgentJob job);
}
