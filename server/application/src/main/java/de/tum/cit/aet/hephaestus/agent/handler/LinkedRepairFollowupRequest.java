package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import java.util.UUID;

/** Discovery stays SYNC; these jobs identify the already authorized event obligation they continue. */
public record LinkedRepairFollowupRequest(
        PullRequestReviewSubmissionRequest request, UUID predecessorJobId, UUID rootJobId)
        implements JobSubmissionRequest {
    public static final String ROOT_JOB = "linked_repair_root_job_id";
    public static final String PREDECESSOR_JOB = "linked_repair_predecessor_job_id";
    public static final String SUCCESSOR_REVISION = "linked_repair_successor_revision";
}
