package de.tum.in.www1.hephaestus.agent.mentor;

import de.tum.in.www1.hephaestus.agent.runtime.PiPlanSpec;
import de.tum.in.www1.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.in.www1.hephaestus.agent.runtime.PiRuntimeFactory.PiPlan;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Mentor adapter: builds an {@link InteractiveSandboxSpec} for a long-lived stdin/stdout JSONL
 * session, symmetric to {@code PracticePiAdapter}'s one-shot {@code task.json} build.
 * Single-flight is enforced by the sandbox registry's {@code (userId, workspaceId)} keying.
 */
@Service
@RequiredArgsConstructor
public class MentorPiAdapter {

    /** Workspace-relative path of the system prompt file the runner loads. */
    public static final String SYSTEM_PROMPT_PATH = "agent/mentor/system.md";

    /** Workspace-relative directory the aspect JSON files land in (matches {@code ContentProvider.OUTPUT_PREFIX}). */
    public static final String ASPECT_INPUT_PREFIX = "context/target/";

    private final PiRuntimeFactory runtimeFactory;
    private final MentorAgentProperties mentorProperties;

    /**
     * Build the interactive sandbox spec for a mentor chat session. The returned spec is keyed
     * by {@code (userId=contributorId, workspaceId)} per {@link InteractiveSandboxSpec}'s
     * contract — concurrent attaches with the same key reuse the live handle.
     *
     * <p>Note: mentor sessions never run in PROXY credential mode — interactive chat doesn't
     * carry a job token. The LLM config's credential mode must resolve to a direct key.
     *
     * @param request       per-turn routing identity (workspace, contributor)
     * @param llmConfig     resolved LLM config — from instance-level properties (primary) or a
     *                      workspace-scoped AgentConfig (fallback); carries provider, credential
     *                      mode, model, timeout
     * @param aspectInputs  pre-built workspace-relative aspect JSON files under
     *                      {@link #ASPECT_INPUT_PREFIX}
     * @return a validated {@link InteractiveSandboxSpec} ready for the sandbox service
     */
    public InteractiveSandboxSpec buildSandboxSpec(
        MentorAgentRequest request,
        MentorLlmConfig llmConfig,
        Map<String, byte[]> aspectInputs
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(llmConfig, "llmConfig");
        Objects.requireNonNull(aspectInputs, "aspectInputs");
        validateAspectInputs(aspectInputs);

        Map<String, byte[]> extraInputs = new LinkedHashMap<>(aspectInputs.size() + 1);
        extraInputs.putAll(aspectInputs);
        extraInputs.put(SYSTEM_PROMPT_PATH, PiRuntimeFactory.loadClasspathResource("mentor/system.md"));

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
            mentorProperties.runnerScript(),
            extraInputs,
            "" /* no precompute step — mentor analytics arrive as aspect JSON */
        );

        PiPlan plan = runtimeFactory.build(planSpec);

        return new InteractiveSandboxSpec(
            UUID.randomUUID(),
            Long.toString(request.contributorId()),
            Long.toString(request.workspaceId()),
            mentorProperties.image(),
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
