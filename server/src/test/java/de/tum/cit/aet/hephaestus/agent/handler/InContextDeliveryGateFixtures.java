package de.tum.cit.aet.hephaestus.agent.handler;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;

/**
 * Holds the workspace side of {@link InContextDeliveryGate} still, so a handler test turns on practice
 * autonomy and run provenance alone; a test of the rollout fence itself must name its own revision with
 * {@link #workspacesAtRevision}.
 */
final class InContextDeliveryGateFixtures {

    private InContextDeliveryGateFixtures() {}

    static InContextDeliveryGate gate(
        PracticeRepository practices,
        ObservationRepository observations,
        FeedbackLedgerRecorder ledger
    ) {
        return new InContextDeliveryGate(practices, observations, ledger, workspaceDefaults(), workspaces());
    }

    static WorkspaceReviewDefaultsProvider workspaceDefaults() {
        WorkspaceReviewDefaultsProvider provider = mock(WorkspaceReviewDefaultsProvider.class);
        lenient().when(provider.forWorkspace(anyLong())).thenReturn(WorkspaceReviewDefaults.UNSET);
        return provider;
    }

    /** Revision 0 for every workspace: the revision a test {@link AgentJob} is admitted under. */
    static WorkspaceRepository workspaces() {
        return workspacesAtRevision(0L);
    }

    static WorkspaceRepository workspacesAtRevision(long rolloutRevision) {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        lenient()
            .when(repository.findById(anyLong()))
            .thenAnswer(invocation -> {
                Workspace workspace = new Workspace();
                workspace.setId(invocation.getArgument(0));
                workspace.getReviewSettings().setRolloutRevision(rolloutRevision);
                return Optional.of(workspace);
            });
        return repository;
    }

    static WorkspaceRepository noWorkspaces() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        lenient().when(repository.findById(anyLong())).thenReturn(Optional.empty());
        return repository;
    }
}
