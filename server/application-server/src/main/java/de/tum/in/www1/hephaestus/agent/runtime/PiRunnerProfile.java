package de.tum.in.www1.hephaestus.agent.runtime;

import java.util.List;
import java.util.Map;

/**
 * Per-runner-kind strategy resolved at {@link PiPlanSpec} construction time. Each adapter owns
 * its implementation in the adjacent domain package and passes it to
 * {@link PiRuntimeFactory#build(PiPlanSpec)}.
 *
 * <p>Not declared {@code sealed}: a {@code permits} list would force this kernel type to import
 * its domain implementations, violating the {@code agent.runtime ↛ agent.{practice,mentor}}
 * boundary. The boundary is enforced by {@code AgentRuntimeBoundaryTest} instead — same pattern
 * as the existing {@code ContentProvider} SPI.
 *
 * <p>Profiles are pure value objects: they MUST NOT capture spec-level state (provider,
 * credentials, baseUrl). Spec-dependent decisions stay in the kernel and read from the spec.
 */
public interface PiRunnerProfile {
    /**
     * Filename of the runner script under {@code resources/agent/} the kernel copies into the
     * container workspace at {@link WorkspaceAbi#RUNNER_SCRIPT_FILENAME}.
     */
    String runnerScript();

    /**
     * V8 CLI flags applied to the {@code node} invocation. Returned without a trailing space; the
     * kernel joins entries with {@code " "} and appends a trailing {@code " "} when non-empty.
     */
    List<String> nodeFlags();

    /**
     * Per-process env fragment scoped to the {@code node} invocation only — the kernel emits
     * these as {@code KEY=value} pairs immediately preceding the {@code node} keyword. NOT
     * applied to other binaries in the same shell ({@code bun}, {@code git}, {@code jq}) and
     * NOT injected into the image-wide container environment.
     */
    Map<String, String> additionalEnv();
}
