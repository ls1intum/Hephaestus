package de.tum.cit.aet.hephaestus.practices.trace;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.result.ResultPresentation;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultViewQuery;
import de.tum.cit.aet.hephaestus.practices.review.ReviewSummaryQueryService;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.trace.dto.OwnItemEventDTO;
import de.tum.cit.aet.hephaestus.practices.trace.dto.OwnItemTraceDTO;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnItemTraceQueryService {
    private final ItemTraceQueryService query;
    private final ReviewSummaryQueryService reviewQuery;
    private final ResultRepository results;
    private final ResultViewQuery views;
    private final ResultPresentation presentation;
    private final CurrentDeveloperLookup developer;
    private final EvidenceAuthorization authorization;

    public OwnItemTraceDTO own(long ws, long itemId, UUID reviewId) {
        var item = query.requireItem(ws, itemId);
        var review = reviewQuery.require(ws, reviewId);
        long account = developer.currentDeveloperIdElseThrow();
        var found = results.findByWorkspaceIdAndReviewIdOrderByRecordedAtAscIdAsc(ws, reviewId).stream()
                .filter(r -> Objects.equals(r.getDeveloperId(), account) && r.getItemId() == itemId)
                .toList();
        var permitted = authorization.permitsAll(ws, found, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        var visible = found.stream().filter(r -> permitted.contains(r.getId())).toList();
        if (visible.isEmpty()) throw new EntityNotFoundException("Review", reviewId.toString());
        return new OwnItemTraceDTO(
                item,
                reviewQuery.ref(review),
                query.events(ws, itemId, PageRequest.ofSize(100)).stream()
                        .map(e -> new OwnItemEventDTO(e.name(), e.occurredAt()))
                        .toList(),
                presentation.lists(ws, views.views(ws, visible), permitted));
    }
}
