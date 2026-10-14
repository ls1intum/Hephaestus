package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.practices.PracticeCatalogInstallationManager;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionService;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogService;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeInputDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionOperations;

class CatalogInstallationConcurrencyIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    CatalogService catalog;

    @Autowired
    PracticeVersionService versions;

    @Autowired
    TransactionOperations transactions;

    @Autowired
    PracticeCatalogInstallationManager installer;

    @Autowired
    PracticeRepository practices;

    @Autowired
    WorkspaceRepository workspaces;

    @Test
    void shouldKeepTheFirstInstallReceiptWhenTheInstallationIsRequestedAgain() {
        var owner = persistUser("install-owner");
        var workspace = createWorkspace("catalog-install", "Install", "install-org", AccountType.ORG, owner);
        installer.install(workspace.getId());
        var first = workspaces.findById(workspace.getId()).orElseThrow().getPracticeCatalogInstalledAt();
        assertThat(first).isNotNull();
        List<Long> ids = practices.findByWorkspaceId(workspace.getId()).stream()
                .map(Practice::getId)
                .toList();
        assertThat(ids).isNotEmpty();
        installer.install(workspace.getId());
        assertThat(workspaces.findById(workspace.getId()).orElseThrow().getPracticeCatalogInstalledAt())
                .isEqualTo(first);
        assertThat(practices.findByWorkspaceId(workspace.getId()))
                .extracting(Practice::getId)
                .containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void shouldInstallTheCommittedDefinitionWhenACatalogWriteIsInProgress() throws Exception {
        var owner = persistUser("install-race-owner");
        var workspace = createWorkspace("catalog-race", "Install race", "install-race-org", AccountType.ORG, owner);
        assertThat(workspace.getPracticeCatalogInstalledAt()).isNull();
        var current = catalog.effectiveCatalog().practices().stream()
                .filter(entry -> entry.shipped() != null)
                .findFirst()
                .orElseThrow();
        var definition = current.effective().definition();
        var updated = new PracticeDefinition(
                definition.name(),
                definition.reviewOn(),
                definition.appliesWhen(),
                definition.about(),
                definition.reads(),
                definition.reviewedBy(),
                "Committed catalog criteria",
                definition.script(),
                definition.scan(),
                definition.lineNotes(),
                definition.guidance());
        var writeReady = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);
        var installerStarted = new CountDownLatch(1);
        var writer = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(ignored -> {
            catalog.putPractice(
                    current.slug(),
                    new CatalogPracticeInputDTO(
                            current.slug(),
                            current.effective().kind().value(),
                            current.effective().groupSlug(),
                            updated,
                            current.position()),
                    false,
                    EntityTagPrecondition.parse(EntityTagPrecondition.format(current.etag())));
            writeReady.countDown();
            await(allowCommit);
        }));
        try {
            assertThat(writeReady.await(10, TimeUnit.SECONDS)).isTrue();
            var installing = CompletableFuture.runAsync(() -> {
                installerStarted.countDown();
                installer.install(workspace.getId());
            });
            try {
                assertThat(installerStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> installing.get(250, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                allowCommit.countDown();
            }
            writer.get(10, TimeUnit.SECONDS);
            installing.get(10, TimeUnit.SECONDS);
        } finally {
            allowCommit.countDown();
            writer.get(10, TimeUnit.SECONDS);
        }
        var installed = practices
                .findByWorkspaceIdAndSlug(workspace.getId(), current.slug())
                .orElseThrow();
        assertThat(versions.definitionOf(installed.getCurrentVersion()).criteria())
                .isEqualTo("Committed catalog criteria");
        assertThat(workspaces.findById(workspace.getId()).orElseThrow().getPracticeCatalogInstalledAt())
                .isNotNull();
        assertThat(versions.history(installed)).hasSize(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out waiting for catalog commit");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for catalog commit", exception);
        }
    }
}
