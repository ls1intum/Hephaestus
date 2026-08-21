package de.tum.cit.aet.hephaestus.agent.handler;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;

/**
 * The workspace side of {@link InContextDeliveryGate}, held still: every workspace resolves to the unset
 * review defaults and to the rollout revision its jobs were admitted under, so the gate decides on practice
 * autonomy and run provenance alone.
 */
final class InContextDeliveryGateFixtures {

    private InContextDeliveryGateFixtures() {}

    /** A real gate over the caller's mocks, for the handler tests that need one as a dependency. */
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

    static WorkspaceRepository workspaces() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        lenient()
            .when(repository.findById(anyLong()))
            .thenAnswer(invocation -> {
                Workspace workspace = new Workspace();
                workspace.setId(invocation.getArgument(0));
                return Optional.of(workspace);
            });
        return repository;
    }
}
