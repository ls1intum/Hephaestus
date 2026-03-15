package de.tum.in.www1.hephaestus.agent.adapter.spi;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * Input for {@link AgentAdapter#buildSandboxSpec}.
 *
 * <p>All fields come from the job orchestrator. The adapter uses them to configure the
 * agent-specific Docker image, command, environment variables, and input files.
 *
 * <p>Authentication depends on {@link #credentialMode()}:
 * <ul>
 *   <li>{@link CredentialMode#PROXY} — {@code jobToken} required; container uses internal proxy</li>
 *   <li>{@link CredentialMode#API_KEY} — {@code credential} required; direct API key auth</li>
 *   <li>{@link CredentialMode#OAUTH} — {@code credential} required; direct OAuth token auth</li>
 * </ul>
 *
 * @param agentType      the agent runtime
 * @param llmProvider    the LLM provider
 * @param credentialMode authentication mode
 * @param modelName      model name (nullable — uses agent default if absent)
 * @param prompt         the full prompt text
 * @param credential     API key or OAuth token for direct modes (null in PROXY mode)
 * @param jobToken       proxy job token (null in direct modes)
 * @param allowInternet  whether the container may reach the public internet
 * @param timeoutSeconds job timeout
 */
public record AgentAdapterRequest(
    AgentType agentType,
    LlmProvider llmProvider,
    CredentialMode credentialMode,
    @Nullable String modelName,
    String prompt,
    @Nullable String credential,
    @Nullable String jobToken,
    boolean allowInternet,
    int timeoutSeconds
) {
    public AgentAdapterRequest {
        Objects.requireNonNull(agentType, "agentType must not be null");
        Objects.requireNonNull(llmProvider, "llmProvider must not be null");
        Objects.requireNonNull(credentialMode, "credentialMode must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive, got: " + timeoutSeconds);
        }
        switch (credentialMode) {
            case PROXY -> {
                if (jobToken == null || jobToken.isBlank()) {
                    throw new IllegalArgumentException("jobToken is required in PROXY mode");
                }
            }
            case API_KEY, OAUTH -> {
                if (credential == null || credential.isBlank()) {
                    throw new IllegalArgumentException("credential is required in " + credentialMode + " mode");
                }
            }
        }
    }
}
