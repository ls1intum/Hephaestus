package de.tum.cit.aet.hephaestus.agent.catalog;

/**
 * Which workspaces may use an instance catalog model.
 *
 * <ul>
 *   <li>{@link #PUBLIC} — available to every workspace.</li>
 *   <li>{@link #GRANTED} — available only to workspaces present in {@code llm_model_workspace_grant}.</li>
 * </ul>
 */
public enum ModelVisibility {
    PUBLIC,
    GRANTED,
}
