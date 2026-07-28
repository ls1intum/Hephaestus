package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.workspace.exception.WorkspaceLifecycleViolationException;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeGuard;
import org.springframework.stereotype.Component;

@Component
class AgentJobWorkspacePurgeGuard implements WorkspacePurgeGuard {

    private final AgentJobRepository agentJobRepository;

    AgentJobWorkspacePurgeGuard(AgentJobRepository agentJobRepository) {
        this.agentJobRepository = agentJobRepository;
    }

    @Override
    public void verifyQuiescent(Long workspaceId) {
        if (agentJobRepository.existsPurgeBlockingWork(workspaceId)) {
            throw new WorkspaceLifecycleViolationException(
                "Workspace deletion is blocked while AI runs are queued, running, or awaiting feedback delivery. " +
                    "Cancel queued or running runs, and wait for pending feedback delivery to finish, then try again."
            );
        }
    }
}
