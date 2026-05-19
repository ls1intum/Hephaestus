package de.tum.in.www1.hephaestus.agent.mentor;

import de.tum.in.www1.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.in.www1.hephaestus.agent.runtime.PiPlanSpec;
import de.tum.in.www1.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.in.www1.hephaestus.agent.runtime.PiRuntimeFactory.PiPlan;
import de.tum.in.www1.hephaestus.agent.runtime.WorkspaceAbi;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Mentor adapter: builds an {@link InteractiveSandboxSpec} for a long-lived stdin/stdout JSONL
 * session, symmetric to {@code PracticePiAdapter}'s one-shot {@code task.json} build.
 * Single-flight is enforced by the sandbox registry's {@code (userId, workspaceId)} keying.
 */
@Service
@RequiredArgsConstructor
public class MentorPiAdapter {

    public static final String SYSTEM_PROMPT_PATH = WorkspaceAbi.MENTOR_SYSTEM_PROMPT_PATH;
    public static final String ASPECT_INPUT_PREFIX = WorkspaceAbi.CONTEXT_TARGET_PREFIX;
    public static final String SESSIONS_DIR_PREFIX = WorkspaceAbi.SESSIONS_DIR_PREFIX;

    private static final MentorRunnerProfile PROFILE = new MentorRunnerProfile();

    private final PiRuntimeFactory runtimeFactory;
    private final MentorAgentProperties mentorProperties;
    private final AgentImageProperties imageProperties;

    /**
     * Build the interactive sandbox spec for a mentor chat session. Sandbox is keyed by
     * {@code (contributorId, workspaceId)}; concurrent attaches reuse the live handle.
     * When {@code sessionRestore} is non-null, the prior turn's JSONL is injected at
     * {@code .sessions/<threadId>.jsonl} so Pi's {@code switchSession} restores byte-identical
     * state for prompt-cache continuity.
     */
    public InteractiveSandboxSpec buildSandboxSpec(
        MentorAgentRequest request,
        MentorLlmConfig llmConfig,
        Map<String, byte[]> aspectInputs,
        @Nullable SessionRestore sessionRestore
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(llmConfig, "llmConfig");
        Objects.requireNonNull(aspectInputs, "aspectInputs");
        validateAspectInputs(aspectInputs);

        Map<String, byte[]> extraInputs = new LinkedHashMap<>(aspectInputs);
        extraInputs.put(SYSTEM_PROMPT_PATH, PiRuntimeFactory.loadClasspathResource("mentor/system.md"));
        if (sessionRestore != null) {
            extraInputs.put(SESSIONS_DIR_PREFIX + sessionRestore.threadId() + ".jsonl", sessionRestore.bytes());
        }

        String baseUrl = mentorProperties.baseUrl().isBlank() ? null : mentorProperties.baseUrl();

        PiPlanSpec planSpec = new PiPlanSpec(
            llmConfig.llmProvider(),
            llmConfig.credentialMode(),
            llmConfig.llmApiKey(),
            llmConfig.modelName(),
            baseUrl,
            null,
            true,
            llmConfig.timeoutSeconds(),
            PROFILE,
            extraInputs,
            ""
        );

        PiPlan plan = runtimeFactory.build(planSpec);

        return new InteractiveSandboxSpec(
            UUID.randomUUID(),
            Long.toString(request.contributorId()),
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

    private static void validateAspectInputs(Map<String, byte[]> aspectInputs) {
        for (String key : aspectInputs.keySet()) {
            if (key == null || !key.startsWith(ASPECT_INPUT_PREFIX)) {
                throw new IllegalArgumentException(
                    "aspectInputs key must begin with '" + ASPECT_INPUT_PREFIX + "', got: " + key
                );
            }
        }
    }
}
