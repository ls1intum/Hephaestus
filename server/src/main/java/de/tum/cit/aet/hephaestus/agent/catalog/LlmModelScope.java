package de.tum.cit.aet.hephaestus.agent.catalog;

/**
 * Which catalog an {@link AvailableLlmModelDTO} entry came from — the discriminator that lets the UI
 * group "Shared models" vs. "Your provider" without re-deriving it from other fields.
 */
public enum LlmModelScope {
    /** From the instance catalog (visible to this workspace: PUBLIC, or explicitly granted). */
    SHARED,
    /** From this workspace's own AI provider connection. */
    WORKSPACE,
}
