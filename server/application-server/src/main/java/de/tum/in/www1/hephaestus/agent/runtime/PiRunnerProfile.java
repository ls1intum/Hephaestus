package de.tum.in.www1.hephaestus.agent.runtime;

import java.util.List;
import java.util.Map;

/**
 * Per-runner-kind strategy resolved at {@link PiPlanSpec} construction time.
 *
 * <p>Each adapter (practice, mentor) owns its own implementation in the adjacent domain package
 * and passes it to {@link PiRuntimeFactory#build(PiPlanSpec)}. This replaces the dispatch
 * branches that previously lived inside the kernel ({@code MENTOR_RUNNER_SCRIPT} filename match,
 * {@code nodeFlagsFor}, {@code nodeEnvFor}). The kernel reads three properties off the profile;
 * adding a third runner kind no longer requires editing the kernel.
 *
 * <p><b>Note on sealing:</b> not declared {@code sealed}. A {@code sealed} interface with
 * {@code permits PracticeRunnerProfile, MentorRunnerProfile} would force this kernel type to
 * import its domain implementations — exactly the {@code agent.runtime ↛ agent.practice /
 * agent.mentor} dependency the architecture test forbids. The boundary is instead enforced by a
 * dedicated arch rule (see {@code AgentRuntimeBoundaryTest.PiRunnerProfilePlacement}), mirroring
 * the established {@code ContentProvider} pattern.
 *
 * <p><b>Note on {@code shouldRegisterHephaestusProvider}:</b> stayed a kernel-side spec helper —
 * it's a function of {@link PiPlanSpec#credentialMode()} / {@link PiPlanSpec#provider()} /
 * {@link PiPlanSpec#baseUrl()}, not the runner kind. Both practice and mentor compute it
 * identically today. Pushing it into the profile would force domain modules to import
 * {@code CredentialMode} + {@code LlmProvider} only to defer to the kernel logic.
 *
 * <p>Profiles are pure value objects. They MUST NOT capture spec-level state (provider,
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
