package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Shared blocklist used by both the sync and interactive adapters. Owning a single source of
 * truth was the C1 security fix from the PE audit (issue #1069 review) — the interactive
 * adapter used to bypass these checks entirely.
 */
@DisplayName("SandboxEnvBlocklist")
class SandboxEnvBlocklistTest extends BaseUnitTest {

    @ParameterizedTest(name = "blocks exact: {0}")
    @ValueSource(
        strings = {
            "LD_PRELOAD",
            "LD_LIBRARY_PATH",
            "PATH",
            "NODE_OPTIONS", // matters specifically for the Node mentor runner
            "GIT_SSH_COMMAND",
            "GIT_PAGER",
            "HTTPS_PROXY",
            "http_proxy",
        }
    )
    void blocksExactByName(String name) {
        assertThat(SandboxEnvBlocklist.isBlocked(name)).isTrue();
    }

    @ParameterizedTest(name = "blocks prefix: {0}")
    @ValueSource(
        strings = { "AWS_ROLE_ARN", "GOOGLE_CLOUD_PROJECT", "AZURE_CLIENT_SECRET", "DOCKER_HOST", "GIT_CONFIG_KEY_0" }
    )
    void blocksByPrefix(String name) {
        assertThat(SandboxEnvBlocklist.isBlocked(name)).isTrue();
    }

    @ParameterizedTest(name = "permits prefix exception: {0}")
    @ValueSource(strings = { "AZURE_OPENAI_DEPLOYMENT_NAME_MAP", "AZURE_OPENAI_BASE_URL", "AZURE_OPENAI_API_VERSION" })
    void allowsPrefixExceptions(String name) {
        assertThat(SandboxEnvBlocklist.isBlocked(name)).isFalse();
    }

    @ParameterizedTest(name = "permits unrelated: {0}")
    @ValueSource(strings = { "MY_APP_KEY", "FOO", "PI_AGENT_BUDGET_MS", "HOME" })
    void allowsUnrelated(String name) {
        assertThat(SandboxEnvBlocklist.isBlocked(name)).isFalse();
    }

    @ParameterizedTest(name = "case-insensitive: {0}")
    @ValueSource(
        strings = { "ld_preload", "Node_Options", "aws_access_key_id", "Google_Cloud_Project", "git_config_key_0" }
    )
    void caseInsensitive(String name) {
        assertThat(SandboxEnvBlocklist.isBlocked(name)).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("null is not blocked (caller's responsibility to validate)")
    void nullIsNotBlocked() {
        assertThat(SandboxEnvBlocklist.isBlocked(null)).isFalse();
    }
}
