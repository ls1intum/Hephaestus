package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackQueryRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.PreviousPracticePageFeedback;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnCaptureRole
public class PracticePageSupportReader {
    private static final Duration LOOKBACK = Duration.ofDays(90);
    private final ResultRepository results;
    private final Clock clock;
    private final PreviousPracticePageFeedback previous;
    private final PracticeRepository practices;
    private final FeedbackQueryRepository queries;

    public record Selection(Optional<PreviousPracticePageFeedback.Previous> previous, List<Result> evidence) {
        public Selection {
            evidence = List.copyOf(evidence);
        }
    }

    @Transactional(readOnly = true)
    public Selection select(long workspaceId, long developerId, long practiceId, Instant now) {
        var practice = practices.findByIdAndWorkspaceId(practiceId, workspaceId).orElseThrow();
        var prior = previous.find(workspaceId, developerId, practice.getSlug(), now);
        var window = now.minus(LOOKBACK);
        var since = prior.map(card -> card.nextEvidenceSince(window)).orElse(window);
        return new Selection(
                prior,
                candidateResults(workspaceId, developerId).stream()
                        .filter(row -> row.getPracticeId() == practiceId
                                && row.getRecordedAt().isAfter(since))
                        .toList());
    }

    @Transactional(readOnly = true)
    public boolean isBackfillOnly(long workspaceId, List<Result> support) {
        if (support.isEmpty()) return false;
        if (support.stream().anyMatch(row -> row.getWorkspaceId() != workspaceId))
            throw new IllegalArgumentException("Support must belong to this workspace.");
        var reviewIds = support.stream().map(Result::getReviewId).distinct().toList();
        var origins = queries.reviewOrigins(workspaceId, reviewIds);
        if (origins.size() != reviewIds.size())
            throw new NoSuchElementException("Selected support has missing review authority.");
        return origins.stream().allMatch(origin -> origin == ReviewOrigin.BACKFILL);
    }

    private List<Result> candidateResults(long workspaceId, long developerId) {
        var since = clock.instant().minus(LOOKBACK);
        return results
                .findCurrentForDeveloper(workspaceId, developerId, ResultMarkKind.INCORRECT, PageRequest.of(0, 100))
                .getContent()
                .stream()
                .filter(row -> row.getOutcome() == ResultOutcome.NOT_MET
                        && row.getEvidenceVersion() == 1
                        && !row.getRecordedAt().isBefore(since))
                .toList();
    }
}
