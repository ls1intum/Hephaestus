package de.tum.cit.aet.hephaestus.integration.outline.domain.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.tum.cit.aet.hephaestus.integration.core.signal.RevisionScheme;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The documentation domain's own vocabulary: which ingested events mean something, and what counts as
 * the same occurrence twice.
 */
@Tag("unit")
class DocsSignalsTest {

    @Test
    @DisplayName("only the three events that change what a document says raise a signal")
    void mapsOnlyTheEventsThatCarryReviewMeaning() {
        assertThat(DocsSignals.forOutlineEvent("documents.publish")).contains(DocsSignals.DOCUMENT_PUBLISHED);
        assertThat(DocsSignals.forOutlineEvent("documents.update")).contains(DocsSignals.DOCUMENT_UPDATED);
        assertThat(DocsSignals.forOutlineEvent("documents.archive")).contains(DocsSignals.DOCUMENT_ARCHIVED);

        // Empty is the common and correct answer: a move or a rename changes where a document sits, not
        // what it says, and occasioning a review on one spends a model call re-reading unchanged bytes.
        assertThat(DocsSignals.forOutlineEvent("documents.move")).isEmpty();
        assertThat(DocsSignals.forOutlineEvent("collections.update")).isEmpty();
        assertThat(DocsSignals.forOutlineEvent(null)).isEmpty();
    }

    @Test
    void keysContentSignalsOnTheContentAndTerminalOnesOnTheState() {
        assertThat(DocsSignals.revisionScheme(DocsSignals.DOCUMENT_PUBLISHED)).isEqualTo(RevisionScheme.CONTENT_DIGEST);
        assertThat(DocsSignals.revisionScheme(DocsSignals.DOCUMENT_UPDATED)).isEqualTo(RevisionScheme.CONTENT_DIGEST);
        assertThat(DocsSignals.revisionScheme(DocsSignals.DOCUMENT_ARCHIVED)).isEqualTo(RevisionScheme.TERMINAL_STATE);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DocsSignals.revisionScheme(SignalName.of("docs.document.invented")));
    }

    @Test
    @DisplayName("a re-publish of the same text is the same occurrence; an edit is a new one")
    void distinguishesOccurrencesByWhatTheDocumentSays() {
        var first = DocsSignals.documentKey(1L, 7L, DocsSignals.DOCUMENT_PUBLISHED, "hash-a", "Title");
        var same = DocsSignals.documentKey(1L, 7L, DocsSignals.DOCUMENT_PUBLISHED, "hash-a", "Title");
        var edited = DocsSignals.documentKey(1L, 7L, DocsSignals.DOCUMENT_PUBLISHED, "hash-b", "Title");
        var retitled = DocsSignals.documentKey(1L, 7L, DocsSignals.DOCUMENT_PUBLISHED, "hash-a", "New title");

        assertThat(first).isPresent().isEqualTo(same);
        assertThat(edited).isPresent().isNotEqualTo(first);
        // The title is part of the content a documentation practice reads, so renaming a document is a
        // new occurrence even when the body is byte-identical.
        assertThat(retitled).isPresent().isNotEqualTo(first);
    }

    @Test
    @DisplayName("no hash means no key — an evicted body cannot be keyed, and a made-up revision is worse")
    void refusesToInventARevisionForAnEvictedBody() {
        assertThat(DocsSignals.documentKey(1L, 7L, DocsSignals.DOCUMENT_PUBLISHED, null, "Title"))
                .isEmpty();
        assertThat(DocsSignals.documentKey(1L, 7L, DocsSignals.DOCUMENT_PUBLISHED, "  ", "Title"))
                .isEmpty();

        // Archiving happened once whatever the body says, so it keys without one.
        assertThat(DocsSignals.documentKey(1L, 7L, DocsSignals.DOCUMENT_ARCHIVED, null, "Title"))
                .isPresent();
    }

    @Test
    void refusesASignalThatIsNotAboutADocument() {
        assertThat(DocsSignals.documentKey(1L, 7L, SignalName.of("scm.pull_request.merged"), "hash-a", "Title"))
                .isEmpty();
    }
}
