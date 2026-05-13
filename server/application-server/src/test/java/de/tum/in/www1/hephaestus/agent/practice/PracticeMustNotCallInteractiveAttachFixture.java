package de.tum.in.www1.hephaestus.agent.practice;

import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec;

/** Deliberate violation of the {@code practice → InteractiveSandboxService.attach} rule. */
@SuppressWarnings("unused")
public final class PracticeMustNotCallInteractiveAttachFixture {

    public void violatesRule(InteractiveSandboxService service, InteractiveSandboxSpec spec) {
        service.attach(spec);
    }

    private PracticeMustNotCallInteractiveAttachFixture() {}
}
