package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * <b>No budget-enforcement class may depend on the fx package.</b>
 *
 * <p>USD is the sole unit of record, pricing and enforcement; the display currency is an estimate
 * computed at read time from a rate that can be stale, missing, or resolved to a date other than
 * today. Letting any of that near a budget comparison would mean an FX move — or a failed fetch —
 * could pause or unpause a workspace's work. So the classes that decide whether spend is allowed stay
 * FX-free structurally rather than by convention.
 *
 * <p>The read-side rollup services ({@code LlmUsageService}, {@code LlmUsageAdminService}) are
 * deliberately NOT in this set: attaching the rate to a response is exactly their job.
 *
 * <p><b>What this test cannot see.</b> The rules below are structural — they catch an import, not a
 * multiplication. A rollup service that IS allowed to hold a rate could still convert a total before
 * passing it to {@code verdictFor}, and every rule here would stay green. That value-level property
 * has its own guard, and it must be a runtime one:
 * {@code LlmUsageFxDisplayIntegrationTest#budgetVerdictIsJudgedInUsdEvenWhenADisplayRateWouldUndercutIt}
 * fixes a cap and a spend that disagree once converted, and pins the USD answer.
 */
@Tag("architecture")
class LlmBudgetFxIsolationArchTest extends HephaestusArchitectureTest {

    private static final String FX_PACKAGE = "de.tum.cit.aet.hephaestus.agent.usage.fx";

    private static final Set<String> ENFORCEMENT_CLASSES = Set.of(
        "de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService",
        // Holds the caps and the spend and performs the comparison the proxy pauses work on — the
        // most literal "decides whether spend is allowed" class there is.
        "de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetHeadroom",
        "de.tum.cit.aet.hephaestus.agent.usage.LlmAdmissionService",
        "de.tum.cit.aet.hephaestus.agent.proxy.ProxyBudgetGate",
        "de.tum.cit.aet.hephaestus.agent.job.AgentJobExecutor",
        "de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder"
    );

    private static final DescribedPredicate<JavaClass> ENFORCEMENT = DescribedPredicate.describe(
        "budget-enforcement classes",
        javaClass -> ENFORCEMENT_CLASSES.contains(javaClass.getFullName())
    );

    /**
     * Both rules below match on name, so a rename would empty them and leave them passing while
     * guarding nothing. This is what keeps them non-vacuous.
     */
    @Test
    @DisplayName("the enforcement set the rules name still exists")
    void enforcementClassesArePresent() {
        List<String> present = classes
            .stream()
            .map(JavaClass::getFullName)
            .filter(ENFORCEMENT_CLASSES::contains)
            .toList();

        assertThat(present).containsExactlyInAnyOrderElementsOf(ENFORCEMENT_CLASSES);
    }

    @Test
    @DisplayName("no budget-enforcement class depends on the fx package")
    void enforcementIsFreeOfFxDependencies() {
        ArchRule rule = noClasses()
            .that(ENFORCEMENT)
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
            .dependOnClassesThat(ENFORCEMENT)
            .because(
                "The dependency is one-directional: read-side rollups pull a rate from fx, and fx " +
                    "knows nothing about budgets, prices or the ledger."
            );
        rule.check(classes);
    }
}
