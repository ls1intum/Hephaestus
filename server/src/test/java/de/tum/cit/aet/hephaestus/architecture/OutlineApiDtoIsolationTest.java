package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * The generated Outline vendor models ({@code integration.outline.client.model}) are an implementation
 * detail of the client that speaks to Outline, of the sync boundary that maps them onto the
 * {@code outline_document} mirror, of the collection admin control plane that verifies registrations
 * against the live {@code collections.list} and captures catalog fields server-side, of the lifecycle
 * registrar whose self-heal diffs the stored subscription id against {@code webhookSubscriptions.list},
 * and of the webhook handler that parses a delivery's pre-authenticated {@code payload.model}. Nothing
 * else — and in particular no agent read-path or content provider — is allowed to touch them, so the
 * vendor's wire shape can never leak past the extract/load seam even though it is now spec-generated.
 */
class OutlineApiDtoIsolationTest extends HephaestusArchitectureTest {

    @Test
    void outlineWireModelsStayOnTheExtractSeam() {
        ArchRule rule = classes()
            .that()
            .resideInAPackage("..integration.outline.client.model..")
            .should()
            .onlyBeAccessed()
            .byClassesThat()
            .resideInAnyPackage(
                "..integration.outline.client..",
                "..integration.outline.sync..",
                "..integration.outline.collection..",
                "..integration.outline.lifecycle..",
                "..integration.outline.webhook.."
            )
            .because(
                "Outline wire models are an implementation detail of the client and its sync, collection-admin, " +
                    "lifecycle-registrar, and webhook consumers on the extract seam; they must not leak into the " +
                    "agent read path or any other module"
            )
            // Never allow this guard to pass vacuously: the generated models MUST be present in the
            // imported set (they are — the base importer no longer excludes them), so an empty subject
            // set here means the package moved/renamed and the boundary is no longer being checked.
            .allowEmptyShould(false);
        rule.check(classes);
    }
}
