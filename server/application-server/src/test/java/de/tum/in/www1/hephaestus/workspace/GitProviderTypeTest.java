package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("unit")
@DisplayName("GitProviderType Unit Tests")
class GitProviderTypeTest {

    @Test
    @DisplayName("Should have exactly GITHUB and GITLAB values")
    void shouldHaveExactValues() {
        assertThat(GitProviderType.values()).containsExactlyInAnyOrder(GitProviderType.GITHUB, GitProviderType.GITLAB);
    }

    @Test
    @DisplayName("Should default to GITHUB when mode is null")
    void shouldDefaultToGitHubWhenModeIsNull() {
        Workspace workspace = new Workspace();
        workspace.setGitProviderMode(null);
        assertThat(workspace.getProviderType()).isEqualTo(GitProviderType.GITHUB);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("modeToProviderTypeMapping")
    @DisplayName("Should derive correct provider type from mode")
    void shouldDeriveCorrectProviderType(Workspace.GitProviderMode mode, GitProviderType expectedType) {
        Workspace workspace = new Workspace();
        workspace.setGitProviderMode(mode);
        assertThat(workspace.getProviderType()).isEqualTo(expectedType);
    }

    static Stream<Arguments> modeToProviderTypeMapping() {
        return Stream.of(
            Arguments.of(Workspace.GitProviderMode.PAT_ORG, GitProviderType.GITHUB),
            Arguments.of(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION, GitProviderType.GITHUB),
            Arguments.of(Workspace.GitProviderMode.GITLAB_PAT, GitProviderType.GITLAB)
        );
    }

    @ParameterizedTest
    @EnumSource(Workspace.GitProviderMode.class)
    @DisplayName("Should handle every GitProviderMode value without error")
    void shouldHandleEveryModeValue(Workspace.GitProviderMode mode) {
        Workspace workspace = new Workspace();
        workspace.setGitProviderMode(mode);
        GitProviderType result = workspace.getProviderType();
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Workspace.getProviderType() should derive from git provider mode")
    void workspaceGetProviderTypeShouldDelegate() {
        Workspace workspace = new Workspace();

        // Default mode is PAT_ORG
        assertThat(workspace.getProviderType()).isEqualTo(GitProviderType.GITHUB);

        workspace.setGitProviderMode(Workspace.GitProviderMode.GITLAB_PAT);
        assertThat(workspace.getProviderType()).isEqualTo(GitProviderType.GITLAB);

        workspace.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        assertThat(workspace.getProviderType()).isEqualTo(GitProviderType.GITHUB);
    }
}
