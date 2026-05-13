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
 * @param provider        LLM provider; required
 * @param credentialMode  PROXY (job-token), API_KEY, or OAUTH; required
 * @param credential      API key in API_KEY/OAUTH modes; must be non-blank in those modes
 * @param modelName       optional model override
 * @param baseUrl         optional OpenAI-compatible base URL override. Exported as
 *                        {@code OPENAI_BASE_URL} / {@code ANTHROPIC_BASE_URL} only in API_KEY/OAUTH
 *                        modes — PROXY mode resolves its base URL from the sandbox-injected
 *                        {@code $LLM_PROXY_URL}, so a baseUrl here would be silently shadowed.
 *                        Production runs leave this {@code null}; live tests use it to point Pi at
 *                        a non-default OpenAI-compatible endpoint (e.g. the TUM gateway).
 * @param jobToken        PROXY mode job token; must be non-blank in PROXY mode
 * @param allowInternet   PROXY mode internet flag; ignored otherwise (always true)
 * @param timeoutSeconds  total sandbox timeout (must be {@code > TIMEOUT_BUFFER_SECONDS})
 * @param runnerScript    filename of the Pi runner under {@code resources/agent/}; required
 * @param extraInputs     additional workspace files keyed by relative path (e.g. {@code task.json})
 * @param precomputeStep  shell fragment ending in {@code " && "} (or empty)
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
    String runnerScript,
    Map<String, byte[]> extraInputs,
    String precomputeStep
) {
    public PiPlanSpec {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(credentialMode, "credentialMode");
        Objects.requireNonNull(runnerScript, "runnerScript");
        if (runnerScript.isBlank()) {
            throw new IllegalArgumentException("runnerScript must not be blank");
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
        precomputeStep = precomputeStep != null ? precomputeStep : "";
    }
}
