package de.tum.cit.aet.hephaestus.agent.practice;

import de.tum.cit.aet.hephaestus.agent.runtime.PiRunnerProfile;
import java.util.List;
import java.util.Map;

/**
 * Runner profile for the one-shot practice-review agent. Conservative tuning: no heap cap (30-file
 * diff patches allocate transiently and 256 MB would convert worst-case OOMs from rare to regular)
 * and no jemalloc LD_PRELOAD (page-decay tuning helps long-lived heaps, not bursty one-shots).
 */
public final class PracticeRunnerProfile implements PiRunnerProfile {

    public static final String SCRIPT = "pi-runner.mjs";

    /**
     * {@code pi-runner.mjs} imports {@code ./pi-finding-normalize.mjs} and the shared
     * {@code ./pi-provider.mjs} provider-registration helper (#1368 slice 5) — stage both beside the
     * runner.
     */
    private static final List<String> SIDECARS = List.of("pi-finding-normalize.mjs", "pi-provider.mjs");

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
    public List<String> nodeFlags() {
        return FLAGS;
    }

    @Override
    public Map<String, String> additionalEnv() {
        return Map.of();
    }
}
