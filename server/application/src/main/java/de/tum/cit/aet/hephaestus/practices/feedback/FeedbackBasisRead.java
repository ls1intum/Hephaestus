package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackBasisDTO;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultViewQuery;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewResultQueryService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackBasisRead {
    private final FeedbackBasisRepository basis;
    private final ResultRepository results;
    private final ResultViewQuery views;
    private final ResultVisibilityPolicy evidence;
    private final ReviewResultQueryService presentation;

    public record Snapshot(List<FeedbackBasis> edges, List<Result> results, Set<UUID> permitted, boolean readable) {
        public Snapshot {
            edges = List.copyOf(edges);
            results = List.copyOf(results);
            permitted = Set.copyOf(permitted);
        }
    }

    public Snapshot read(long workspaceId, UUID feedbackId) {
        var edges = basis.findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(workspaceId, feedbackId);
        var ids = edges.stream().map(FeedbackBasis::getResultId).distinct().toList();
        var support = results.findByWorkspaceIdAndIdIn(workspaceId, ids);
        var permitted = evidence.permitsHistory(workspaceId, support, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        return new Snapshot(
                edges,
                support,
                permitted,
                !edges.isEmpty() && support.size() == ids.size() && permitted.containsAll(ids));
    }

    public List<FeedbackBasisDTO> bound(long workspaceId, Snapshot snapshot) {
        var resultViews = views.views(workspaceId, snapshot.results()).stream()
                .collect(Collectors.toMap(view -> view.result().getId(), view -> view));
        return snapshot.edges().stream()
                .map(edge -> new FeedbackBasisDTO(
                        edge.getResultId(),
                        edge.getProblemId(),
                        edge.getOrdinal(),
                        presentation.bound(
                                Objects.requireNonNull(resultViews.get(edge.getResultId())),
                                snapshot.permitted().contains(edge.getResultId()))))
                .toList();
    }
}
