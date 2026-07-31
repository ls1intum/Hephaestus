package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class LegacyPracticeCatalogProvenanceLinkerTest extends BaseUnitTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private PracticeRevisionRepository revisionRepository;

    @Mock
    private CuratedPracticeRepository curatedPracticeRepository;

    private LegacyPracticeCatalogProvenanceLinker linker;

    @BeforeEach
    void setUp() {
        linker = new LegacyPracticeCatalogProvenanceLinker(
            workspaceRepository,
            practiceRepository,
            revisionRepository,
            curatedPracticeRepository
        );
    }

    @Test
    void shouldLinkMatchingDetectorInputsDespiteLearnerGuidanceEdits() {
        Workspace workspace = TestEntities.workspace(7L);
        Practice practice = workspacePractice("Local guidance", "Evaluate failures", 11L);
        CuratedPractice curated = curatedPractice("Bundled guidance", "Evaluate failures", 21L);
        when(workspaceRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(workspace));
        when(workspaceRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(workspace));
        when(practiceRepository.findByFilters(7L, null)).thenReturn(List.of(practice));
        when(curatedPracticeRepository.findBySlug("review-failures")).thenReturn(Optional.of(curated));
        when(revisionRepository.linkEquivalentCuratedRevision(11L, 21L, fingerprint())).thenReturn(1);

        assertThat(linker.lockFirstWorkspace()).containsSame(workspace);
        assertThat(linker.link(workspace)).isEqualTo(1);

        verify(practice).setSourceCuratedPractice(curated);
        verify(practiceRepository).save(practice);
    }

    @Test
    void shouldLeaveEditedDetectorInputsUnlinked() {
        Workspace workspace = TestEntities.workspace(7L);
        Practice practice = workspacePractice("Local guidance", "Edited criteria", null);
        CuratedPractice curated = curatedPractice("Bundled guidance", "Evaluate failures", null);
        when(workspaceRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(workspace));
        when(workspaceRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(workspace));
        when(practiceRepository.findByFilters(7L, null)).thenReturn(List.of(practice));
        when(curatedPracticeRepository.findBySlug("review-failures")).thenReturn(Optional.of(curated));

        assertThat(linker.lockFirstWorkspace()).containsSame(workspace);
        assertThat(linker.link(workspace)).isZero();

        verify(practice, never()).setSourceCuratedPractice(org.mockito.ArgumentMatchers.any());
        verify(practiceRepository, never()).save(practice);
    }

    private static Practice workspacePractice(String guidance, String criteria, Long revisionId) {
        PracticeRevision revision = mock(PracticeRevision.class);
        if (revisionId != null) {
            when(revision.getId()).thenReturn(revisionId);
        }
        Practice practice = mock(Practice.class);
        when(practice.getSlug()).thenReturn("review-failures");
        when(practice.getName()).thenReturn("Review failures");
        when(practice.getArtifactType()).thenReturn(WorkArtifact.PULL_REQUEST);
        when(practice.getTriggerEvents()).thenReturn(TriggerEventsConverter.toJsonNode(List.of("PullRequestCreated")));
        when(practice.getCriteria()).thenReturn(criteria);
        when(practice.getWhyItMatters()).thenReturn(guidance);
        when(practice.getCurrentRevision()).thenReturn(revision);
        return practice;
    }

    private static CuratedPractice curatedPractice(String guidance, String criteria, Long revisionId) {
        CuratedPracticeRevision revision = mock(CuratedPracticeRevision.class);
        if (revisionId != null) {
            when(revision.getId()).thenReturn(revisionId);
        }
        when(revision.getName()).thenReturn("Review failures");
        when(revision.getArtifactType()).thenReturn(WorkArtifact.PULL_REQUEST);
        when(revision.getTriggerEvents()).thenReturn(TriggerEventsConverter.toJsonNode(List.of("PullRequestCreated")));
        when(revision.getCriteria()).thenReturn(criteria);
        when(revision.getWhyItMatters()).thenReturn(guidance);
        CuratedPractice curated = mock(CuratedPractice.class);
        when(curated.getSourceKind()).thenReturn(CuratedPracticeSourceKind.BUNDLED);
        when(curated.getLatestBundledRevision()).thenReturn(revision);
        return curated;
    }

    private static String fingerprint() {
        return de.tum.cit.aet.hephaestus.practices.PracticeDetectionFingerprint.of(
            "review-failures",
            "Review failures",
            WorkArtifact.PULL_REQUEST,
            List.of("PullRequestCreated"),
            "Evaluate failures",
            null,
            null
        );
    }
}
