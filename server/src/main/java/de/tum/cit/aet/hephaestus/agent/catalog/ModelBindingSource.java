package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.workspace.Workspace;
import org.jspecify.annotations.Nullable;

/**
 * Something that binds a workspace to exactly one catalog model plus an enabled flag (#1368) — the
 * shape {@link LlmModelResolver} and {@code LlmAdmissionService} need to resolve and admit a model,
 * independent of whether it comes from a named {@code AgentConfig} or a per-purpose
 * {@code WorkspaceAgentBinding}. Exactly one of the two model getters is non-null for a usable
 * binding.
 */
public interface ModelBindingSource {
    Long getId();

    @Nullable
    LlmModel getInstanceModel();

    @Nullable
    WorkspaceLlmModel getWorkspaceModel();

    Workspace getWorkspace();

    boolean isEnabled();

    boolean isAllowInternet();

    int getTimeoutSeconds();

    int getMaxConcurrentJobs();
}
