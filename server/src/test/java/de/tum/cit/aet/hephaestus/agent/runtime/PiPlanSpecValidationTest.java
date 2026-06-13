package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeRunnerProfile;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PiPlanSpecValidationTest extends BaseUnitTest {

    private static final PiRunnerProfile PROFILE = new PracticeRunnerProfile();
    private static final byte[] BYTES = new byte[] { 0x01 };

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

    @ParameterizedTest
    @ValueSource(
        strings = {
            "inputs/context/diff.patch", // CONTEXT_PREFIX
            "agent/mentor/system.md", // MENTOR_SYSTEM_PROMPT_PATH
            ".sessions/abc-123.jsonl", // SESSIONS_DIR_PREFIX
        }
    )
    void allowlistedPathsAccepted(String path) {
        assertThatNoException().isThrownBy(() -> specWith(Map.of(path, BYTES)));
    }

    @Test
    void undeclaredPathRejected() {
        assertThatThrownBy(() -> specWith(Map.of("evil/../etc/passwd", BYTES)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("evil/../etc/passwd")
            .hasMessageContaining("allowedExtraInputPaths");
    }

    @Test
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
}
