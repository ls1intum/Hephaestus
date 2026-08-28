package de.tum.cit.aet.hephaestus.agent.mentor;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxSpec;

/** Deliberate violation of the {@code mentor → SandboxManager.execute} rule; loaded only by the positive-fixture test. */
@SuppressWarnings("unused")
public final class MentorMustNotCallSandboxManagerExecuteFixture {

    public void violatesRule(SandboxManager manager, SandboxSpec spec) {
        manager.execute(spec);
    }

    private MentorMustNotCallSandboxManagerExecuteFixture() {}
}
