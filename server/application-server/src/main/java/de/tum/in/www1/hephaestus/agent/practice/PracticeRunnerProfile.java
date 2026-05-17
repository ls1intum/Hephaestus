package de.tum.in.www1.hephaestus.agent.practice;

import de.tum.in.www1.hephaestus.agent.runtime.PiRunnerProfile;
import java.util.List;
import java.util.Map;

/**
 * Runner profile for the one-shot practice-review agent.
 *
 * <p><b>V8 flags:</b> only {@code --no-warnings}. We do NOT cap the heap because practice
 * routinely parses 30-file diff patches that allocate transiently; a 256 MB cap would convert
 * worst-case-input OOMs from "rare" to "regular." We do NOT {@code --expose-gc} because the
 * practice runner never calls {@code global.gc()} and exposing the global is a foot-gun.
 *
 * <p><b>Per-process env:</b> empty. Practice's bursty allocations don't benefit from jemalloc's
 * page-decay tuning, and forcing {@code LD_PRELOAD} on a short-lived process is unmeasured
 * overhead.
 */
public final class PracticeRunnerProfile implements PiRunnerProfile {

    /** Filename of the practice runner under {@code resources/agent/}. */
    public static final String SCRIPT = "pi-runner.mjs";

    private static final List<String> FLAGS = List.of("--no-warnings");

    @Override
    public String runnerScript() {
        return SCRIPT;
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
