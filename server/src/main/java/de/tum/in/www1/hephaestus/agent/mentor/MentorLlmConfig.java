package de.tum.in.www1.hephaestus.agent.mentor;

import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import org.springframework.lang.Nullable;

/**
 * Slim projection of the LLM fields that {@link MentorPiAdapter} needs to build a sandbox spec.
 *
 * <p>Populated from instance-level {@link MentorAgentProperties} (primary path, works without a
 * workspace DB row) or from a workspace-scoped {@link AgentConfig} (fallback for multi-tenant
 * deployments that need per-workspace key routing).
 *
 * <p>{@link AgentConfig} is the practice-review agent's concept; mentor treats it only as a
 * fallback. The canonical local-dev configuration lives in {@code application-local.yml} under
 * {@code hephaestus.mentor.agent.*}.
 */
public record MentorLlmConfig(
    LlmProvider llmProvider,
    CredentialMode credentialMode,
    @Nullable String llmApiKey,
    @Nullable String modelName,
    int timeoutSeconds
) {
    public static MentorLlmConfig fromProperties(MentorAgentProperties props) {
        return new MentorLlmConfig(
            props.llmProvider(),
            props.credentialMode(),
            props.llmApiKey(),
            props.modelName(),
            props.timeoutSeconds()
        );
    }

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
