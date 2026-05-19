package de.tum.cit.aet.hephaestus.agent.runtime;

import java.util.List;
import java.util.Map;

/**
 * Per-runner-kind strategy. Each adapter owns its implementation in the adjacent domain package
 * and passes it to {@link PiRuntimeFactory#build(PiPlanSpec)}. Profiles are pure value objects —
 * they MUST NOT capture spec-level state (provider, credentials, baseUrl).
 */
public interface PiRunnerProfile {
    /** Runner script filename under {@code resources/agent/}. */
    String runnerScript();

    /** V8 flags for the {@code node} invocation. */
    List<String> nodeFlags();

    /** {@code KEY=value} pairs scoped to the {@code node} invocation only — not image-wide ENV. */
    Map<String, String> additionalEnv();
}
