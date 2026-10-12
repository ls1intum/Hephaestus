package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResultInvalidation;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * A workspace admin's correction of one result, and its reversal. Neither touches the result, and
 * restoring does not re-send feedback the invalidation stopped: that feedback was withheld for a reason that
 * was true when it was recorded.
 */
@Service
@RequiredArgsConstructor
public class PracticeResultInvalidationService {

    private final PracticeResultRepository practiceResultRepository;
    private final PracticeResultInvalidationRepository invalidationRepository;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackDispatchRepository dispatchRepository;
    private final Clock clock;

    @Transactional
    public void setValidity(long workspaceId, UUID practiceResultId, long accountId, boolean valid, String reason) {
        PracticeResult practiceResult = practiceResultRepository
                .lockByIdAndWorkspaceId(practiceResultId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("PracticeResult", practiceResultId.toString()));
        if (!valid && dispatchRepository.existsInFlightCiting(workspaceId, practiceResultId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hephaestus is delivering feedback that cites this result right now. Try again in a moment.");
        }
        var active = invalidationRepository.findActive(workspaceId, practiceResultId);
        if (valid) {
            active.orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.CONFLICT, "This result is not invalidated."))
                    .restore(accountId, reason.strip(), clock.instant());
            return;
        }
        if (active.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This result is already invalidated.");
        }
        invalidationRepository.save(
                new PracticeResultInvalidation(practiceResult, accountId, reason.strip(), clock.instant()));
        feedbackRepository.suppressUndeliveredCiting(
                workspaceId, practiceResultId, FeedbackSuppressionReason.OBSERVATION_INVALIDATED.name());
    }
}
