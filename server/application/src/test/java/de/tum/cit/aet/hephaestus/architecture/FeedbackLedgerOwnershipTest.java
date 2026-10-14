package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

/** Feedback persistence belongs to practices. Runtime writers call practices.spi. */
class FeedbackLedgerOwnershipTest extends HephaestusArchitectureTest {
    @Test
    void shouldKeepFeedbackRepositoriesInsideTheirOwningModule() {
        noClasses()
                .that()
                .resideOutsideOfPackage("..practices..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..practices.feedback..")
                .because("the runtime prepares and sends feedback through the frozen feedback ports")
                .check(classes);
    }
}
