package de.tum.cit.aet.hephaestus.agent.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The floor under the documentation seam.
 *
 * <p>What this pins is not the emptiness — it is that a deployment with no documentation integration
 * has an answer at all. The seam's only implementation lives behind a master switch that is off by
 * default, so without this bean the review context source that reads it could not be built and the
 * application did not start. A boot with the switch off is exercised by the role-gating integration
 * tests; this states what the floor then returns.
 */
class NoDocumentationMirrorTest extends BaseUnitTest {

    private final DocumentProjection projection = new NoDocumentationMirror();

    @Test
    @DisplayName("nothing is mirrored, so nothing is found — never a fabricated document")
    void answersNothingForEveryLookup() {
        assertThat(projection.documentsForWorkspace(1L)).isEmpty();
        assertThat(projection.documentsByReference(1L, List.of("doc-1", "https://wiki.example.com/doc/x"))).isEmpty();
        assertThat(projection.searchDocuments(1L, "runbook", 10)).isEmpty();
        assertThat(projection.extractReferences("see https://wiki.example.com/doc/onboarding-a1b2c3")).isEmpty();
    }

    @Test
    @DisplayName("a document review finds no subject, which its content source reports as unavailable")
    void hasNoSubjectForADocumentReview() {
        // Empty rather than a stub document: DocumentContentSource turns this into an unavailable
        // required source and the review is refused with a reason, instead of a model being handed a
        // document-shaped file with no document in it.
        assertThat(projection.documentById(1L, 77L)).isEmpty();
    }
}
