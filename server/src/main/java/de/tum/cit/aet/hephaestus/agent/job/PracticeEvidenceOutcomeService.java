package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.PracticeReadinessRow;
import de.tum.cit.aet.hephaestus.agent.job.PracticeEvidenceOutcomeDTO.PracticeEvidenceBlockDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads back the readiness decisions recorded on recent reviews, grouped by practice. */
@Service
@RequiredArgsConstructor
public class PracticeEvidenceOutcomeService {

    /**
     * Reviews looked at. Large enough that a weekly-cadence practice still has something to show, small
     * enough that the query stays a bounded scan rather than growing with the workspace's history. Also
     * what keeps the counts inside an {@code int}.
     */
    static final int REVIEW_WINDOW = 200;

    private final AgentJobRepository agentJobRepository;

    @Transactional(readOnly = true)
    public List<PracticeEvidenceOutcomeDTO> recentOutcomes(Long workspaceId) {
        Map<String, PracticeEvidenceOutcomeDTO> byPractice = new LinkedHashMap<>();
        for (PracticeReadinessRow row : agentJobRepository.findReadinessOutcomes(workspaceId, REVIEW_WINDOW)) {
            PracticeEvidenceOutcomeDTO outcome = byPractice.computeIfAbsent(row.getPracticeSlug(), slug ->
                new PracticeEvidenceOutcomeDTO(
                    slug,
                    (int) row.getConsideredReviews(),
                    (int) row.getReviewedCount(),
                    new ArrayList<>()
                )
            );
            // The left join repeats each practice's counts once per blocking reason, and leaves the
            // reason columns null for a practice that never skipped.
            if (row.getReasonCode() != null) {
                outcome
                    .skippedBecause()
                    .add(
                        new PracticeEvidenceBlockDTO(row.getSourceKind(), row.getReasonCode(), (int) row.getReviews())
                    );
            }
        }
        return List.copyOf(byPractice.values());
    }
}
