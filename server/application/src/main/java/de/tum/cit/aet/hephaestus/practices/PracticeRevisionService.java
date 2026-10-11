package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeRevisionDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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

    @Transactional
    public PracticeRevision forReview(Practice practice) {
        return pin(practice).revision();
    }

    private ReviewRevision pin(Practice practice) {
        Practice locked = practiceRepository
                .findByIdForUpdate(practice.getId())
                .orElseThrow(() -> new EntityNotFoundException("Practice", String.valueOf(practice.getId())));
        PracticeRevision current = locked.getCurrentRevision();
        if (current != null && ReviewRuleFingerprint.isCurrentScheme(current.getReviewRuleFingerprint())) {
            return new ReviewRevision(current, false);
        }
        return new ReviewRevision(appendLocked(locked), true);
    }

    /** The revision a review pins for one practice, and whether asking for it appended it. */
    public record ReviewRevision(PracticeRevision revision, boolean appended) {}

    /** {@link #forReview(Practice)} for each practice of the workspace with one of these slugs. */
    @Transactional
    public List<ReviewRevision> forReview(Long workspaceId, Collection<String> slugs) {
        return practiceRepository.findByWorkspaceIdAndSlugIn(workspaceId, slugs).stream()
                .map(this::pin)
                .toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PracticeRevision append(Practice practice) {
        // Lock only: the revision snapshots the caller's practice, not the row as stored.
        if (practiceRepository.findByIdForUpdate(practice.getId()).isEmpty()) {
            throw new EntityNotFoundException("Practice", String.valueOf(practice.getId()));
        }
        return appendLocked(practice);
    }

    private PracticeRevision appendLocked(Practice practice) {
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
        return practice.getCurrentRevision() == null
                ? null
                : practice.getCurrentRevision().getRevisionNumber();
    }

    /**
     * Every stored version of the practice, newest first, each with the parts of the definition that differ from the
     * version before it.
     */
    @Transactional(readOnly = true)
    public List<PracticeRevisionDTO> history(Practice practice) {
        List<PracticeRevision> revisions =
                practiceRevisionRepository.findByPracticeIdOrderByRevisionNumberAsc(practice.getId());
        List<PracticeRevisionDTO> history = new ArrayList<>(revisions.size());
        PracticeRevision previous = null;
        for (PracticeRevision revision : revisions) {
            history.add(new PracticeRevisionDTO(
                    Objects.requireNonNull(revision.getId(), "a stored revision has an id"),
                    revision.getRevisionNumber(),
                    Objects.requireNonNull(revision.getCreatedAt(), "a stored revision has a creation time"),
                    previous == null ? List.of() : changed(previous, revision),
                    previous != null
                            && !Objects.equals(
                                    previous.getReviewRuleFingerprint(), revision.getReviewRuleFingerprint())));
            previous = revision;
        }
        return history.reversed();
    }

    /** The parts that differ, in {@link PracticeDefinitionField} order; every field is compared. */
    private static List<PracticeDefinitionField> changed(PracticeRevision before, PracticeRevision after) {
        return Arrays.stream(PracticeDefinitionField.values())
                .filter(field -> !Objects.equals(value(field, before), value(field, after)))
                .toList();
    }

    /**
     * One part of a stored version. Exhaustive like {@code PracticeDefinitionMerge#value}, so a new definition field
     * fails the build here rather than going missing from the version history.
     */
    private static @Nullable Object value(PracticeDefinitionField field, PracticeRevision revision) {
        return switch (field) {
            case NAME -> revision.getName();
            case SIGNALS -> revision.getSignals();
            case EVIDENCE_REQUIREMENTS -> revision.getEvidenceRequirements();
            case REVIEW_WHEN -> revision.getReviewWhen();
            case SUBJECT -> revision.getSubject();
            case PRECONDITION -> revision.getPrecondition();
            case CRITERIA -> revision.getCriteria();
            case PRACTICE_SCRIPT -> revision.getPracticeScript();
            case AUTOMATED_REVIEW_POLICY -> revision.getAutomatedReviewPolicy();
            case WHY_IT_MATTERS -> revision.getWhyItMatters();
            case WHAT_GOOD_LOOKS_LIKE -> revision.getWhatGoodLooksLike();
            case GROUP_SLUG -> revision.getGroupSlug();
            case DELIVERY_BEHAVIOR -> revision.getDeliveryBehavior();
            case VISUAL -> revision.getVisual();
            case GUIDE -> revision.getGuide();
        };
    }
}
