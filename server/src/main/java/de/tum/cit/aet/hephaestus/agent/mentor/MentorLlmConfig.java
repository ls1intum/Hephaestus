package de.tum.cit.aet.hephaestus.agent.mentor;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import org.jspecify.annotations.Nullable;

/**
 * Slim projection of the LLM fields that {@link MentorPiAdapter} needs to build a sandbox spec.
 * Decouples the mentor module from the JPA {@link AgentConfig} entity.
 */
public record MentorLlmConfig(
    LlmProvider llmProvider,
    CredentialMode credentialMode,
    @Nullable String llmApiKey,
    @Nullable String modelName,
    int timeoutSeconds
) {
    public static MentorLlmConfig fromAgentConfig(AgentConfig config) {
        return new MentorLlmConfig(
            config.getLlmProvider(),
            config.getCredentialMode(),
            config.getLlmApiKey(),
            config.getModelName(),
            config.getTimeoutSeconds()
        );
    }
}
