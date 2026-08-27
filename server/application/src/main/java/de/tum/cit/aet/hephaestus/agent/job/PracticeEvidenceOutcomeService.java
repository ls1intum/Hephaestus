package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.job.PracticeEvidenceOutcomeDTO.PracticeEvidenceBlockerDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Reads back the readiness decisions recorded on recent reviews, grouped by practice. */
@Service
@RequiredArgsConstructor
public class PracticeEvidenceOutcomeService {

    /**
     * Reviews looked at. Large enough that a weekly-cadence practice still has something to show, small
     * enough that the query stays a bounded scan rather than growing with the workspace's history.
     */
    static final int REVIEW_WINDOW = 200;

    private static final TypeReference<List<PracticeEvidenceBlockerDTO>> BLOCKERS = new TypeReference<>() {};

    private final AgentJobRepository agentJobRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<PracticeEvidenceOutcomeDTO> recentOutcomes(Long workspaceId) {
        return agentJobRepository
            .findReadinessOutcomes(workspaceId, REVIEW_WINDOW)
            .stream()
            .map(row ->
                new PracticeEvidenceOutcomeDTO(
                    row.getPracticeSlug(),
                    row.getConsideredReviews(),
                    row.getReviewedCount(),
                    objectMapper.readValue(row.getBlockersObserved(), BLOCKERS)
                )
            )
            .toList();
    }
}
