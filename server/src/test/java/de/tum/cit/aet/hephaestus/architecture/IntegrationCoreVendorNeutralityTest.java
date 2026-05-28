package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins vendor-neutrality of {@code integration.core.**}. {@code integration.scm.domain.**}
 * is intentionally allowed — it is the cross-vendor SCM domain layer.
 */
class IntegrationCoreVendorNeutralityTest extends HephaestusArchitectureTest {

    @Test
    @DisplayName("no class in integration.core.** imports from a vendor adapter package")
    void coreNeverImportsVendorAdapter() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("de.tum.cit.aet.hephaestus.integration.core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "de.tum.cit.aet.hephaestus.integration.scm.github..",
                "de.tum.cit.aet.hephaestus.integration.scm.gitlab..",
                "de.tum.cit.aet.hephaestus.integration.slack..",
                "de.tum.cit.aet.hephaestus.integration.outline.."
            )
            .because(
                "integration.core.** is the shared event/registry/SPI substrate. " +
                    "Vendor types must live in integration.<kind>.** and reach core via SPI ports. " +
                    "A vendor import in core silently transitively couples every consumer of the " +
                    "core surface to that vendor."
            );
        rule.check(classes);
    }
}
