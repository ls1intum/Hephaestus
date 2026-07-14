package de.tum.cit.aet.hephaestus.integration.outline.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Real-Postgres proof of the full-text relevance search: {@code websearch_to_tsquery('simple', ...)} +
 * {@code ts_rank} over title + body. Pinned properties: only live rows with a body match; a tombstoned row
 * carrying the query terms never surfaces; a weaker match ranks below a stronger one; other workspaces'
 * rows never leak.
 */
@TestPropertySource(properties = "hephaestus.integration.outline.enabled=true")
class OutlineDocumentSearchIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OutlineDocumentRepository documentRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private long workspaceId;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        Workspace workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("outline-search"));
        workspaceId = workspace.getId();

        seed(
            workspaceId,
            "doc-strong",
            "Deployment rollback procedure",
            "How we run a deployment and the rollback steps. Every deployment needs a rollback plan.",
            null
        );
        seed(
            workspaceId,
            "doc-weak",
            "Team handbook",
            "General onboarding notes. Mentions deployment once in passing.",
            null
        );
        seed(workspaceId, "doc-unrelated", "Holiday party planning", "Venue, catering, and music ideas.", null);
        seed(
            workspaceId,
            "doc-tombstoned",
            "Deployment rollback runbook",
            "Full deployment rollback terms, but removed upstream.",
            Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    @Test
    void ranksLiveMatchesByRelevanceAndExcludesTombstonedAndUnrelatedRows() {
        List<OutlineDocument> hits = documentRepository.searchByRelevance(workspaceId, "deployment OR rollback", 10);

        assertThat(hits).extracting(OutlineDocument::getDocumentId).containsExactly("doc-strong", "doc-weak");
    }

    @Test
    void neverLeaksAnotherWorkspacesDocuments() {
        Workspace other = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("outline-search-other"));
        seed(other.getId(), "doc-foreign", "Deployment rollback procedure", "Deployment rollback everywhere.", null);

        List<OutlineDocument> hits = documentRepository.searchByRelevance(other.getId(), "deployment OR rollback", 10);

        assertThat(hits).extracting(OutlineDocument::getDocumentId).containsExactly("doc-foreign");
    }

    @Test
    void returnsEmptyWhenNoTermMatches() {
        assertThat(documentRepository.searchByRelevance(workspaceId, "quantum OR entanglement", 10)).isEmpty();
    }

    @Test
    void oversizedDocumentDoesNotBreakSearchForTheWholeWorkspace() {
        // Postgres hard-refuses a tsvector over 1MB ("string is too long for tsvector") — it ERRORs, it does
        // not degrade. body_markdown is unbounded, so without the left(...) bound in the query expression a
        // single large mirrored wiki page would make EVERY Outline retrieval in this workspace throw, not
        // just rank that page badly. Seed a body comfortably past the ceiling and prove the query still runs
        // and still finds the ordinary documents around it.
        StringBuilder huge = new StringBuilder(1_400_000);
        while (huge.length() < 1_300_000) {
            huge.append("lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor ");
        }
        seed(workspaceId, "doc-huge", "Enormous appendix", huge.toString(), null);

        assertThat(documentRepository.searchByRelevance(workspaceId, "deployment OR rollback", 10))
            .extracting(OutlineDocument::getDocumentId)
            .containsExactly("doc-strong", "doc-weak");

        // The oversized document is still searchable itself — through the truncated prefix, which is what
        // the index is built over. (Its terms appear well inside the first 900 000 characters.)
        assertThat(documentRepository.searchByRelevance(workspaceId, "consectetur", 10))
            .extracting(OutlineDocument::getDocumentId)
            .containsExactly("doc-huge");
    }

    private void seed(long workspace, String documentId, String title, String body, @Nullable Instant deletedAt) {
        OutlineDocument doc = new OutlineDocument();
        doc.setWorkspaceId(workspace);
        doc.setConnectionId(1L);
        doc.setDocumentId(documentId);
        doc.setCollectionId("col-1");
        doc.setCollectionSlug("ops");
        doc.setSlug(documentId);
        doc.setTitle(title);
        doc.setBodyMarkdown(body);
        doc.setDeletedAt(deletedAt);
        documentRepository.save(doc);
    }
}
