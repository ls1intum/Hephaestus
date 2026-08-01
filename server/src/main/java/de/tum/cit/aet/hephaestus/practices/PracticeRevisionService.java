package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
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

    /**
     * Records the practice's current definition as the next revision.
     *
     * <p>The revision's fingerprint is what later says whether this copy still matches the catalog —
     * derived from the definition itself, so editing only what people read keeps the match, editing
     * the detection criteria drops it, and editing them back restores it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PracticeRevision append(Practice practice) {
        practiceRepository
            .findByIdForUpdate(practice.getId())
            .orElseThrow(() -> new EntityNotFoundException("Practice", String.valueOf(practice.getId())));
        int revisionNumber = practiceRevisionRepository
            .findFirstByPracticeIdOrderByRevisionNumberDesc(practice.getId())
            .map(revision -> revision.getRevisionNumber() + 1)
            .orElse(1);
        PracticeRevision saved = practiceRevisionRepository.save(new PracticeRevision(practice, revisionNumber));
        practice.setCurrentRevision(saved);
        practiceRepository.save(practice);
        return saved;
    }

    @Transactional(readOnly = true)
    public @Nullable Integer currentRevisionNumber(Practice practice) {
        return practice.getCurrentRevision() == null ? null : practice.getCurrentRevision().getRevisionNumber();
    }
}
