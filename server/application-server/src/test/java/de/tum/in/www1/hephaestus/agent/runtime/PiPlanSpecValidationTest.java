package de.tum.in.www1.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.practice.PracticeRunnerProfile;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fail-fast validation contract for {@link PiPlanSpec#extraInputs}.
 *
 * <p>Every workspace-relative path an adapter puts in {@code extraInputs} must be either
 * prefixed by a known dynamic-suffix prefix from {@link WorkspaceAbi#allowedExtraInputPrefixes}
 * (e.g. {@link WorkspaceAbi#CONTEXT_TARGET_PREFIX}, {@link WorkspaceAbi#SESSIONS_DIR_PREFIX}),
 * OR equal to one of the static allowlisted paths in {@link WorkspaceAbi#allowedExtraInputPaths}.
 * Adapters caught writing to undeclared paths fail at construction time — surfaced at boot or
 * the first unit test, not at runtime in front of a user.
 */
@DisplayName("PiPlanSpec.extraInputs path validation")
class PiPlanSpecValidationTest extends BaseUnitTest {

    private static final PiRunnerProfile PROFILE = new PracticeRunnerProfile();

    private static PiPlanSpec specWith(Map<String, byte[]> extraInputs) {
        return new PiPlanSpec(
            LlmProvider.OPENAI,
            CredentialMode.API_KEY,
            "sk",
            null,
            null,
            null,
            true,
            600,
            PROFILE,
            extraInputs,
            ""
        );
    }

    @Test
    @DisplayName("CONTEXT_TARGET_PREFIX paths are accepted")
    void contextTargetPathAccepted() {
        Map<String, byte[]> inputs = Map.of(
            WorkspaceAbi.CONTEXT_TARGET_PREFIX + "diff.patch",
            "x".getBytes(StandardCharsets.UTF_8)
        );
        // No throw — happy path.
        PiPlanSpec spec = specWith(inputs);
        assertThat(spec.extraInputs()).containsKey(WorkspaceAbi.CONTEXT_TARGET_PREFIX + "diff.patch");
    }

    @Test
    @DisplayName("Static allowlist (MENTOR_SYSTEM_PROMPT_PATH) accepted")
    void mentorSystemPromptPathAccepted() {
        Map<String, byte[]> inputs = Map.of(
            WorkspaceAbi.MENTOR_SYSTEM_PROMPT_PATH,
            "# prompt".getBytes(StandardCharsets.UTF_8)
        );
        // No throw.
        PiPlanSpec spec = specWith(inputs);
        assertThat(spec.extraInputs()).containsKey(WorkspaceAbi.MENTOR_SYSTEM_PROMPT_PATH);
    }

    @Test
    @DisplayName("Dynamic-suffix prefix (SESSIONS_DIR_PREFIX) accepted for mentor session restores")
    void sessionsPrefixAccepted() {
        Map<String, byte[]> inputs = Map.of(
            WorkspaceAbi.SESSIONS_DIR_PREFIX + "abc-123.jsonl",
            "{}".getBytes(StandardCharsets.UTF_8)
        );
        // No throw.
        specWith(inputs);
    }

    @Test
    @DisplayName("Arbitrary workspace path is REJECTED with a message naming the path")
    void undeclaredPathRejected() {
        Map<String, byte[]> inputs = Map.of("evil/../etc/passwd", "x".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> specWith(inputs))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("evil/../etc/passwd")
            .hasMessageContaining("allowedExtraInputPaths");
    }

    @Test
    @DisplayName("PROXY mode requires jobToken")
    void proxyRequiresToken() {
        assertThatThrownBy(() ->
            new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.PROXY,
                null,
                null,
                null,
                null,
                false,
                600,
                PROFILE,
                Map.of(),
                ""
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("jobToken");
    }

    @Test
    @DisplayName("API_KEY mode requires credential")
    void apiKeyRequiresCredential() {
        assertThatThrownBy(() ->
            new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                null,
                null,
                null,
                null,
                false,
                600,
                PROFILE,
                Map.of(),
                ""
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("credential");
    }

    @Test
    @DisplayName("timeoutSeconds must exceed TIMEOUT_BUFFER_SECONDS")
    void timeoutMustExceedBuffer() {
        assertThatThrownBy(() ->
            new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk",
                null,
                null,
                null,
                true,
                PiRuntimeFactory.TIMEOUT_BUFFER_SECONDS,
                PROFILE,
                Map.of(),
                ""
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("TIMEOUT_BUFFER_SECONDS");
    }

    @Test
    @DisplayName("runnerProfile must not be null")
    void runnerProfileNotNull() {
        assertThatThrownBy(() ->
            new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk",
                null,
                null,
                null,
                true,
                600,
                null,
                Map.of(),
                ""
            )
        )
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("runnerProfile");
    }
}
