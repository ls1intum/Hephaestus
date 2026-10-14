package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class PracticesIntegrationBoundaryTest extends HephaestusArchitectureTest {
    @Test
    void shouldReachIntegrationOnlyThroughNeutralPorts() {
        noClasses()
                .that()
                .resideInAPackage("..practices..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..integration.scm..",
                        "..integration.chat..",
                        "..integration.docs..",
                        "..integration.slack..",
                        "..integration.outline..",
                        "..integration.core.item..",
                        "..integration.core.itemevent..")
                .check(classes);
    }
}
