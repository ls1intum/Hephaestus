package de.tum.cit.aet.hephaestus.integration.outline.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.documentation.DocumentProjection.ProjectedDocument;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.outline.identity.OutlineIdentityResolver;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;

class OutlineDocumentProjectorTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 42L;
    private static final String SERVER_URL = "https://wiki.example.com";
    private static final String TEAM_ID = "9ff8ee7d-team";

    @Mock
    private OutlineDocumentRepository documentRepository;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private OutlineIdentityResolver identityResolver;

    @Captor
    private ArgumentCaptor<Set<String>> refsCaptor;

    private OutlineDocumentProjector projector;

    @BeforeEach
    void setUp() {
        projector = new OutlineDocumentProjector(documentRepository, connectionService, identityResolver);
        lenient()
            .when(connectionService.findActive(WORKSPACE_ID, IntegrationKind.OUTLINE))
            .thenReturn(Optional.of(activeConnection()));
        lenient()
            .when(identityResolver.resolveMemberId(anyLong(), anyString(), any(), anyString()))
            .thenReturn(Optional.empty());
    }

    private static Connection activeConnection() {
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        return new Connection(
            workspace,
            IntegrationKind.OUTLINE,
            TEAM_ID,
            new ConnectionConfig.OutlineConfig(SERVER_URL, null, null, Set.of())
        );
    }

    private static OutlineDocument liveDocument() {
        OutlineDocument doc = new OutlineDocument();
        doc.setWorkspaceId(WORKSPACE_ID);
        doc.setConnectionId(7L);
        doc.setDocumentId("doc-uuid-1");
        doc.setCollectionId("col-uuid-1");
        doc.setCollectionSlug("architecture");
        doc.setSlug("design-doc");
        doc.setTitle("Design Doc");
        doc.setBodyMarkdown("# Design\n\nContent.");
        return doc;
    }

    private static OutlineDocument authoredDocument(String documentId, String subject, String name) {
        OutlineDocument doc = liveDocument();
        doc.setDocumentId(documentId);
        doc.setCreatedBySubject(subject);
        doc.setCreatedByName(name);
        doc.setUpdatedBySubject(subject);
        doc.setUpdatedByName(name);
        return doc;
    }

    private static OutlineDocument tombstonedDocument() {
        OutlineDocument doc = new OutlineDocument();
        doc.setWorkspaceId(WORKSPACE_ID);
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
        when(documentRepository.findForProjection(eq(WORKSPACE_ID), any(Pageable.class))).thenReturn(
            List.of(liveDocument())
        );

        List<ProjectedDocument> result = projector.documentsForWorkspace(WORKSPACE_ID);

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
        when(documentRepository.findForProjection(eq(WORKSPACE_ID), any(Pageable.class))).thenReturn(
            List.of(tombstonedDocument())
        );

        List<ProjectedDocument> result = projector.documentsForWorkspace(WORKSPACE_ID);

        assertThat(result)
            .singleElement()
            .satisfies(projected -> {
                assertThat(projected.deleted()).isTrue();
                assertThat(projected.bodyMarkdown()).isNull();
                assertThat(projected.slug()).isEqualTo("removed-doc");
            });
    }

    // --- authorship projection ---

    @Test
    @DisplayName("author substrate projects and a linked author resolves to their workspace member id")
    void authorFields_projectedAndResolved() {
        OutlineDocument doc = authoredDocument("doc-uuid-1", "0aa1bb2c-user", "Ada Lovelace");
        when(documentRepository.findForProjection(eq(WORKSPACE_ID), any(Pageable.class))).thenReturn(List.of(doc));
        when(identityResolver.resolveMemberId(WORKSPACE_ID, SERVER_URL, TEAM_ID, "0aa1bb2c-user")).thenReturn(
            Optional.of(555L)
        );

        List<ProjectedDocument> result = projector.documentsForWorkspace(WORKSPACE_ID);

        assertThat(result)
            .singleElement()
            .satisfies(projected -> {
                assertThat(projected.createdByName()).isEqualTo("Ada Lovelace");
                assertThat(projected.createdBySubject()).isEqualTo("0aa1bb2c-user");
                assertThat(projected.createdByMemberId()).isEqualTo(555L);
                assertThat(projected.updatedByName()).isEqualTo("Ada Lovelace");
                assertThat(projected.updatedByMemberId()).isEqualTo(555L);
            });
    }

    @Test
    @DisplayName("the resolver is consulted once per DISTINCT subject, not once per field or per document")
    void resolverConsultedOncePerDistinctSubject() {
        // Two documents by the same author (creator == last editor on both) → four subject slots, ONE lookup.
        OutlineDocument first = authoredDocument("doc-uuid-1", "0aa1bb2c-user", "Ada Lovelace");
        OutlineDocument second = authoredDocument("doc-uuid-2", "0aa1bb2c-user", "Ada Lovelace");
        second.setSlug("second-doc");
        when(documentRepository.findForProjection(eq(WORKSPACE_ID), any(Pageable.class))).thenReturn(
            List.of(first, second)
        );

        projector.documentsForWorkspace(WORKSPACE_ID);

        verify(identityResolver, times(1)).resolveMemberId(WORKSPACE_ID, SERVER_URL, TEAM_ID, "0aa1bb2c-user");
    }

    @Test
    @DisplayName("an unlinked author degrades to name-only (memberId null) — the graceful floor")
    void unlinkedAuthor_degradesToNameOnly() {
        OutlineDocument doc = authoredDocument("doc-uuid-1", "0aa1bb2c-user", "Ada Lovelace");
        when(documentRepository.findForProjection(eq(WORKSPACE_ID), any(Pageable.class))).thenReturn(List.of(doc));
        when(identityResolver.resolveMemberId(WORKSPACE_ID, SERVER_URL, TEAM_ID, "0aa1bb2c-user")).thenReturn(
            Optional.empty()
        );

        List<ProjectedDocument> result = projector.documentsForWorkspace(WORKSPACE_ID);

        assertThat(result)
            .singleElement()
            .satisfies(projected -> {
                assertThat(projected.createdByName()).isEqualTo("Ada Lovelace");
                assertThat(projected.createdByMemberId()).isNull();
            });
    }

    @Test
    @DisplayName("without an ACTIVE Outline connection authors degrade to name-only and the resolver is never called")
    void noActiveConnection_skipsResolution() {
        when(connectionService.findActive(WORKSPACE_ID, IntegrationKind.OUTLINE)).thenReturn(Optional.empty());
        OutlineDocument doc = authoredDocument("doc-uuid-1", "0aa1bb2c-user", "Ada Lovelace");
        when(documentRepository.findForProjection(eq(WORKSPACE_ID), any(Pageable.class))).thenReturn(List.of(doc));

        List<ProjectedDocument> result = projector.documentsForWorkspace(WORKSPACE_ID);

        assertThat(result)
            .singleElement()
            .satisfies(projected -> {
                assertThat(projected.createdByName()).isEqualTo("Ada Lovelace");
                assertThat(projected.createdByMemberId()).isNull();
            });
        verify(identityResolver, never()).resolveMemberId(anyLong(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("documentsByReference expands a URL reference to its trailing path segment before querying")
    void documentsByReference_expandsUrlToTrailingSegment() {
        when(documentRepository.findByWorkspaceIdAndReferenceIn(eq(WORKSPACE_ID), refsCaptor.capture())).thenReturn(
            List.of(liveDocument())
        );

        List<ProjectedDocument> result = projector.documentsByReference(
            WORKSPACE_ID,
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
        assertThat(projector.documentsByReference(WORKSPACE_ID, List.of())).isEmpty();
    }
}
