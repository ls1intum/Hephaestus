package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Erasure integration tests (Testcontainers) for the Outline workspace-purge arm: purging one workspace drops
 * every mirrored document for it and leaves another workspace's documents untouched — completeness and no
 * over-reach. Distinct workspace ids per test give isolation without a clean-between step.
 */
class OutlineWorkspacePurgeAdapterIntegrationTest extends BaseIntegrationTest {

    private static final AtomicLong WS_SEQ = new AtomicLong(9_200_000L);

    @Autowired
    private OutlineWorkspacePurgeAdapter adapter;

    @Autowired
    private OutlineDocumentRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private OutlineDocument document(long workspaceId, long connectionId, String documentId) {
        OutlineDocument doc = new OutlineDocument();
        doc.setWorkspaceId(workspaceId);
        doc.setConnectionId(connectionId);
        doc.setDocumentId(documentId);
        doc.setCollectionId("col-1");
        doc.setTitle("Design decision");
        doc.setSlug("design-decision");
        doc.setBodyMarkdown("# Why\n\nWe chose X over Y.");
        return doc;
    }

    @Test
    void purge_erasesTheWorkspacesDocumentsAndSparesAnother() {
        long wsA = WS_SEQ.incrementAndGet();
        long wsB = WS_SEQ.incrementAndGet();
        repository.save(document(wsA, 1L, "doc-a1"));
        repository.save(document(wsA, 1L, "doc-a2"));
        repository.save(document(wsB, 2L, "doc-b1"));

        // The production purge runs the contributor chain inside one transaction (WorkspacePurgeIntegrationTest →
        // workspaceLifecycleService.purgeWorkspace); mirror that boundary so the bulk delete has an active tx.
        transactionTemplate.executeWithoutResult(status -> adapter.deleteWorkspaceData(wsA));

        assertThat(repository.countByWorkspaceId(wsA)).isZero();
        assertThat(repository.countByWorkspaceId(wsB)).isEqualTo(1L);
    }
}
