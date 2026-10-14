package de.tum.cit.aet.hephaestus.agent.practice;

import de.tum.cit.aet.hephaestus.agent.runtime.PiRunnerProfile;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import java.util.List;
import java.util.Map;

public final class PracticeRunnerProfile implements PiRunnerProfile {

    public static final String SCRIPT = "pi-runner.ts";

    /** Relative imports require these scripts to share the runner's directory. */
    private static final List<String> SIDECARS = List.of(
            "pi-agent-sandbox.ts",
            "pi-assessment-cache.ts",
            "pi-task-paths.ts",
            "pi-tool-arguments.ts",
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
            "pi-feedback-preparation.ts",
            "pi-session-lifecycle.ts",
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

    /**
     * The practice programs import the toolkit as {@code ../lib/…} beside their staged sources, and the runner as
     * {@code ./work/practice-scripts/lib/…}. The toolkit ships in the image; the link that names it is made here,
     * before Node starts, because the runner's first import resolves through it.
     */
    @Override
    public List<String> setupCommands() {
        return List.of(
                "mkdir -p " + SandboxLayout.WORKSPACE_ROOT + "/" + SandboxLayout.PRACTICE_SCRIPT_PREFIX,
                "ln -sfn " + SandboxLayout.PRACTICE_SCRIPT_LIBRARY_TARGET + " " + SandboxLayout.WORKSPACE_ROOT + "/"
                        + SandboxLayout.PRACTICE_SCRIPT_LIBRARY_LINK);
    }
}
