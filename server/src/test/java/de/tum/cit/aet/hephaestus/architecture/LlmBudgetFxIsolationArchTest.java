package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * <b>No converted number may ever reach an enforcement gate.</b>
 *
 * <p>USD is the sole unit of record, pricing and enforcement; the display currency is an estimate
 * computed at read time from a rate that can be stale, missing, or resolved to a date other than
 * today. Letting any of that near a budget comparison would mean an FX move — or a failed fetch —
 * could pause or unpause a workspace's work. So the classes that decide whether spend is allowed
 * stay FX-free, structurally, rather than by convention:
 *
 * <ul>
 *   <li>{@code LlmBudgetService} — the cap comparison itself</li>
 *   <li>{@code LlmAdmissionService} — admission-time price freeze</li>
 *   <li>{@code ProxyBudgetGate} — the per-request gate in front of the LLM proxy</li>
 *   <li>{@code AgentJobExecutor} — the claim-time budget recheck</li>
 *   <li>{@code LlmUsageRecorder} — the ledger write</li>
 * </ul>
 *
 * <p>The read-side rollup services ({@code LlmUsageService}, {@code LlmUsageAdminService}) are
 * deliberately NOT in this set: attaching the rate to a response is exactly their job.
 */
@Tag("architecture")
class LlmBudgetFxIsolationArchTest extends HephaestusArchitectureTest {

    private static final String FX_PACKAGE = "de.tum.cit.aet.hephaestus.agent.usage.fx";

    /** Fully-qualified names of the enforcement set. */
    private static final Set<String> ENFORCEMENT_CLASSES = Set.of(
        "de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService",
        "de.tum.cit.aet.hephaestus.agent.usage.LlmAdmissionService",
        "de.tum.cit.aet.hephaestus.agent.proxy.ProxyBudgetGate",
        "de.tum.cit.aet.hephaestus.agent.job.AgentJobExecutor",
        "de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder"
    );

    @Test
    @DisplayName("the enforcement set the rule names still exists (renames must not silently empty it)")
    void enforcementClassesArePresent() {
        List<String> present = classes
            .stream()
            .map(JavaClass::getFullName)
            .filter(ENFORCEMENT_CLASSES::contains)
            .toList();

        assertThat(present)
            .as(
                "Every class this rule guards must be found in the imported set — a rename that " +
                    "silently drops one would leave the rule passing while guarding nothing."
            )
            .containsExactlyInAnyOrderElementsOf(ENFORCEMENT_CLASSES);
    }

    @Test
    @DisplayName("no budget-enforcement class depends on the fx package")
    void enforcementIsFreeOfFxDependencies() {
        ArchRule rule = noClasses()
            .that()
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService")
            .or()
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.agent.usage.LlmAdmissionService")
            .or()
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.agent.proxy.ProxyBudgetGate")
            .or()
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.agent.job.AgentJobExecutor")
            .or()
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(FX_PACKAGE + "..")
            .because(
                "USD is the unit of enforcement. A display rate can be stale, absent, or dated to a " +
                    "different day — none of which may ever decide whether a workspace's work runs."
            );
        rule.check(classes);
    }

    @Test
    @DisplayName("the fx package never reaches back into budget enforcement")
    void fxDoesNotDependOnEnforcement() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage(FX_PACKAGE + "..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.agent.usage.LlmAdmissionService")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.agent.proxy.ProxyBudgetGate")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder")
            .because(
                "The dependency is one-directional: read-side rollups pull a rate from fx, and fx " +
                    "knows nothing about budgets, prices or the ledger."
            );
        rule.check(classes);
    }
}
