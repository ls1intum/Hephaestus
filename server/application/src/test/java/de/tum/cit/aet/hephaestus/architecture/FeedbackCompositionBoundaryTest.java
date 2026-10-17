package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

class FeedbackCompositionBoundaryTest extends HephaestusArchitectureTest {
    @Test
    void shouldKeepCompositionOutOfFeedbackPersistence() {
        noClasses()
                .that()
                .resideInAPackage("..practices.feedback.delivery..")
                .and()
                .haveSimpleNameEndingWith("Parser")
                .should()
                .dependOnClassesThat()
                .areAssignableTo(Feedback.class)
                .orShould()
                .dependOnClassesThat()
                .areAssignableTo(Repository.class)
                .check(classes);
    }
}
