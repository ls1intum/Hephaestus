package de.tum.cit.aet.hephaestus.agent.mentor;

import de.tum.cit.aet.hephaestus.agent.context.providers.mentor.MentorContextKeys;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.cit.aet.hephaestus.agent.runtime.PiPlanSpec;
import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory.PiPlan;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SecurityProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Mentor adapter: builds an {@link InteractiveSandboxSpec} for a long-lived stdin/stdout JSONL
 * session, symmetric to {@code PracticePiAdapter}'s one-shot {@code task.json} build.
 * Single-flight is enforced by the sandbox registry's {@code (userId, workspaceId)} keying, where the
 * mentee's {@code developerId} is carried in the spec's {@code userId} slot.
 */
@Service
@RequiredArgsConstructor
public class MentorPiAdapter {

    public static final String SYSTEM_PROMPT_PATH = SandboxLayout.MENTOR_SYSTEM_PROMPT_PATH;
    public static final String CONTEXT_INPUT_PREFIX = SandboxLayout.CONTEXT_PREFIX;
    public static final String SESSIONS_DIR_PREFIX = SandboxLayout.SESSIONS_DIR_PREFIX;

    private static final MentorRunnerProfile PROFILE = new MentorRunnerProfile();

    private final PiRuntimeFactory runtimeFactory;
    private final MentorAgentProperties mentorProperties;
    private final AgentImageProperties imageProperties;

    /**
     * Build the interactive sandbox spec for a mentor chat session. Sandbox is keyed by
     * {@code (developerId, workspaceId)}; concurrent attaches reuse the live handle.
     * When {@code sessionRestore} is non-null, the prior turn's JSONL is injected at
     * {@code .sessions/<threadId>.jsonl} so Pi's {@code switchSession} restores byte-identical
     * state for prompt-cache continuity.
     */
    public InteractiveSandboxSpec buildSandboxSpec(
        MentorAgentRequest request,
        MentorLlmConfig llmConfig,
        Map<String, byte[]> contextInputs,
        @Nullable SessionRestore sessionRestore
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(llmConfig, "llmConfig");
        Objects.requireNonNull(contextInputs, "contextInputs");
        validateContextInputs(contextInputs);

        Map<String, byte[]> extraInputs = new LinkedHashMap<>(contextInputs);
        extraInputs.put(SYSTEM_PROMPT_PATH, PiRuntimeFactory.loadClasspathResource("mentor/system.md"));
        if (sessionRestore != null) {
            extraInputs.put(SESSIONS_DIR_PREFIX + sessionRestore.threadId() + ".jsonl", sessionRestore.bytes());
        }

        // Honor the bound mentor config's base URL first (per-workspace LLM gateway, e.g. a TUM GPU
        // endpoint that activates the hephaestus provider); fall back to the global mentor property.
        String baseUrl;
        if (llmConfig.llmBaseUrl() != null && !llmConfig.llmBaseUrl().isBlank()) {
            baseUrl = llmConfig.llmBaseUrl();
        } else {
            baseUrl = mentorProperties.baseUrl().isBlank() ? null : mentorProperties.baseUrl();
        }

        // The config API floor (@Min(30) on AgentConfig timeoutSeconds) sits below PiPlanSpec's runtime
        // floor (must exceed TIMEOUT_BUFFER_SECONDS=60), so a legitimately persisted 30-60s config would
        // otherwise throw from PiPlanSpec and surface as an ERROR on the chat stream. Clamp up to the
        // minimum buildable budget so any valid config always yields a mentor sandbox.
        int timeoutSeconds = Math.max(llmConfig.timeoutSeconds(), PiRuntimeFactory.TIMEOUT_BUFFER_SECONDS + 1);

        PiPlanSpec planSpec = new PiPlanSpec(
            llmConfig.llmProvider(),
            llmConfig.credentialMode(),
            llmConfig.llmApiKey(),
            llmConfig.modelName(),
            baseUrl,
            null,
            true,
            timeoutSeconds,
            PROFILE,
            extraInputs,
            ""
        );

        PiPlan plan = runtimeFactory.build(planSpec);

        return new InteractiveSandboxSpec(
            UUID.randomUUID(),
            Long.toString(request.developerId()),
            Long.toString(request.workspaceId()),
            imageProperties.reference(),
            plan.command(),
            plan.environment(),
            plan.networkPolicy(),
            ResourceLimits.DEFAULT,
            SecurityProfile.DEFAULT,
            plan.inputFiles(),
            Map.of()
        );
    }

    private static void validateContextInputs(Map<String, byte[]> contextInputs) {
        for (Map.Entry<String, byte[]> entry : contextInputs.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(CONTEXT_INPUT_PREFIX)) {
                throw new IllegalArgumentException(
                    "contextInputs key must begin with '" + CONTEXT_INPUT_PREFIX + "', got: " + key
                );
            }
            if (!MentorContextKeys.ALLOWED_OUTPUT_KEYS.contains(key)) {
                throw new IllegalArgumentException("unsupported mentor context input key: " + key);
            }
            // Reject null bytes here so the failure names the offending key, rather than surfacing as an
            // opaque NPE deep inside PiPlanSpec's Map.copyOf(extraInputs), which rejects null values.
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("contextInputs value for '" + key + "' must not be null");
            }
        }
    }
}
