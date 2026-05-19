package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeRunnerProfile;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PiPlanSpec validation contract")
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
            "context/target/diff.patch", // CONTEXT_TARGET_PREFIX
            "agent/mentor/system.md", // MENTOR_SYSTEM_PROMPT_PATH
            ".sessions/abc-123.jsonl", // SESSIONS_DIR_PREFIX
        }
    )
    @DisplayName("Allowlisted workspace paths are accepted")
    void allowlistedPathsAccepted(String path) {
        assertThatNoException().isThrownBy(() -> specWith(Map.of(path, BYTES)));
    }

    @Test
    @DisplayName("Undeclared workspace paths are rejected with a message naming the path")
    void undeclaredPathRejected() {
        assertThatThrownBy(() -> specWith(Map.of("evil/../etc/passwd", BYTES)))
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
}
