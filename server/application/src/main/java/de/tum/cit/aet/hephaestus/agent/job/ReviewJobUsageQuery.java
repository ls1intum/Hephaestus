package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.llm.usage.LlmUsageEventRepository;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsageSourceType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ReviewJobUsageQuery {

    private final ReviewJobRepository agentJobRepository;
    private final LlmUsageEventRepository usageEvents;

    @Transactional(readOnly = true)
    public ReviewJobUsageDTO usage(Long workspaceId, UUID jobId) {
        if (agentJobRepository.findByIdAndWorkspaceId(jobId, workspaceId).isEmpty()) {
            throw new EntityNotFoundException("ReviewJob", jobId.toString());
        }
        return ReviewJobUsageDTO.of(
                usageEvents
                        .findByWorkspaceIdAndSourceTypeAndSourceIdOrderBySourceAttemptAsc(
                                workspaceId, UsageSourceType.REVIEW_JOB, jobId)
                        .stream()
                        .map(ReviewJobUsageAttemptDTO::from)
                        .toList(),
                usageEvents.findByWorkspaceIdAndSourceTypeInAndSourceIdOrderBySourceAttemptAsc(
                        workspaceId, UsageSourceType.PRACTICE_SCRIPT, jobId));
    }
}
