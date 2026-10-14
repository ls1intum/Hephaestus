package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedResults;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmissionRequestDTO;

/** The runtime boundary for the installed item source and pinned practice assignments. */
public interface ReviewExecutionHandler {
    PreparedJobInputs prepareInputs(ReviewJob job);

    PreparedResults prepareResults(ReviewJob job, AdmissionRequestDTO submission);
}
