package de.tum.cit.aet.hephaestus.agent.catalog;

/**
 * Which workspaces may use an instance catalog model: every workspace, or only those listed in
 * {@code llm_model_workspace_grant}.
 */
public enum ModelVisibility {
    PUBLIC,
    GRANTED,
}
