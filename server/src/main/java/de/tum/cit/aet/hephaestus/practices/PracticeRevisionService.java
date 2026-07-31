package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PracticeRevisionService {

    private final PracticeRepository practiceRepository;
    private final PracticeRevisionRepository practiceRevisionRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public PracticeRevision append(Practice practice) {
        return append(practice, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PracticeRevision append(Practice practice, @Nullable CuratedPracticeRevision equivalentCuratedRevision) {
        practiceRepository
            .findByIdForUpdate(practice.getId())
            .orElseThrow(() -> new EntityNotFoundException("Practice", String.valueOf(practice.getId())));
        var previous = practiceRevisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(practice.getId());
        int revisionNumber = previous.map(revision -> revision.getRevisionNumber() + 1).orElse(1);
        PracticeRevision revision = new PracticeRevision(practice, revisionNumber, equivalentCuratedRevision);
        String detectionFingerprint = revision.getDetectionFingerprint();
        if (
            equivalentCuratedRevision == null &&
            previous
                .filter(prior -> prior.getDetectionFingerprint() != null)
                .filter(prior -> prior.getDetectionFingerprint().equals(detectionFingerprint))
                .isPresent()
        ) {
            revision = new PracticeRevision(
                practice,
                revisionNumber,
                previous.orElseThrow().getEquivalentCuratedRevision()
            );
        }
        PracticeRevision saved = practiceRevisionRepository.save(revision);
        practice.setCurrentRevision(saved);
        practiceRepository.save(practice);
        return saved;
    }

    @Transactional(readOnly = true)
    public @Nullable Integer currentRevisionNumber(Practice practice) {
        return practice.getCurrentRevision() == null ? null : practice.getCurrentRevision().getRevisionNumber();
    }
}
