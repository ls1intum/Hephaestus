package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelope;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cross-language sync test. Pins the runner's hardcoded {@code task.json}, exit-{@code 42}, and
 * {@code schemaVersion} literals against the {@link WorkspaceAbi} and {@link TaskEnvelope}
 * constants the Java side uses. The Java side itself is single-sourced from {@code WorkspaceAbi};
 * this test catches drift only on the JavaScript runner.
 */
class WorkspaceAbiSyncTest extends BaseUnitTest {

    @Test
    void runnerLiteralsMatchAbi() throws IOException {
        Path runner = resolveResource("agent/pi-runner.mjs");
        assertThat(runner).isRegularFile();
        String body = Files.readString(runner, StandardCharsets.UTF_8);

        assertThat(body)
            .as("runner reads /workspace/task.json")
            .contains("\"" + WorkspaceAbi.WORKSPACE_ROOT + "/" + WorkspaceAbi.TASK_ENVELOPE_FILENAME + "\"");

        assertThat(body)
            .as("runner pins SUPPORTED_SCHEMA_VERSION to the Java SCHEMA_VERSION constant")
            .contains("SUPPORTED_SCHEMA_VERSION = " + TaskEnvelope.SCHEMA_VERSION);

        assertThat(body)
            .as("runner pins ENVELOPE_MISMATCH_EXIT to WorkspaceAbi.EXIT_ENVELOPE_MISMATCH")
            .contains("ENVELOPE_MISMATCH_EXIT = " + WorkspaceAbi.EXIT_ENVELOPE_MISMATCH);

        assertThat(body)
            .as("runner writes its output under WorkspaceAbi.OUTPUT_PATH")
            .contains("\"" + WorkspaceAbi.OUTPUT_PATH + "\"");

        assertThat(body)
            .as("runner reads the practices index under WorkspaceAbi.PRACTICES_PREFIX")
            .contains("/" + WorkspaceAbi.PRACTICES_PREFIX + "index.json");
    }

    @Test
    @DisplayName("pi-orchestrator.md cites the repo mount at WorkspaceAbi.REPO_MOUNT_RELATIVE (prompt↔ABI pin)")
    void orchestratorPromptCitesRepoMountFromAbi() throws IOException {
        // The orchestrator prompt tells the LIVE agent where to read the repo checkout. That path is NOT
        // pinned by the runner sync above, so a rename of the sandbox source region (e.g. worktrees→sources)
        // could silently rot the prompt — shipping CI-green while pointing the agent at a dead path and
        // losing all surrounding-code context on every real SCM review. Pin it to the ABI constant so the
        // next rename must update both in lockstep or this test fails.
        Path orchestrator = resolveResource("agent/pi-orchestrator.md");
        assertThat(orchestrator).isRegularFile();
        String body = Files.readString(orchestrator, StandardCharsets.UTF_8);

        assertThat(body)
            .as("orchestrator prompt references the repo mount at WorkspaceAbi.REPO_MOUNT_RELATIVE")
            .contains(WorkspaceAbi.REPO_MOUNT_RELATIVE);
    }

    @Test
    @DisplayName(
        "pi-mentor-runner.mjs pins MENTOR_SYSTEM_PROMPT_PATH, SESSIONS_DIR_PREFIX, PI_AGENT_DIR, and the envelope exit"
    )
    void mentorRunnerLiteralsMatchAbi() throws IOException {
        Path runner = resolveResource("agent/pi-mentor-runner.mjs");
        assertThat(runner).isRegularFile();
        String body = Files.readString(runner, StandardCharsets.UTF_8);

        assertThat(body)
            .as("mentor runner references WorkspaceAbi.MENTOR_SYSTEM_PROMPT_PATH literally")
            .contains("\"" + WorkspaceAbi.MENTOR_SYSTEM_PROMPT_PATH + "\"");

        assertThat(body)
            .as("mentor runner references WorkspaceAbi.SESSIONS_DIR_PREFIX dir name (.sessions)")
            .contains("/" + WorkspaceAbi.SESSIONS_DIR_PREFIX.replaceFirst("/$", ""));

        assertThat(body)
            .as("mentor runner falls back to WorkspaceAbi.PI_AGENT_DIR when PI_CODING_AGENT_DIR is unset")
            .contains("\"" + WorkspaceAbi.PI_AGENT_DIR + "\"");

        assertThat(body)
            .as("mentor runner pins ENVELOPE_MISMATCH_EXIT to WorkspaceAbi.EXIT_ENVELOPE_MISMATCH")
            .contains("ENVELOPE_MISMATCH_EXIT = " + WorkspaceAbi.EXIT_ENVELOPE_MISMATCH);
    }

    private static Path resolveResource(String relativePath) {
        Path candidate = Path.of("src/main/resources").resolve(relativePath);
        return Files.exists(candidate) ? candidate : Path.of("server/src/main/resources").resolve(relativePath);
    }
}
