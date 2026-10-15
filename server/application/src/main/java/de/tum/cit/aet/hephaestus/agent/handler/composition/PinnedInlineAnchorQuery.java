package de.tum.cit.aet.hephaestus.agent.handler.composition;

import de.tum.cit.aet.hephaestus.agent.job.ReviewCompositionBasisRepository;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedResultQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewInlineAnchorQuery;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@ConditionalOnCaptureRole
public class PinnedInlineAnchorQuery implements ReviewInlineAnchorQuery {
    private final ReviewCompositionBasisRepository bases;
    private final ReviewJobRepository jobs;
    private final AdmittedResultQuery results;
    private final ObjectMapper mapper;

    public @Nullable PinnedInlineAnchors placement(ReviewJob job) {
        var retained = bases.findByWorkspaceIdAndJobId(job.getWorkspaceId(), job.getId())
                .orElse(null);
        if (retained == null) return null;
        var change = retained.getPayload().path("files").path("context/change.json");
        if (!change.isString()) return null;
        try {
            return new PinnedInlineAnchors(mapper.readTree(Base64.getDecoder().decode(change.asString())));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    @Override
    public boolean hasInlineAnchor(long workspaceId, UUID reviewId, UUID resultId) {
        for (var job : jobs.findByReviewIdAndWorkspaceIdOrderByAvailableAtAsc(reviewId, workspaceId)) {
            var attempt = job.getAdmissionAttempt();
            var placement = placement(job);
            if (attempt == null || placement == null) continue;
            if (results.forAttempt(workspaceId, reviewId, job.getId(), attempt).stream()
                    .filter(row -> row.id().equals(resultId))
                    .flatMap(row -> row.problems().stream())
                    .flatMap(problem -> problem.candidate().citations().stream())
                    .anyMatch(placement::permits)) return true;
        }
        return false;
    }
}
