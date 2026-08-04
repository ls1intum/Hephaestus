package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeReviewReadiness;
import org.springframework.stereotype.Component;

/** {@link PracticeReviewReadiness} backed by the per-purpose binding table. */
@Component
public class PracticeReviewReadinessAdapter implements PracticeReviewReadiness {

    private final WorkspaceAgentBindingRepository bindingRepository;
    private final LlmModelResolver llmModelResolver;

    public PracticeReviewReadinessAdapter(
        WorkspaceAgentBindingRepository bindingRepository,
        LlmModelResolver llmModelResolver
    ) {
        this.bindingRepository = bindingRepository;
        this.llmModelResolver = llmModelResolver;
    }

    @Override
    // Must stay non-transactional: isModelAvailable swallows the resolver's throw, which inside a
    // shared transaction would leave it rollback-only. Hence the graph-fetching lookup.
    public boolean hasRunnableAgent(Long workspaceId) {
        return bindingRepository
            .findByWorkspaceIdAndPurposeWithModels(workspaceId, AgentPurpose.PRACTICE_REVIEW)
            .filter(WorkspaceAgentBinding::isEnabled)
            .map(this::isModelAvailable)
            .orElse(false);
    }

    private boolean isModelAvailable(WorkspaceAgentBinding binding) {
        try {
            llmModelResolver.resolve(binding);
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}
