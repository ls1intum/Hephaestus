package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static de.tum.cit.aet.hephaestus.architecture.HephaestusArchitectureTest.classes;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Product reviews consume neutral ports. Domain and provider code own source-specific work. */
@Tag("architecture")
class PracticeReviewModuleBoundaryTest {
    @Test
    void shouldKeepDomainAndProviderTypesOutOfCoreProducts() {
        noClasses().that().resideInAnyPackage("..core..", "..workspace..", "..practices..",
                        "..agent..", "..evidence..", "..mentor..", "..integration.core..")
                .should().dependOnClassesThat().resideInAnyPackage("..integration.scm..",
                        "..integration.chat..", "..integration.docs..", "..integration.slack..", "..integration.outline..")
                .because("domain adapters resolve provider identity and evidence through neutral ports")
                .check(classes);
    }

    @Test
    void shouldKeepSharedReviewPortsFreeOfPersistenceAndOrchestration() {
        noClasses().that().resideInAPackage("..practices.spi..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..",
                        "org.springframework..", "..agent..", "..integration.scm..",
                        "..integration.slack..", "..integration.outline..")
                .check(classes);
    }

    @Test
    void shouldKeepProductProofIndependentOfRetainedJobs() {
        noClasses().that().haveNameMatching(".*\\.(Result|Feedback)")
                .should().dependOnClassesThat().resideInAPackage("..agent..")
                .because("results and feedback retain their review after runtime jobs expire")
                .check(classes);
    }
}
