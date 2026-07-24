package de.tum.cit.aet.hephaestus.agent.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Inputs for {@link PiRuntimeFactory#build(PiPlanSpec)}.
 *
 * <p>#1368 slice 5 — ONE credential path: every Pi sandbox talks to the in-app LLM proxy over
 * {@code $LLM_PROXY_URL}/{@code $LLM_PROXY_TOKEN} and NEVER holds a real provider API key.
 * {@code jobToken} is therefore always required — it is the job-scoped bearer credential the proxy
 * resolves server-side (see {@code LlmProxyController}), bounded by the job's timeout and revoked on
 * completion. The task prompt is carried by the {@code task.json} envelope written by the handler —
 * not by this request.
 *
 * @param apiProtocol Pi's own {@code api} token (e.g. {@code openai-completions}), passed through
 *     verbatim into the {@code hephaestus} provider registration — see {@code pi-provider.mjs}.
 * @param upstreamModelId the model id the sandbox requests; also {@code settings.json}'s
 *     {@code defaultModel}.
 * @param contextWindow optional capability hint written into {@code pi-provider.json}.
 * @param maxOutputTokens optional capability hint written into {@code pi-provider.json}.
 */
public record PiPlanSpec(
    String apiProtocol,
    String upstreamModelId,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens,
    boolean supportsReasoning,
    String jobToken,
    boolean allowInternet,
    int timeoutSeconds,
    PiRunnerProfile runnerProfile,
    Map<String, byte[]> extraInputs,
    String precomputeStep
) {
    public PiPlanSpec {
        if (apiProtocol == null || apiProtocol.isBlank()) {
            throw new IllegalArgumentException("apiProtocol must not be blank");
        }
        if (upstreamModelId == null || upstreamModelId.isBlank()) {
            throw new IllegalArgumentException("upstreamModelId must not be blank");
        }
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
        if (jobToken == null || jobToken.isBlank()) {
            throw new IllegalArgumentException("jobToken is required — every sandbox talks to the LLM proxy");
        }
        // Map.copyOf freezes the MAP, but byte[] values stay caller-mutable shared references — a caller could
        // mutate file contents after validation passed. Clone each value too so the record is genuinely
        // immutable (the keySet allowlist check below then runs over the defensive copy).
        extraInputs =
            extraInputs != null
                ? extraInputs
                      .entrySet()
                      .stream()
                      .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().clone()))
                : Map.of();
        for (String path : extraInputs.keySet()) {
            boolean ok =
                SandboxLayout.allowedExtraInputPaths().contains(path) ||
                SandboxLayout.allowedExtraInputPrefixes().stream().anyMatch(path::startsWith);
            if (!ok) {
                throw new IllegalArgumentException(
                    "extraInputs path '" +
                        path +
                        "' is not a recognised workspace path: must appear in " +
                        "SandboxLayout.allowedExtraInputPaths() or be prefixed by one of " +
                        SandboxLayout.allowedExtraInputPrefixes()
                );
            }
        }
        precomputeStep = precomputeStep != null ? precomputeStep : "";
    }
}
