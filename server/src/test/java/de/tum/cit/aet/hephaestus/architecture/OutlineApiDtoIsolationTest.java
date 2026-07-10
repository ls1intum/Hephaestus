package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * The raw Outline wire records ({@code integration.outline.client.dto}) are an implementation detail of the
 * client that speaks to Outline, of the sync boundary that maps them onto the {@code outline_document}
 * mirror, of the collection admin control plane that verifies registrations against the live
 * {@code collections.list} and captures catalog fields server-side, and of the lifecycle registrar whose
 * self-heal diffs the stored subscription id against {@code webhookSubscriptions.list}. Nothing else — and
 * in particular no agent read-path or content provider — is allowed to touch them, so the vendor's wire
 * shape can never leak past the extract/load seam.
 */
class OutlineApiDtoIsolationTest extends HephaestusArchitectureTest {

    @Test
    void outlineWireDtosStayOnTheExtractSeam() {
        ArchRule rule = classes()
            .that()
            .resideInAPackage("..integration.outline.client.dto..")
            .should()
            .onlyBeAccessed()
            .byClassesThat()
            .resideInAnyPackage(
                "..integration.outline.client..",
                "..integration.outline.sync..",
                "..integration.outline.collection..",
                "..integration.outline.lifecycle.."
            )
            .because(
                "Outline wire DTOs are an implementation detail of the client and its sync, collection-admin, and " +
                    "lifecycle-registrar consumers on the extract seam; they must not leak into the agent read " +
                    "path or any other module"
            );
        rule.check(classes);
    }
}
