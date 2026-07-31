package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BundledCuratedCatalogReconcilerTest extends BaseUnitTest {

    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");

    private final CuratedCatalogSyncStateRepository stateRepository = mock(CuratedCatalogSyncStateRepository.class);
    private final CuratedPracticeRepository practiceRepository = mock(CuratedPracticeRepository.class);
    private final CuratedPracticeRevisionRepository revisionRepository = mock(CuratedPracticeRevisionRepository.class);
    private final CuratedPracticeAreaRepository areaRepository = mock(CuratedPracticeAreaRepository.class);
    private final LegacyPracticeCatalogProvenanceLinker provenanceLinker = mock(
        LegacyPracticeCatalogProvenanceLinker.class
    );
    private final ConfigAuditPort configAudit = mock(ConfigAuditPort.class);
    private final BundledCuratedCatalogReconciler reconciler = new BundledCuratedCatalogReconciler(
        stateRepository,
        practiceRepository,
        revisionRepository,
        areaRepository,
        provenanceLinker,
        configAudit,
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    private CuratedCatalogSyncState state;

    @BeforeEach
    void setUp() {
        state = new CuratedCatalogSyncState();
        ReflectionTestUtils.setField(state, "source", BundledCuratedCatalogReconciler.SOURCE);
        ReflectionTestUtils.setField(state, "catalogRevision", 1L);
        ReflectionTestUtils.setField(state, "contentDigest", "old-digest");
        ReflectionTestUtils.setField(state, "provenanceBackfillVersion", 1);
        when(stateRepository.findBySourceForUpdate(BundledCuratedCatalogReconciler.SOURCE)).thenReturn(
            Optional.of(state)
        );
        when(revisionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldApplyChangedBundleWhenPracticeIsSynced() {
        CuratedPractice practice = bundledPractice(definition("Original"));
        when(practiceRepository.findAllBundledForUpdate()).thenReturn(List.of(practice));
        when(revisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(null)).thenReturn(
            Optional.of(practice.getCurrentRevision())
        );

        reconciler.reconcile(catalog(2, definition("Updated")));

        assertThat(practice.getCurrentRevision().getName()).isEqualTo("Updated");
        assertThat(practice.getCurrentRevision().getOrigin()).isEqualTo(CuratedPracticeRevisionOrigin.BUNDLED);
        assertThat(practice.getSyncStatus()).isEqualTo(CuratedPracticeSyncStatus.SYNCED);
        verify(configAudit).record(any());
    }

    @Test
    void shouldHoldChangedBundleWhenPracticeIsOverridden() {
        CuratedPractice practice = bundledPractice(definition("Original"));
        PracticeDefinition override = definition("Instance override");
        CuratedPracticeRevision adminRevision = revision(
            practice,
            2,
            override,
            CuratedPracticeRevisionOrigin.ADMIN,
            null
        );
        practice.overrideWith(adminRevision, NOW);
        when(practiceRepository.findAllBundledForUpdate()).thenReturn(List.of(practice));
        when(revisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(null)).thenReturn(
            Optional.of(adminRevision)
        );

        reconciler.reconcile(catalog(2, definition("Updated bundle")));

        assertThat(practice.getCurrentRevision()).isSameAs(adminRevision);
        assertThat(practice.getLatestBundledRevision().getName()).isEqualTo("Updated bundle");
        assertThat(practice.getSyncStatus()).isEqualTo(CuratedPracticeSyncStatus.UPDATE_AVAILABLE);
    }

    @Test
    void shouldRejectDifferentContentForSynchronizedRevision() {
        assertThatThrownBy(() -> reconciler.reconcile(catalog(1, definition("Changed"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("conflicting content");
    }

    @Test
    void shouldSkipLowerBundleRevision() {
        ReflectionTestUtils.setField(state, "catalogRevision", 3L);

        reconciler.reconcile(catalog(2, definition("Older")));

        verifyNoInteractions(practiceRepository, revisionRepository, areaRepository, configAudit);
        assertThat(state.getCatalogRevision()).isEqualTo(3);
    }

    @Test
    void shouldDoNothingWhenRevisionAndDigestMatch() {
        BundledPracticeCatalog catalog = catalog(1, definition("Original"));
        ReflectionTestUtils.setField(state, "contentDigest", catalog.contentDigest());

        reconciler.reconcile(catalog);

        verifyNoInteractions(practiceRepository, revisionRepository, areaRepository, configAudit);
    }

    @Test
    void shouldLinkLegacyPracticesOnceAfterCatalogIsAvailable() {
        BundledPracticeCatalog catalog = catalog(1, definition("Original"));
        ReflectionTestUtils.setField(state, "contentDigest", catalog.contentDigest());
        ReflectionTestUtils.setField(state, "provenanceBackfillVersion", 0);
        Workspace workspace = TestEntities.workspace(7L);
        when(provenanceLinker.lockFirstWorkspace()).thenReturn(Optional.of(workspace));
        when(provenanceLinker.link(workspace)).thenReturn(3);

        reconciler.reconcile(catalog);
        reconciler.reconcile(catalog);

        verify(provenanceLinker).lockFirstWorkspace();
        verify(provenanceLinker).link(workspace);
        assertThat(state.getProvenanceBackfillVersion()).isEqualTo(1);
        assertThat(state.getProvenanceBackfilledAt()).isEqualTo(NOW);
    }

    @Test
    void shouldHoldReturningSourceWhenRemovedPracticeWasOverridden() {
        PracticeDefinition original = definition("Original");
        CuratedPractice practice = bundledPractice(original);
        when(practiceRepository.findAllBundledForUpdate()).thenReturn(List.of(practice));

        reconciler.reconcile(emptyCatalog(2));
        CuratedPracticeRevision adminRevision = revision(
            practice,
            2,
            definition("Instance override"),
            CuratedPracticeRevisionOrigin.ADMIN,
            null
        );
        practice.overrideWith(adminRevision, NOW);
        when(revisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(null)).thenReturn(
            Optional.of(adminRevision)
        );

        reconciler.reconcile(catalog(3, original));

        assertThat(practice.getCurrentRevision()).isSameAs(adminRevision);
        assertThat(practice.getSyncStatus()).isEqualTo(CuratedPracticeSyncStatus.UPDATE_AVAILABLE);
    }

    @Test
    void shouldCreateNewBundledPractice() {
        when(practiceRepository.findAllBundledForUpdate()).thenReturn(List.of());
        when(practiceRepository.findBySlugForUpdate("practice")).thenReturn(Optional.empty());
        when(practiceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(null)).thenReturn(Optional.empty());

        reconciler.reconcile(catalog(2, definition("New practice")));

        verify(revisionRepository).save(any());
        verify(configAudit).record(any());
    }

    @Test
    void shouldNotOverwriteInstanceSlugCollision() {
        CuratedPractice instancePractice = new CuratedPractice();
        instancePractice.initializeInstance("practice", NOW);
        when(practiceRepository.findAllBundledForUpdate()).thenReturn(List.of());
        when(practiceRepository.findBySlugForUpdate("practice")).thenReturn(Optional.of(instancePractice));

        reconciler.reconcile(catalog(2, definition("Bundled collision")));

        verify(revisionRepository, never()).save(any());
        verify(configAudit, never()).record(any());
        assertThat(instancePractice.getSourceKind()).isEqualTo(CuratedPracticeSourceKind.INSTANCE);
    }

    private CuratedPractice bundledPractice(PracticeDefinition definition) {
        CuratedPractice practice = new CuratedPractice();
        practice.initializeBundled("practice", NOW);
        CuratedPracticeRevision revision = revision(practice, 1, definition, CuratedPracticeRevisionOrigin.BUNDLED, 1L);
        practice.applyBundled(revision, NOW);
        return practice;
    }

    private BundledPracticeCatalog catalog(long revision, PracticeDefinition definition) {
        String digest = definition.digest("practice");
        return new BundledPracticeCatalog(
            revision,
            digest,
            List.of(),
            List.of(new BundledPracticeCatalog.BundledPractice("practice", definition, digest))
        );
    }

    private BundledPracticeCatalog emptyCatalog(long revision) {
        return new BundledPracticeCatalog(revision, "empty-" + revision, List.of(), List.of());
    }

    private CuratedPracticeRevision revision(
        CuratedPractice practice,
        int number,
        PracticeDefinition definition,
        CuratedPracticeRevisionOrigin origin,
        Long bundleRevision
    ) {
        return new CuratedPracticeRevision(
            practice,
            number,
            definition,
            definition.detectionFingerprint("practice"),
            origin,
            bundleRevision,
            definition.digest("practice"),
            NOW
        );
    }

    private static PracticeDefinition definition(String name) {
        return new PracticeDefinition(
            name,
            WorkArtifact.PULL_REQUEST,
            List.of("PullRequestCreated"),
            "Evaluate the practice",
            null,
            "It matters",
            "A concrete example",
            null
        );
    }
}
