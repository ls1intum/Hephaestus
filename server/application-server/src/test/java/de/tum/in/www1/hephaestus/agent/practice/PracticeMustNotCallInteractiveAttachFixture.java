package de.tum.in.www1.hephaestus.agent.practice;

import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec;

/**
 * Test-only fixture that intentionally violates the architectural rule "agent.practice.** must
 * not invoke {@link InteractiveSandboxService#attach}". The positive fixture test in
 * {@code SandboxArchitectureTest.InteractiveIsolation} loads this class to prove the rule
 * actually catches violations (guards against ArchUnit #324 — {@code callMethod} silently passes
 * for unmatched signatures).
 *
 * <p>This class must not be referenced by production code. It lives under {@code src/test/java}
 * and is excluded from {@code classes} via {@code DO_NOT_INCLUDE_TESTS}, so the main behavioural
 * rule does not see it.
 */
@SuppressWarnings("unused")
public final class PracticeMustNotCallInteractiveAttachFixture {

    public void violatesRule(InteractiveSandboxService service, InteractiveSandboxSpec spec) {
        service.attach(spec);
    }

    private PracticeMustNotCallInteractiveAttachFixture() {}
}
