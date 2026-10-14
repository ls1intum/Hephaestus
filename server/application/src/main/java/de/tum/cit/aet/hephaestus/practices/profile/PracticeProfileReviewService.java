package de.tum.cit.aet.hephaestus.practices.profile;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.profile.dto.ProfileReviewDTO;
import de.tum.cit.aet.hephaestus.practices.profile.dto.ProfileReviewDetailDTO;
import de.tum.cit.aet.hephaestus.practices.result.ResultPresentation;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultView;
import de.tum.cit.aet.hephaestus.practices.result.ResultViewQuery;
import de.tum.cit.aet.hephaestus.practices.review.ReviewSummaryQueryService;
import de.tum.cit.aet.hephaestus.practices.review.dto.OutcomeVectorDTO;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.trace.OwnItemTraceQueryService;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PracticeProfileReviewService {
    private final CurrentDeveloperLookup developer;
    private final ResultRepository results;
    private final ResultViewQuery views;
    private final ResultPresentation presentation;
    private final ReviewSummaryQueryService reviews;
    private final OwnItemTraceQueryService traces;
    private final EvidenceAuthorization authorization;

    public Page<ProfileReviewDTO> list(
            WorkspaceContext ws,
            @Nullable String itemKind,
            @Nullable Long itemId,
            @Nullable String groupSlug,
            @Nullable Instant since,
            Pageable page) {
        var account = developer.currentDeveloperId();
        if (account.isEmpty()) return Page.empty(page);
        var rows = results.forDevelopersSince(
                ws.id(), List.of(account.get()), since == null ? Instant.EPOCH : since, false);
        var permitted = authorization.permitsAll(ws.id(), rows, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        var byReview = new LinkedHashMap<UUID, List<ResultView>>();
        views
                .views(
                        ws.id(),
                        rows.stream().filter(r -> permitted.contains(r.getId())).toList())
                .stream()
                .filter(v -> itemKind == null || v.item().kind().equals(itemKind))
                .filter(v -> itemId == null || v.result().getItemId() == itemId)
                .filter(v -> groupSlug == null
                        || (v.practice().getGroup() != null
                                && v.practice().getGroup().getSlug().equals(groupSlug)))
                .forEach(v -> byReview.computeIfAbsent(v.result().getReviewId(), id -> new ArrayList<>())
                        .add(v));
        var ordered = byReview.values().stream()
                .sorted(Comparator.comparing(
                                (List<ResultView> v) -> v.getFirst().review().getCreatedAt())
                        .thenComparing(v -> v.getFirst().review().getId().toString())
                        .reversed())
                .toList();
        return new PageImpl<>(
                ordered.stream()
                        .skip(page.getOffset())
                        .limit(page.getPageSize())
                        .map(v -> dto(v))
                        .toList(),
                page,
                ordered.size());
    }

    public ProfileReviewDetailDTO get(WorkspaceContext ws, UUID id) {
        long account = developer.currentDeveloperIdElseThrow();
        var rows = results.findByWorkspaceIdAndReviewIdOrderByRecordedAtAscIdAsc(ws.id(), id).stream()
                .filter(r -> Objects.equals(r.getDeveloperId(), account))
                .toList();
        var permitted = authorization.permitsAll(ws.id(), rows, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        var visible = rows.stream().filter(r -> permitted.contains(r.getId())).toList();
        if (visible.isEmpty()) throw new EntityNotFoundException("Review", id.toString());
        var projected = views.views(ws.id(), visible);
        var dto = dto(projected);
        var counts = ReviewSummaryQueryService.counts(visible);
        return new ProfileReviewDetailDTO(
                dto.review(),
                dto.results(),
                dto.feedback(),
                traces.own(ws.id(), projected.getFirst().result().getItemId(), id),
                new OutcomeVectorDTO(counts.met(), counts.notMet(), counts.doesNotApply(), counts.couldNotDetermine()));
    }

    private ProfileReviewDTO dto(List<ResultView> v) {
        return new ProfileReviewDTO(
                reviews.ref(v.getFirst().review()),
                presentation.lists(
                        v.getFirst().result().getWorkspaceId(),
                        v,
                        v.stream().map(r -> r.result().getId()).collect(Collectors.toSet())),
                v.stream()
                        .flatMap(r -> presentation.ownFeedback(r.result()).stream())
                        .distinct()
                        .toList());
    }
}
