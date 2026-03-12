package de.tum.in.www1.hephaestus.agent.job;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentJobService {

    private final AgentJobRepository agentJobRepository;

    @Transactional(readOnly = true)
    public Page<AgentJob> getJobs(Long workspaceId, AgentJobStatus status, Pageable pageable) {
        if (status != null) {
            return agentJobRepository.findByWorkspaceIdAndStatus(workspaceId, status, pageable);
        }
        return agentJobRepository.findByWorkspaceId(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<AgentJob> getJob(Long workspaceId, UUID jobId) {
        return agentJobRepository.findByIdAndWorkspaceId(jobId, workspaceId);
    }
}
