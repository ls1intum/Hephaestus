package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageEventRepository;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What one job of a workspace spent on the model, read from the usage ledger. */
@Service
@RequiredArgsConstructor
class AgentJobUsageQuery {

    private final AgentJobRepository agentJobRepository;
    private final LlmUsageEventRepository usageEvents;

    @Transactional(readOnly = true)
    public AgentJobUsageDTO usage(Long workspaceId, UUID jobId) {
        if (agentJobRepository.findByIdAndWorkspaceId(jobId, workspaceId).isEmpty()) {
            throw new EntityNotFoundException("AgentJob", jobId.toString());
        }
        return AgentJobUsageDTO.of(usageEvents
                .findByWorkspaceIdAndSourceTypeAndSourceIdOrderBySourceAttemptAsc(
                        workspaceId, LlmUsageSourceType.AGENT_JOB, jobId)
                .stream()
                .map(AgentJobUsageAttemptDTO::from)
                .toList());
    }
}
