package de.tum.cit.aet.hephaestus.agent.practice;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmApiProtocol;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelKind;
import de.tum.cit.aet.hephaestus.agent.catalog.ReasoningEffort;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult;
import de.tum.cit.aet.hephaestus.agent.runtime.PiPlanSpec;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PracticePiAdapter {

    private static final PracticeRunnerProfile PROFILE = new PracticeRunnerProfile();

    private final PiRuntimeFactory runtimeFactory;
    private final PiResultParser resultParser;
    private final AgentImageProperties imageProperties;
    private final PracticeReviewProperties reviewProperties;
    private final ObjectMapper objectMapper;

    /** The runner reads it and registers it as the model's sampling temperature. */
    static final String SAMPLING_TEMPERATURE_ENV = "LLM_SAMPLING_TEMPERATURE";

    /** The runner bounds each turn by these, per practice it carries: pi-runner.ts PER_PRACTICE_WORK. */
    static final String PRACTICE_MODEL_CALLS_ENV = "PI_PRACTICE_MODEL_CALLS";

    static final String PRACTICE_OUTPUT_TOKENS_ENV = "PI_PRACTICE_OUTPUT_TOKENS";

    static final String PRACTICE_SCRIPT_TOKEN_ENV = "PRACTICE_SCRIPT_PROXY_TOKEN";

    /** The longest the practice script stage takes, whatever the job's timeout. */
    public static final int MAX_PRACTICE_SCRIPT_SECONDS = 90;

    /**
     * The shortest job timeout. It leaves the runner more than its shutdown buffer even after a practice script
     * stage of {@link #MAX_PRACTICE_SCRIPT_SECONDS}.
     */
    public static final int MIN_TIMEOUT_SECONDS =
            MAX_PRACTICE_SCRIPT_SECONDS + PiRuntimeFactory.TIMEOUT_BUFFER_SECONDS + 1;

    public PracticeSandboxSpec buildSandboxSpec(PracticeAgentRequest request) {
        int practiceScriptSeconds = practiceScriptBudgetSeconds(request.timeoutSeconds());
        String practiceScriptToken = request.practiceScriptToken();
        Map<String, BoundModel> practiceScriptModels = boundModels(request.practiceScriptModels());
        Map<String, byte[]> extraInputs = practiceScriptModels.isEmpty()
                ? Map.of()
                : Map.of(SandboxLayout.PRACTICE_SCRIPT_MODELS_FILE, toJson(practiceScriptModels));
        PiRuntimeFactory.PiPlan plan = runtimeFactory.build(new PiPlanSpec(
                request.apiProtocol(),
                request.upstreamModelId(),
                request.contextWindow(),
                request.maxOutputTokens(),
                request.reasoningEffort(),
                request.jobToken(),
                // Native shell access must not provide a route to push or fetch uncaptured evidence.
                false,
                // The runner starts after the practice script stage, so that stage's budget is not the runner's.
                request.timeoutSeconds() - practiceScriptSeconds,
                PROFILE,
                extraInputs,
                buildPracticeScriptStep(practiceScriptSeconds, reviewProperties.practiceScriptMaxTokensPerAttempt())));
        Map<String, String> environment = new LinkedHashMap<>(plan.environment());
        environment.put(PRACTICE_MODEL_CALLS_ENV, Integer.toString(reviewProperties.practiceModelCalls()));
        environment.put(PRACTICE_OUTPUT_TOKENS_ENV, Integer.toString(reviewProperties.practiceOutputTokens()));
        Double temperature = reviewProperties.samplingTemperature();
        if (temperature != null) {
            environment.put(SAMPLING_TEMPERATURE_ENV, temperature.toString());
        }
        if (practiceScriptToken != null) {
            environment.put(PRACTICE_SCRIPT_TOKEN_ENV, practiceScriptToken);
        }
        return new PracticeSandboxSpec(
                imageProperties.reference(),
                plan.command(),
                Map.copyOf(environment),
                plan.inputFiles(),
                SandboxLayout.OUTPUT_PATH,
                null,
                plan.networkPolicy(),
                plan.promptDigest());
    }

    public AgentResult parseResult(SandboxResult sandboxResult) {
        return resultParser.parse(sandboxResult);
    }

    /** One entry of {@link SandboxLayout#PRACTICE_SCRIPT_MODELS_FILE}: the practice script runner's {@code boundModelSchema}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BoundModel(
            String protocol, String modelId, @Nullable ReasoningEffort reasoningEffort) {}

    /**
     * The models for practice scripts file, by slot. The runner rejects the whole file for one unknown protocol,
     * and a pre-catalog snapshot can hold one, so a model on such a protocol has no entry.
     */
    private static Map<String, BoundModel> boundModels(Map<ModelKind, FrozenModel> models) {
        Map<String, BoundModel> bound = new LinkedHashMap<>();
        models.forEach((kind, model) -> {
            if (LlmApiProtocol.parse(model.apiProtocol()).isPresent()) {
                bound.put(
                        kind.slot(),
                        new BoundModel(model.apiProtocol(), model.upstreamModelId(), model.reasoningEffort()));
            }
        });
        return bound;
    }

    private byte[] toJson(Map<String, BoundModel> models) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(models);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize " + SandboxLayout.PRACTICE_SCRIPT_MODELS_FILE, e);
        }
    }

    /** One tenth of the job's timeout, from 1 second to {@link #MAX_PRACTICE_SCRIPT_SECONDS}. */
    static int practiceScriptBudgetSeconds(int timeoutSeconds) {
        return Math.min(MAX_PRACTICE_SCRIPT_SECONDS, Math.max(1, timeoutSeconds / 10));
    }

    /**
     * What runs before the model: the change view is derived from the checkout (a failure there is the
     * review's failure, since nothing could be reviewed), then the practices' practice scripts, which
     * only add hints and may fail quietly. Only the practice script stage may call the models for practice scripts, so
     * the review runner starts without their token. The practice script runner holds its scripts to the
     * proxy's token cap for the attempt.
     */
    private static String buildPracticeScriptStep(int budgetSeconds, long maxTokens) {
        return "node /workspace/pi-change.ts && sh /workspace/pi-practice-scripts.sh " + budgetSeconds + " " + maxTokens
                + " && unset " + PRACTICE_SCRIPT_TOKEN_ENV + " && ";
    }
}
