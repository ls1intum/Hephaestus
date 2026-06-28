package de.tum.cit.aet.hephaestus.agent.mentor;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import org.jspecify.annotations.Nullable;

/**
 * Slim projection of the LLM fields that {@link MentorPiAdapter} needs to build a sandbox spec.
 * Decouples the mentor module from the JPA {@link AgentConfig} entity.
 *
 * <p>This record deliberately carries NO credential-mode precondition: the API_KEY-needs-credential /
 * PROXY-needs-jobToken invariant is owned by {@code PiPlanSpec}'s compact constructor, which
 * {@link MentorPiAdapter#buildSandboxSpec} feeds these values into and which fails fast at spec build.
 * Keeping the check single-sourced there avoids a second, drift-prone copy here.
 */
public record MentorLlmConfig(
    LlmProvider llmProvider,
    CredentialMode credentialMode,
    @Nullable String llmApiKey,
    @Nullable String modelName,
    @Nullable String llmBaseUrl,
    int timeoutSeconds
) {
    public static MentorLlmConfig fromAgentConfig(AgentConfig config) {
        return new MentorLlmConfig(
            config.getLlmProvider(),
            config.getCredentialMode(),
            config.getLlmApiKey(),
            config.getModelName(),
            config.getLlmBaseUrl(),
            config.getTimeoutSeconds()
        );
    }
}
