package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.PreviousPracticePageFeedback;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private final EvidenceAuthorization evidence;
    private final Clock clock;
    private final PreviousPracticePageFeedback previous;
    private final PracticeRepository practices;

    public record Selection(Optional<PreviousPracticePageFeedback.Previous> previous, List<Result> evidence) {
        public Selection {
            evidence = List.copyOf(evidence);
        }
    }

    @Transactional(readOnly = true)
    public Selection select(long workspaceId, long developerId, long practiceId, Instant now) {
        var practice = practices
                .findById(practiceId)
                .filter(p -> p.getWorkspaceId() == workspaceId)
                .orElseThrow();
        var prior = previous.find(workspaceId, developerId, practice.getSlug(), now);
        var window = now.minus(LOOKBACK);
        var since = prior.map(card -> card.nextEvidenceSince(window)).orElse(window);
        return new Selection(
                prior,
                read(workspaceId, developerId).stream()
                        .filter(row -> row.getPracticeId() == practiceId
                                && row.getRecordedAt().isAfter(since))
                        .toList());
    }

    @Transactional(readOnly = true)
    public List<Result> read(long workspaceId, long developerId) {
        var since = clock.instant().minus(LOOKBACK);
        var rows = results
                .findCurrentForDeveloper(workspaceId, developerId, ResultMarkKind.INCORRECT, PageRequest.of(0, 100))
                .getContent()
                .stream()
                .filter(row -> row.getOutcome() == ResultOutcome.NOT_MET
                        && row.getEvidenceVersion() == 1
                        && !row.getRecordedAt().isBefore(since))
                .toList();
        var permitted = evidence.permitsForNewDelivery(workspaceId, rows, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        return rows.stream().filter(row -> permitted.contains(row.getId())).toList();
    }
}
