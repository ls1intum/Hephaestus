package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeReviewVolumeQuery;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class PracticeReviewVolumeQueryAdapter implements PracticeReviewVolumeQuery {

    private final AgentJobRepository repository;

    @Override
    @Transactional(readOnly = true)
    public int countSince(long workspaceId, Instant since) {
        return Math.toIntExact(repository.countByWorkspaceIdAndPurposeAndCreatedAtGreaterThanEqual(
                workspaceId, AgentPurpose.PRACTICE_REVIEW, since));
    }
}
