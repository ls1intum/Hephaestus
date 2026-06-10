package de.tum.cit.aet.hephaestus.agent.runtime;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Inputs for {@link PiRuntimeFactory#build(PiPlanSpec)}. Validation lives in the compact
 * constructor; callers construct positionally.
 *
 * <p><b>baseUrl trap:</b> exported as {@code OPENAI_BASE_URL} / {@code ANTHROPIC_BASE_URL} only in
 * API_KEY modes — PROXY mode resolves its base URL from the sandbox-injected
 * {@code $LLM_PROXY_URL}, so a baseUrl here would be silently shadowed.
 */
public record PiPlanSpec(
    LlmProvider provider,
    CredentialMode credentialMode,
    @Nullable String credential,
    @Nullable String modelName,
    @Nullable String baseUrl,
    @Nullable String jobToken,
    boolean allowInternet,
    int timeoutSeconds,
    PiRunnerProfile runnerProfile,
    Map<String, byte[]> extraInputs,
    String precomputeStep
) {
    public PiPlanSpec {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(credentialMode, "credentialMode");
        Objects.requireNonNull(runnerProfile, "runnerProfile");
        if (runnerProfile.runnerScript() == null || runnerProfile.runnerScript().isBlank()) {
            throw new IllegalArgumentException("runnerProfile.runnerScript() must not be blank");
        }
        if (timeoutSeconds <= PiRuntimeFactory.TIMEOUT_BUFFER_SECONDS) {
            throw new IllegalArgumentException(
                "timeoutSeconds must exceed TIMEOUT_BUFFER_SECONDS=" +
                    PiRuntimeFactory.TIMEOUT_BUFFER_SECONDS +
                    ", got " +
                    timeoutSeconds
            );
        }
        switch (credentialMode) {
            case PROXY -> {
                if (jobToken == null || jobToken.isBlank()) {
                    throw new IllegalArgumentException("jobToken is required in PROXY mode");
                }
            }
            case API_KEY -> {
                if (credential == null || credential.isBlank()) {
                    throw new IllegalArgumentException("credential is required in " + credentialMode + " mode");
                }
            }
        }
        extraInputs = extraInputs != null ? Map.copyOf(extraInputs) : Map.of();
        for (String path : extraInputs.keySet()) {
            boolean ok =
                WorkspaceAbi.allowedExtraInputPaths().contains(path) ||
                WorkspaceAbi.allowedExtraInputPrefixes().stream().anyMatch(path::startsWith);
            if (!ok) {
                throw new IllegalArgumentException(
                    "extraInputs path '" +
                        path +
                        "' is not a recognised workspace path: must appear in " +
                        "WorkspaceAbi.allowedExtraInputPaths() or be prefixed by one of " +
                        WorkspaceAbi.allowedExtraInputPrefixes()
                );
            }
        }
        precomputeStep = precomputeStep != null ? precomputeStep : "";
    }
}
