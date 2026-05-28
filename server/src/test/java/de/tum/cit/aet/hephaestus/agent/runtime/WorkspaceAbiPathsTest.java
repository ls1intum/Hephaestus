package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.architecture.HephaestusArchitectureTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guardrail for the workspace ABI rename ({@code .context/} → {@code context/target/}).
 * Scans the bundled Pi runtime resources (orchestrator + runner) for any remaining legacy
 * references that would slip past the rolling-deploy contract documented in
 * {@code docs/contributor/agent/workspace-abi.mdx}.
 */
@DisplayName("Workspace ABI paths")
class WorkspaceAbiPathsTest extends HephaestusArchitectureTest {

    /** Matches references to the legacy {@code .context/} prefix that are NOT {@code context/target/}. */
    private static final Pattern LEGACY_CONTEXT_PREFIX = Pattern.compile("(?<![A-Za-z0-9_/.])\\.context/");

    @Test
    @DisplayName("agent resources contain no references to the legacy .context/ prefix")
    void agentResourcesAreOnContextTarget() throws IOException {
        Path agentResources = resolveDir("src/main/resources/agent", "server/src/main/resources/agent");
        assertThat(agentResources).isDirectory();

        try (Stream<Path> stream = Files.walk(agentResources)) {
            stream
                .filter(Files::isRegularFile)
                .forEach(p -> {
                    try {
                        String body = Files.readString(p, StandardCharsets.UTF_8);
                        assertThat(LEGACY_CONTEXT_PREFIX.matcher(body).find())
                            .as("Legacy '.context/' prefix found in %s", p)
                            .isFalse();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
    }

    /** Surefire may invoke us from either the module root or the repo root — try both. */
    private static Path resolveDir(String moduleRelative, String repoRelative) {
        Path candidate = Path.of(moduleRelative);
        return Files.isDirectory(candidate) ? candidate : Path.of(repoRelative);
    }
}
