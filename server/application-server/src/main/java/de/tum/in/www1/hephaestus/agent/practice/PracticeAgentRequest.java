package de.tum.in.www1.hephaestus.agent.practice;

import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * Input for {@link PiPracticeAgent#buildSandboxSpec}. PROXY mode requires {@code jobToken};
 * API_KEY/OAUTH require {@code credential}.
 */
public record PracticeAgentRequest(
    LlmProvider llmProvider,
    CredentialMode credentialMode,
    @Nullable String modelName,
    String prompt,
    @Nullable String credential,
    @Nullable String jobToken,
    boolean allowInternet,
    int timeoutSeconds
) {
    public PracticeAgentRequest {
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
