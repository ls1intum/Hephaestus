package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@WorkspaceAgnostic("One-time provenance migration is limited to the first workspace")
class LegacyPracticeCatalogProvenanceLinker {

    private final WorkspaceRepository workspaceRepository;
    private final PracticeRepository practiceRepository;
    private final PracticeRevisionRepository revisionRepository;
    private final CuratedPracticeRepository curatedPracticeRepository;

    Optional<Workspace> lockFirstWorkspace() {
        return workspaceRepository
            .findFirstByOrderByIdAsc()
            .flatMap(workspace -> workspaceRepository.findByIdForUpdate(workspace.getId()));
    }

    int link(Workspace workspace) {
        return practiceRepository.findByFilters(workspace.getId(), null).stream().mapToInt(this::link).sum();
    }

    private int link(Practice practice) {
        if (practice.getSourceCuratedPractice() != null || practice.getCurrentRevision() == null) {
            return 0;
        }
        CuratedPractice curated = curatedPracticeRepository.findBySlug(practice.getSlug()).orElse(null);
        if (
            curated == null ||
            curated.getSourceKind() != CuratedPracticeSourceKind.BUNDLED ||
            curated.getLatestBundledRevision() == null
        ) {
            return 0;
        }
        PracticeDefinition local = PracticeDefinition.from(practice);
        CuratedPracticeRevision bundledRevision = curated.getLatestBundledRevision();
        PracticeDefinition source = PracticeDefinition.from(bundledRevision);
        if (!local.hasSameDetectorInputs(source)) {
            return 0;
        }
        practice.setSourceCuratedPractice(curated);
        practiceRepository.save(practice);
        return revisionRepository.linkEquivalentCuratedRevision(
            practice.getCurrentRevision().getId(),
            bundledRevision.getId(),
            source.detectionFingerprint(practice.getSlug())
        );
    }
}
