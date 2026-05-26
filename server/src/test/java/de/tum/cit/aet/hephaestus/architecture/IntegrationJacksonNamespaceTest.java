package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins Slice H — the integration module must use the Spring-Boot-4-wired
 * Jackson 3 {@code ObjectMapper} ({@code tools.jackson.databind.ObjectMapper}),
 * never Jackson 2's {@code com.fasterxml.jackson.databind.ObjectMapper}.
 * Jackson 2 jars remain on the classpath (transitive deps), so without this
 * rule it is easy to re-introduce the legacy type by import autocompletion.
 *
 * <p>Annotation imports ({@code com.fasterxml.jackson.annotation.*}) are still
 * valid because Jackson 3 reads them natively — that namespace is shared.
 */
class IntegrationJacksonNamespaceTest extends HephaestusArchitectureTest {

    @Test
    @DisplayName("integration/** does not import Jackson 2 databind classes")
    void integrationDoesNotUseJackson2Databind() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..integration..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.fasterxml.jackson.databind..")
            .because(
                "Spring Boot 4 auto-wires tools.jackson.databind.ObjectMapper (Jackson 3). " +
                    "Jackson 2 databind types must not be reintroduced under integration/. " +
                    "Annotation imports under com.fasterxml.jackson.annotation.* remain " +
                    "allowed — that namespace is shared between Jackson 2 and Jackson 3."
            );
        rule.check(classes);
    }

    @Test
    @DisplayName("integration/** does not import Jackson 2 core classes")
    void integrationDoesNotUseJackson2Core() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..integration..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.fasterxml.jackson.core..")
            .because("Jackson 2 core types replaced by tools.jackson.core.* in Slice H.");
        rule.check(classes);
    }
}
