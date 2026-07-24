package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.practices.spi.AgentConfigChecker;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link AgentConfigChecker} backed by the per-purpose binding table (#1368).
 */
@Component
public class AgentConfigCheckerAdapter implements AgentConfigChecker {

    private final WorkspaceAgentBindingRepository bindingRepository;
    private final LlmModelResolver llmModelResolver;

    public AgentConfigCheckerAdapter(
        WorkspaceAgentBindingRepository bindingRepository,
        LlmModelResolver llmModelResolver
    ) {
        this.bindingRepository = bindingRepository;
        this.llmModelResolver = llmModelResolver;
    }

    @Override
    // Keep this boundary non-transactional: resolve() reports revocation with an exception. Catching that
    // exception inside a shared transaction would still mark the transaction rollback-only. That is why
    // the lookup fetches the model + connection graph eagerly — resolving a lazily-loaded binding after
    // the session closed would throw LazyInitializationException instead of answering the question.
    public boolean hasRunnablePractice(Long workspaceId) {
        return bindingRepository
            .findByWorkspaceIdAndPurposeWithModels(workspaceId, AgentPurpose.PRACTICE_DETECTION)
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
