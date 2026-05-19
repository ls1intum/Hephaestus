package de.tum.cit.aet.hephaestus.agent.practice;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * Input for {@link PracticePiAdapter#buildSandboxSpec}. PROXY mode requires {@code jobToken};
 * API_KEY/OAUTH require {@code credential}. The task prompt is carried by the
 * {@code task.json} envelope written by the handler — not by this request.
 *
 * @param baseUrl optional OpenAI-compatible base URL override. {@code null} in production
 *                (the provider URL is intrinsic to the configured LLM provider). Live tests use
 *                it to point Pi at a non-default endpoint such as the TUM AET ASE gateway.
 *                Threaded through to {@link PiPlanSpec}; see {@link LlmProxyAuthShell} for the
 *                env-var semantics (only exported in API_KEY/OAUTH modes).
 */
public record PracticeAgentRequest(
    LlmProvider llmProvider,
    CredentialMode credentialMode,
    @Nullable String modelName,
    @Nullable String credential,
    @Nullable String baseUrl,
    @Nullable String jobToken,
    boolean allowInternet,
    int timeoutSeconds
) {
    public PracticeAgentRequest {
        Objects.requireNonNull(llmProvider, "llmProvider must not be null");
        Objects.requireNonNull(credentialMode, "credentialMode must not be null");
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
