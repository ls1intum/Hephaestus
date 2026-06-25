package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Pins the #895 research-integrity firewall: the detection-context assembly must be REACTION-BLIND.
 *
 * <p><b>Why this matters (ADR 0021, F-9).</b> {@code Reaction} records whether a contributor
 * previously {@code ADDRESSED}/{@code DISPUTED}/{@code NOT_APPLICABLE}'d a finding. If any of that
 * reaches the sandbox the detector reads, the AI could learn to agree with whatever the contributor
 * accepted and contaminate the accuracy measurement the thesis depends on — so a finding must be
 * emitted blind to how earlier findings were received. Today the firewall holds only by omission (no
 * context provider happens to query reactions); a future refactor could silently breach it. This test
 * makes the firewall load-bearing: the {@code ContentProvider}s that materialise the detection
 * sandbox ({@code inputs/...}) may not depend on the reaction package.
 *
 * <p>Scope note: this guards the <em>detection context</em> only. Reaction-aware <em>delivery</em>
 * (suppressing re-nag of an already-applied finding) is a separate, intended capability in the
 * delivery layer and is deliberately NOT constrained here.
 */
class DetectionReactionFirewallTest extends HephaestusArchitectureTest {

    private static final String CONTEXT_PROVIDERS = "..agent.context.providers..";
    private static final String REACTION_PACKAGE = "..practices.observation.reaction..";

    @Test
    void detectionContextProvidersAreReactionBlind() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage(CONTEXT_PROVIDERS)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(REACTION_PACKAGE)
            .because(
                "the detection sandbox must be blind to contributor reactions (#895 / ADR 0021 F-9): a " +
                    "finding is emitted without knowing whether earlier findings were applied or disputed, or " +
                    "the accuracy measurement the research depends on is contaminated. Reaction-aware behaviour " +
                    "belongs in the delivery layer, never in the context providers."
            );
        rule.check(classes);
    }
}
