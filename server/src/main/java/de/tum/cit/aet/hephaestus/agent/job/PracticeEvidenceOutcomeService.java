package de.tum.cit.aet.hephaestus.agent.job;

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
     * enough that the query stays a bounded scan rather than growing with the workspace's history.
     */
    static final int REVIEW_WINDOW = 200;

    private final AgentJobRepository agentJobRepository;

    @Transactional(readOnly = true)
    public List<PracticeEvidenceOutcomeDTO> recentOutcomes(Long workspaceId) {
        Map<String, List<PracticeEvidenceBlockDTO>> blocks = new LinkedHashMap<>();
        for (var row : agentJobRepository.countReadinessBlocksByPractice(workspaceId, REVIEW_WINDOW)) {
            blocks
                .computeIfAbsent(row.getPracticeSlug(), slug -> new ArrayList<>())
                .add(new PracticeEvidenceBlockDTO(row.getSourceKind(), row.getReasonCode(), (int) row.getReviews()));
        }
        return agentJobRepository
            .countReadinessByPractice(workspaceId, REVIEW_WINDOW)
            .stream()
            .map(row ->
                new PracticeEvidenceOutcomeDTO(
                    row.getPracticeSlug(),
                    (int) row.getConsideredReviews(),
                    (int) row.getReviewedCount(),
                    blocks.getOrDefault(row.getPracticeSlug(), List.of())
                )
            )
            .sorted((left, right) -> left.practiceSlug().compareTo(right.practiceSlug()))
            .toList();
    }
}
