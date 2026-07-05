package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * The raw Outline wire records ({@code integration.outline.client.dto}) are an implementation detail of the
 * client that speaks to Outline and of the sync boundary that maps them onto the {@code outline_document}
 * mirror. Nothing else — and in particular no agent read-path or content provider — is allowed to touch
 * them, so the vendor's wire shape can never leak past the extract/load seam.
 */
class OutlineApiDtoIsolationTest extends HephaestusArchitectureTest {

    @Test
    void outlineWireDtosAreUsedOnlyByTheClientAndSync() {
        ArchRule rule = classes()
            .that()
            .resideInAPackage("..integration.outline.client.dto..")
            .should()
            .onlyBeAccessed()
            .byClassesThat()
            .resideInAnyPackage("..integration.outline.client..", "..integration.outline.sync..")
            .because(
                "Outline wire DTOs are an implementation detail of the client and its sole sync consumer; " +
                    "they must not leak into the agent read path or any other module"
            );
        rule.check(classes);
    }
}
