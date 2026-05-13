package de.tum.in.www1.hephaestus.agent.mentor;

import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxSpec;

/**
 * Test-only fixture that intentionally violates the architectural rule "agent.mentor.** must not
 * invoke {@link SandboxManager#execute}". Loaded selectively by the positive fixture test in
 * {@code SandboxArchitectureTest.InteractiveIsolation} to prove the rule actually catches
 * violations (guards against ArchUnit #324 — {@code callMethod} silently passes for unmatched
 * signatures).
 *
 * <p>This class must not be referenced by production code. It lives under {@code src/test/java}
 * and is excluded from {@code classes} via {@code DO_NOT_INCLUDE_TESTS}, so the main behavioural
 * rule does not see it.
 */
@SuppressWarnings("unused")
public final class MentorMustNotCallSandboxManagerExecuteFixture {

    public void violatesRule(SandboxManager manager, SandboxSpec spec) {
        manager.execute(spec);
    }

    private MentorMustNotCallSandboxManagerExecuteFixture() {}
}
