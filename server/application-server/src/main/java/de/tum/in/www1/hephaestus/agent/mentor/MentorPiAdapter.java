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

    /** Workspace-relative path of the system prompt file the runner loads. */
    public static final String SYSTEM_PROMPT_PATH = "agent/mentor/system.md";

    /** Workspace-relative directory the aspect JSON files land in (matches {@code ContentProvider.OUTPUT_PREFIX}). */
    public static final String ASPECT_INPUT_PREFIX = "context/target/";

    /**
     * Workspace-relative directory the Pi SDK session JSONL files land in. Matches the
     * {@code SESSIONS_DIR} the runner mkdirs at startup ({@code /workspace/.sessions}) so a
     * verbatim restore lands exactly where the runner's {@code bindThread} → {@code switchSession}
     * will read it from. Keep aligned with {@code pi-mentor-runner.mjs#SESSIONS_DIR}.
     */
    public static final String SESSION_FILE_PREFIX = ".sessions/";

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
     * @param request        per-turn routing identity (workspace, contributor)
     * @param llmConfig      resolved LLM config — from instance-level properties (primary) or a
     *                       workspace-scoped AgentConfig (fallback); carries provider, credential
     *                       mode, model, timeout
     * @param aspectInputs   pre-built workspace-relative aspect JSON files under
     *                       {@link #ASPECT_INPUT_PREFIX}
     * @param sessionRestore optional verbatim Pi SDK session JSONL captured from the prior turn —
     *                       when present, injected into the container at
     *                       {@code .sessions/<threadId>.jsonl} so the runner's
     *                       {@code switchSession} loads byte-identical prior state on first
     *                       {@code open_thread}. {@code null} means "fresh session" (first turn
     *                       of a thread, or no prior bytes captured).
     * @return a validated {@link InteractiveSandboxSpec} ready for the sandbox service
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

        int extraSize = aspectInputs.size() + 1 + (sessionRestore != null ? 1 : 0);
        Map<String, byte[]> extraInputs = new LinkedHashMap<>(extraSize);
        extraInputs.putAll(aspectInputs);
        extraInputs.put(SYSTEM_PROMPT_PATH, PiRuntimeFactory.loadClasspathResource("mentor/system.md"));
        if (sessionRestore != null) {
            extraInputs.put(SESSION_FILE_PREFIX + sessionRestore.threadId() + ".jsonl", sessionRestore.jsonl());
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

    /**
     * Verbatim Pi SDK session JSONL bytes captured from a prior turn of the same thread. Loaded
     * from {@code chat_thread.session_jsonl} by {@code MentorChatService} before
     * {@link #buildSandboxSpec} runs.
     *
     * <p>The {@code threadId} is the AI SDK thread id (same UUID the runner uses for its session
     * file path); the runner's {@code SESSIONS_DIR/{threadId}.jsonl} convention is the contract
     * between Java and the runner. {@code jsonl} is the literal bytes from the runner's last
     * {@code session_persisted} event — Pi prompt-cache compatibility requires byte-identical
     * prefix, so no normalisation or re-encoding.
     */
    public record SessionRestore(UUID threadId, byte[] jsonl) {
        public SessionRestore {
            Objects.requireNonNull(threadId, "threadId");
            Objects.requireNonNull(jsonl, "jsonl");
            if (jsonl.length == 0) {
                throw new IllegalArgumentException("jsonl must be non-empty");
            }
        }
    }
}
