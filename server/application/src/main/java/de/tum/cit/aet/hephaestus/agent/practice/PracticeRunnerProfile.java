package de.tum.cit.aet.hephaestus.agent.practice;

import de.tum.cit.aet.hephaestus.agent.runtime.PiRunnerProfile;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class PracticeRunnerProfile implements PiRunnerProfile {

    public static final String SCRIPT = "pi-runner.ts";

    /** Relative imports require these scripts to share the runner's directory. */
    private static final List<String> SIDECARS = List.of(
            "pi-agent-sandbox.ts",
            "pi-assessment-cache.ts",
            "pi-task-paths.ts",
            "pi-tool-arguments.ts",
            "pi-tool-validation.ts",
            "pi-change.ts",
            "pi-practice-scripts-report.ts",
            "pi-error-text.ts",
            "pi-model-failure.ts",
            "pi-text.ts",
            "pi-result-normalize.ts",
            "pi-practice-coverage.ts",
            "pi-review-brief.ts",
            "pi-folder-index.ts",
            "pi-review-turns.ts",
            "pi-turn-context.ts",
            "pi-runner-output.ts",
            "pi-runner-usage.ts",
            "pi-runner-retry.ts",
            "pi-runner-composition.ts",
            "pi-composition-stage.ts",
            "pi-admitted-results.ts",
            "pi-feedback-preparation.ts",
            "pi-session-lifecycle.ts",
            "pi-session-reads.ts",
            SandboxLayout.PROVIDER_HELPER_FILENAME);

    private static final List<String> PROMPTS = List.of(
            SandboxLayout.FEEDBACK_STYLE_PROMPT_FILENAME,
            SandboxLayout.FEEDBACK_COMPOSER_PROMPT_FILENAME,
            SandboxLayout.REVIEW_COMPOSER_PROMPT_FILENAME);

    @Override
    public String runnerScript() {
        return SCRIPT;
    }

    @Override
    public List<String> sidecarScripts() {
        return SIDECARS;
    }

    @Override
    public List<String> promptResources() {
        return PROMPTS;
    }

    @Override
    public List<String> runtimeFlags() {
        // Subprocesses bypass Node permissions; read-only mounts and container isolation protect evidence.
        return List.of("--max-old-space-size=256");
    }

    @Override
    public Map<String, String> additionalEnv() {
        return Map.of();
    }

    @Override
    public List<String> setupCommands() {
        return Stream.concat(
                        PiRunnerProfile.super.setupCommands().stream(),
                        Stream.of("ln -sfn " + SandboxLayout.PRACTICE_SCRIPT_KITS_TARGET + " "
                                + SandboxLayout.WORKSPACE_ROOT + "/" + SandboxLayout.PRACTICE_SCRIPT_KITS_LINK))
                .toList();
    }
}
