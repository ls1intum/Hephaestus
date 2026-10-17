package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineTestFixtures;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Erasure integration tests (Testcontainers) for the Outline workspace-purge arm: purging one workspace drops
 * every mirrored document for it and leaves another workspace's documents untouched — completeness and no
 * over-reach. Distinct workspace ids per test give isolation without a clean-between step.
 */
class OutlineWorkspacePurgeAdapterIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private ConnectionRepository connections;

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
        var first = workspaces.saveAndFlush(WorkspaceTestFixtures.activeWorkspace("purge-a-" + UUID.randomUUID()));
        var second = workspaces.saveAndFlush(WorkspaceTestFixtures.activeWorkspace("purge-b-" + UUID.randomUUID()));
        long wsA = first.getId();
        long wsB = second.getId();
        var sourceA = OutlineTestFixtures.connection(connections, first);
        var sourceB = OutlineTestFixtures.connection(connections, second);
        var a1 = repository.saveAndFlush(document(wsA, sourceA.getId(), "doc-a1"));
        var a2 = repository.saveAndFlush(document(wsA, sourceA.getId(), "doc-a2"));
        var b1 = repository.saveAndFlush(document(wsB, sourceB.getId(), "doc-b1"));

        // The production purge runs the contributor chain inside one transaction (WorkspacePurgeIntegrationTest →
        // workspaceLifecycleService.purgeWorkspace); mirror that boundary so the bulk delete has an active tx.
        transactionTemplate.executeWithoutResult(status -> adapter.deleteWorkspaceData(wsA));

        assertThat(repository.findById(a1.getId())).isEmpty();
        assertThat(repository.findById(a2.getId())).isEmpty();
        assertThat(repository.findById(b1.getId())).isPresent();
    }
}
