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

    /**
     * Sibling ES-module files the runner imports relatively (e.g. {@code ./pi-observation-normalize.ts}).
     * Each is staged at the workspace root next to the runner so the import resolves. Empty by default.
     */
    default List<String> sidecarScripts() {
        return List.of();
    }

    /**
     * Prompt files this runner reads from the workspace root by name (e.g. the composition stage's
     * instructions). Staged like the sidecars, and digested with the rest of the prompt scaffolding —
     * they ARE prompt content, so a run's recorded prompt version must move when they do. Empty by
     * default.
     */
    default List<String> promptResources() {
        return List.of();
    }

    /** Flags for the {@code bun} invocation. */
    List<String> runtimeFlags();

    /** {@code KEY=value} pairs scoped to the {@code bun} invocation only — not image-wide ENV. */
    Map<String, String> additionalEnv();
}
