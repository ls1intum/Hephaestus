package de.tum.cit.aet.hephaestus.agent.mentor;

import de.tum.cit.aet.hephaestus.agent.runtime.PiRunnerProfile;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runner profile for the long-lived mentor chat agent. Tuning is set for ~100 MB V8 heap floor
 * and predictable page-decay over hours-long sessions.
 *
 * <p>V8 flags: {@code --max-old-space-size=256} caps the heap so a leaky session OOMs itself
 * (default ~1.4 GB lets one bad runner take the host); {@code --expose-gc} backs
 * {@code pi-mentor-runner.mjs}'s post-turn compaction call to {@code global.gc()}.
 *
 * <p>Per-process env preloads jemalloc and tunes page decay because mentor's long-lived heap
 * benefits from background-thread decay sweeps (cf. jemalloc TUNING.md "long-lived process");
 * practice's bursty allocations don't, hence kernel-scoped LD_PRELOAD on the node invocation
 * only. {@code /usr/local/lib/libjemalloc.so.2} is the per-arch symlink the Pi Dockerfile
 * creates so this literal stays arch-independent.
 */
public final class MentorRunnerProfile implements PiRunnerProfile {

    public static final String SCRIPT = "pi-mentor-runner.mjs";

    private static final List<String> FLAGS = List.of("--max-old-space-size=256", "--no-warnings", "--expose-gc");

    // Iteration order is load-bearing: renderNodeEnv emits `KEY=value` pairs in iteration order,
    // so a non-deterministic Map yields a non-deterministic command line across builds.
    private static final Map<String, String> ENV;

    static {
        LinkedHashMap<String, String> env = new LinkedHashMap<>();
        env.put("LD_PRELOAD", "/usr/local/lib/libjemalloc.so.2");
        env.put("MALLOC_CONF", "background_thread:true,narenas:2,dirty_decay_ms:30000,muzzy_decay_ms:30000");
        ENV = Collections.unmodifiableMap(env);
    }

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
        return ENV;
    }
}
