package de.tum.cit.aet.hephaestus.agent.practice;

import de.tum.cit.aet.hephaestus.agent.catalog.ModelKind;
import de.tum.cit.aet.hephaestus.agent.catalog.ReasoningEffort;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Input for {@link PracticePiAdapter#buildSandboxSpec}. Carries no prompt — that travels in the
 * {@code task.json} envelope the handler writes.
 *
 * @param practiceScriptToken the credential of the practice scripts, scoped to the models for practice scripts
 *     only; {@code null} for a job that runs no practice scripts
 * @param practiceScriptModels the models that the practice scripts may call, by kind; empty without a
 *     {@code practiceScriptToken}
 */
public record PracticeAgentRequest(
        String apiProtocol,
        String upstreamModelId,
        @Nullable Integer contextWindow,
        @Nullable Integer maxOutputTokens,
        @Nullable ReasoningEffort reasoningEffort,
        String jobToken,
        int timeoutSeconds,
        @Nullable String practiceScriptToken,
        Map<ModelKind, FrozenModel> practiceScriptModels) {
    public PracticeAgentRequest {
        Objects.requireNonNull(apiProtocol, "apiProtocol must not be null");
        Objects.requireNonNull(upstreamModelId, "upstreamModelId must not be null");
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive, got: " + timeoutSeconds);
        }
        if (jobToken == null || jobToken.isBlank()) {
            throw new IllegalArgumentException("jobToken is required — every sandbox talks to the LLM proxy");
        }
        practiceScriptModels = Map.copyOf(practiceScriptModels);
    }
}
