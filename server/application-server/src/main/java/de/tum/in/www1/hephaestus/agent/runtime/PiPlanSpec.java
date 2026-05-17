package de.tum.in.www1.hephaestus.agent.runtime;

import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import java.util.Map;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * Inputs for {@link PiRuntimeFactory#build(PiPlanSpec)}. Validation lives in the compact
 * constructor — there is no builder; callers construct positionally.
 *
 * @param baseUrl         OpenAI-compatible base URL override. Exported as {@code OPENAI_BASE_URL}
 *                        / {@code ANTHROPIC_BASE_URL} only in API_KEY/OAUTH modes — PROXY mode
 *                        resolves its base URL from the sandbox-injected {@code $LLM_PROXY_URL},
 *                        so a baseUrl here would be silently shadowed.
 * @param extraInputs     additional workspace files keyed by relative path. Each key MUST appear
 *                        in {@link WorkspaceAbi#allowedExtraInputPaths()} or be prefixed by one
 *                        of {@link WorkspaceAbi#allowedExtraInputPrefixes()}.
 * @param precomputeStep  shell fragment ending in {@code " && "} (or empty).
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
            case API_KEY, OAUTH -> {
                if (credential == null || credential.isBlank()) {
                    throw new IllegalArgumentException("credential is required in " + credentialMode + " mode");
                }
            }
        }
        extraInputs = extraInputs != null ? Map.copyOf(extraInputs) : Map.of();
        // Fail-fast: adapters writing to undeclared workspace paths surface here at test/boot time
        // instead of silently overwriting a future mount-point.
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
