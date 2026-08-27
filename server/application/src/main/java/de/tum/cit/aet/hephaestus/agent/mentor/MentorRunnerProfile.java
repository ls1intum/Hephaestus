package de.tum.cit.aet.hephaestus.agent.mentor;

import de.tum.cit.aet.hephaestus.agent.runtime.PiRunnerProfile;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import java.util.List;
import java.util.Map;

/**
 * Runner profile for the long-lived mentor chat agent, whose heap lives for hours rather than for
 * one review.
 *
 * <p>{@code --smol} is Bun's reduced-memory mode: it collects more eagerly, which suits a session
 * that idles between turns. {@code --expose-gc} publishes {@code global.gc}, which
 * {@code pi-mentor-runner.ts} calls after a turn once the heap passes its watermark.
 *
 * <p>The heap is bounded by the sandbox's memory limit, not by a per-process ceiling.
 */
public final class MentorRunnerProfile implements PiRunnerProfile {

    public static final String SCRIPT = "pi-mentor-runner.ts";

    /**
     * {@code pi-provider.ts} is shared with the practice runner; {@code pi-mentor-protocol.ts}
     * declares the JSON-RPC contract this runner speaks with {@code MentorRunnerClient}. Both are
     * imported with relative specifiers, so both must be staged beside pi-mentor-runner.ts.
     */
    private static final List<String> SIDECARS =
            List.of("pi-error-text.ts", SandboxLayout.PROVIDER_HELPER_FILENAME, "pi-mentor-protocol.ts");

    private static final List<String> FLAGS = List.of("--smol", "--expose-gc");

    @Override
    public String runnerScript() {
        return SCRIPT;
    }

    @Override
    public List<String> sidecarScripts() {
        return SIDECARS;
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
