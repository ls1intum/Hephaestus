package de.tum.cit.aet.hephaestus.agent.practice;

import de.tum.cit.aet.hephaestus.agent.runtime.PiRunnerProfile;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import java.util.List;
import java.util.Map;

/**
 * Runner profile for the one-shot practice-review agent. It takes no flags: a review is bursty and
 * short-lived, so the eager collection {@code --smol} buys the mentor would cost throughput here for
 * no benefit, and the process exits before any tuning would pay off.
 */
public final class PracticeRunnerProfile implements PiRunnerProfile {

    public static final String SCRIPT = "pi-runner.ts";

    /** Imported by {@link #SCRIPT} with a relative specifier, so each must be staged beside it. */
    private static final List<String> SIDECARS = List.of(
        "pi-error-text.ts",
        "pi-observation-normalize.ts",
        "pi-runner-usage.ts",
        "pi-runner-timings.ts",
        "pi-runner-composition.ts",
        "pi-runner-fanout.ts",
        SandboxLayout.PROVIDER_HELPER_FILENAME
    );

    private static final List<String> PROMPTS = List.of(SandboxLayout.FEEDBACK_COMPOSER_PROMPT_FILENAME);

    private static final List<String> FLAGS = List.of();

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
        return FLAGS;
    }

    @Override
    public Map<String, String> additionalEnv() {
        return Map.of();
    }
}
