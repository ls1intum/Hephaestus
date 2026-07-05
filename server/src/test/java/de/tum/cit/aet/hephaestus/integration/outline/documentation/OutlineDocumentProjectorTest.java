package de.tum.cit.aet.hephaestus.integration.outline.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.documentation.DocumentProjection.ProjectedDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;

class OutlineDocumentProjectorTest extends BaseUnitTest {

    @Mock
    private OutlineDocumentRepository documentRepository;

    @Captor
    private ArgumentCaptor<Set<String>> refsCaptor;

    private static OutlineDocument liveDocument() {
        OutlineDocument doc = new OutlineDocument();
        doc.setWorkspaceId(42L);
        doc.setConnectionId(7L);
        doc.setDocumentId("doc-uuid-1");
        doc.setCollectionId("col-uuid-1");
        doc.setCollectionSlug("architecture");
        doc.setSlug("design-doc");
        doc.setTitle("Design Doc");
        doc.setBodyMarkdown("# Design\n\nContent.");
        return doc;
    }

    private static OutlineDocument tombstonedDocument() {
        OutlineDocument doc = new OutlineDocument();
        doc.setWorkspaceId(42L);
        doc.setConnectionId(7L);
        doc.setDocumentId("doc-uuid-2");
        doc.setCollectionId("col-uuid-1");
        doc.setCollectionSlug("architecture");
        doc.setSlug("removed-doc");
        doc.setTitle("Removed Doc");
        // Removed upstream: body dropped, deleted_at stamped.
        doc.setBodyMarkdown(null);
        doc.setDeletedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return doc;
    }

    @Test
    @DisplayName("documentsForWorkspace maps entity fields onto the projected document")
    void documentsForWorkspace_mapsFieldsCorrectly() {
        OutlineDocumentProjector projector = new OutlineDocumentProjector(documentRepository);
        when(documentRepository.findForProjection(eq(42L), any(Pageable.class))).thenReturn(List.of(liveDocument()));

        List<ProjectedDocument> result = projector.documentsForWorkspace(42L);

        assertThat(result)
            .singleElement()
            .satisfies(projected -> {
                assertThat(projected.collectionSlug()).isEqualTo("architecture");
                assertThat(projected.slug()).isEqualTo("design-doc");
                assertThat(projected.title()).isEqualTo("Design Doc");
                assertThat(projected.bodyMarkdown()).isEqualTo("# Design\n\nContent.");
                assertThat(projected.deleted()).isFalse();
            });
    }

    @Test
    @DisplayName("a tombstoned row projects as deleted=true with a null body")
    void documentsForWorkspace_tombstonedRowIsDeletedWithNullBody() {
        OutlineDocumentProjector projector = new OutlineDocumentProjector(documentRepository);
        when(documentRepository.findForProjection(eq(42L), any(Pageable.class))).thenReturn(
            List.of(tombstonedDocument())
        );

        List<ProjectedDocument> result = projector.documentsForWorkspace(42L);

        assertThat(result)
            .singleElement()
            .satisfies(projected -> {
                assertThat(projected.deleted()).isTrue();
                assertThat(projected.bodyMarkdown()).isNull();
                assertThat(projected.slug()).isEqualTo("removed-doc");
            });
    }

    @Test
    @DisplayName("documentsByReference expands a URL reference to its trailing path segment before querying")
    void documentsByReference_expandsUrlToTrailingSegment() {
        OutlineDocumentProjector projector = new OutlineDocumentProjector(documentRepository);
        when(documentRepository.findByWorkspaceIdAndReferenceIn(eq(42L), refsCaptor.capture())).thenReturn(
            List.of(liveDocument())
        );

        List<ProjectedDocument> result = projector.documentsByReference(
            42L,
            List.of("https://outline.example.com/doc/design-doc", "doc-uuid-1")
        );

        assertThat(result).hasSize(1);
        assertThat(refsCaptor.getValue()).contains(
            "https://outline.example.com/doc/design-doc",
            "design-doc",
            "doc-uuid-1"
        );
    }

    @Test
    @DisplayName("documentsByReference short-circuits on an empty reference set")
    void documentsByReference_emptyInputReturnsEmpty() {
        OutlineDocumentProjector projector = new OutlineDocumentProjector(documentRepository);

        assertThat(projector.documentsByReference(42L, List.of())).isEmpty();
    }
}
