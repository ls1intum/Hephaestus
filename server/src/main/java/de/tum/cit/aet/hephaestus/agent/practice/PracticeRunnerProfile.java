package de.tum.cit.aet.hephaestus.agent.practice;

import de.tum.cit.aet.hephaestus.agent.runtime.PiRunnerProfile;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import java.util.List;
import java.util.Map;

/**
 * Runner profile for the one-shot practice-review agent. Conservative tuning: no heap cap (30-file
 * diff patches allocate transiently and 256 MB would convert worst-case OOMs from rare to regular)
 * and no jemalloc LD_PRELOAD (page-decay tuning helps long-lived heaps, not bursty one-shots).
 */
public final class PracticeRunnerProfile implements PiRunnerProfile {

    public static final String SCRIPT = "pi-runner.mjs";

    /** Imported by {@link #SCRIPT} with a relative specifier, so each must be staged beside it. */
    private static final List<String> SIDECARS = List.of(
        "pi-observation-normalize.mjs",
        "pi-runner-usage.mjs",
        SandboxLayout.PROVIDER_HELPER_FILENAME
    );

    private static final List<String> PROMPTS = List.of(SandboxLayout.FEEDBACK_COMPOSER_PROMPT_FILENAME);

    private static final List<String> FLAGS = List.of("--no-warnings");

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
    public List<String> nodeFlags() {
        return FLAGS;
    }

    @Override
    public Map<String, String> additionalEnv() {
        return Map.of();
    }
}
