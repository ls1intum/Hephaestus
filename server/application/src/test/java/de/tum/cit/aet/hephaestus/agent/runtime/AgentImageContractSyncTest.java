package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Cross-artifact sync test. The server and the agent image are one runtime contract shipped as two
 * artifacts, and the label is the only place the image states which contract it implements — so a
 * bump on one side that never reaches the other would make every deployed pair look compatible
 * while none of them is. This pins the Dockerfile's literals to {@link SandboxLayout}.
 */
class AgentImageContractSyncTest extends BaseUnitTest {

    private static final Pattern BUN_VERSION_ARG = Pattern.compile("^ARG BUN_VERSION=(\\S+)$", Pattern.MULTILINE);
    private static final Pattern PI_VERSION_ARG = Pattern.compile("^ARG PI_VERSION=(\\S+)$", Pattern.MULTILINE);

    @Test
    void dockerfileStampsTheContractTheServerStagesFor() throws IOException {
        String body = dockerfile();

        assertThat(body)
                .as("agent image labels the runtime contract SandboxLayout stages for")
                .contains("LABEL " + SandboxLayout.RUNTIME_CONTRACT_LABEL + "=" + SandboxLayout.RUNTIME_CONTRACT_VERSION
                        + "\n");

        assertThat(body)
                .as("drift reports can name the interpreter and SDK the image actually carries")
                .contains("LABEL hephaestus.agent.bun-version=${BUN_VERSION}")
                .contains("LABEL hephaestus.agent.pi-version=${PI_VERSION}");
    }

    /**
     * The label assertions above hold for every value of these ARGs, so on their own they let
     * Renovate carry the image onto a new interpreter or SDK major while the contract version — and
     * therefore every drift check that reads it — still claims the old one.
     */
    @Test
    void dockerfileStaysOnTheInterpreterAndSdkMajorsTheRunnersAreWrittenAgainst() throws IOException {
        String body = dockerfile();

        assertThat(majorOf(body, BUN_VERSION_ARG, "BUN_VERSION"))
                .as("docker/agents/pi/Dockerfile moved to a new Bun major: decide whether the staged runners "
                        + "still run on it, then bump SandboxLayout.BUN_MAJOR and RUNTIME_CONTRACT_VERSION together")
                .isEqualTo(SandboxLayout.BUN_MAJOR);

        assertThat(majorOf(body, PI_VERSION_ARG, "PI_VERSION"))
                .as(
                        "docker/agents/pi/Dockerfile moved to a new Pi SDK major: decide whether the runners still "
                                + "import against it, then bump SandboxLayout.PI_SDK_MAJOR and RUNTIME_CONTRACT_VERSION together")
                .isEqualTo(SandboxLayout.PI_SDK_MAJOR);
    }

    private static int majorOf(String body, Pattern arg, String name) {
        Matcher matcher = arg.matcher(body);
        assertThat(matcher.find())
                .as("docker/agents/pi/Dockerfile declares ARG %s", name)
                .isTrue();
        String version = matcher.group(1);
        assertThat(version).as("ARG %s is a dotted version", name).matches("\\d+\\.\\d+.*");
        return Integer.parseInt(version.substring(0, version.indexOf('.')));
    }

    private static String dockerfile() throws IOException {
        Path path = resolveRepoFile("docker/agents/pi/Dockerfile");
        assertThat(path).isRegularFile();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Path resolveRepoFile(String relativePath) {
        Path candidate = Path.of("..", "..").resolve(relativePath);
        return Files.exists(candidate) ? candidate : Path.of(relativePath);
    }
}
