package de.tum.in.www1.hephaestus.agent.mentor;

import de.tum.in.www1.hephaestus.agent.runtime.PiRunnerProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runner profile for the long-lived mentor chat agent.
 *
 * <p><b>V8 flags:</b>
 * <ul>
 *   <li>{@code --max-old-space-size=256} — cap V8 old-gen at 256 MB. Empirically the mentor
 *       runtime sits at ~100 MB V8 heap; 2.5× headroom defends against a leaky session OOM-ing
 *       the host instead of itself. Default ~1.4 GB on 64-bit lets one bad runner take the host
 *       down.</li>
 *   <li>{@code --no-warnings} — keeps stderr clean for ops grep against our own log prefix.</li>
 *   <li>{@code --expose-gc} — exposes {@code global.gc()} so the runner can force a post-turn
 *       compaction in {@code pi-mentor-runner.mjs:forwardEvent}. The flag costs nothing on its
 *       own and is only effective when {@code global.gc()} is actually called.</li>
 * </ul>
 *
 * <p>Note: {@code --disable-source-maps} was removed in Node 22 (source maps are off by default;
 * the flag itself no longer exists and causes {@code bad option} exit 9). Do not re-add.
 *
 * <p>We deliberately removed {@code --max-semi-space-size=16} and {@code UV_THREADPOOL_SIZE=2}
 * from prior revisions: the former matches the Node 22 default on 64-bit (so it was a no-op),
 * and the latter risks serialising libuv fs/crypto bursts.
 *
 * <p><b>Per-process env:</b> {@code LD_PRELOAD=libjemalloc.so.2} +
 * {@code MALLOC_CONF=background_thread:true,narenas:2,dirty_decay_ms:30000,muzzy_decay_ms:30000}.
 * Mentor's long-lived heap benefits from jemalloc's page-decay tuning; precompute's bursty
 * allocations don't. {@code background_thread:true} runs jemalloc's page-decay sweep on a
 * dedicated thread — without it the mutator must re-enter the allocator to trigger decay, which
 * a long-idle Node loop rarely does (cf. jemalloc TUNING.md). Decay window 30 s matches
 * jemalloc upstream's "long-lived process" recommendation.
 *
 * <p>The path matches the {@code /usr/local/lib/libjemalloc.so.2} symlink created by the Pi
 * Dockerfile ({@code docker/agents/pi/Dockerfile}). The symlink is per-arch by design; the env
 * literal here is arch-independent.
 */
public final class MentorRunnerProfile implements PiRunnerProfile {

    /** Filename of the mentor runner under {@code resources/agent/}. */
    public static final String SCRIPT = "pi-mentor-runner.mjs";

    private static final List<String> FLAGS = List.of("--max-old-space-size=256", "--no-warnings", "--expose-gc");

    private static final Map<String, String> ENV;

    static {
        // LinkedHashMap preserves declaration order — the assembled command line is the same
        // every build, which simplifies cross-build diff inspection.
        LinkedHashMap<String, String> env = new LinkedHashMap<>();
        env.put("LD_PRELOAD", "/usr/local/lib/libjemalloc.so.2");
        env.put("MALLOC_CONF", "background_thread:true,narenas:2,dirty_decay_ms:30000,muzzy_decay_ms:30000");
        ENV = Map.copyOf(env);
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
