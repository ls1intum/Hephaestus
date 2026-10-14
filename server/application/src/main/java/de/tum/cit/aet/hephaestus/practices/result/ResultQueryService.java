package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.dto.DeveloperPracticeSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.ResultDetailDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.ResultListDTO;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResultQueryService {
    private final CurrentDeveloperLookup developer;
    private final ResultRepository results;
    private final ResultReadQuery read;
    private final ResultViewQuery views;
    private final ResultPresentation presentation;
    private final EvidenceAuthorization authorization;

    public Page<ResultListDTO> listOwnResults(long ws, ResultFeedQuery query, Pageable page) {
        long developer = this.developer.currentDeveloperIdElseThrow();
        var filter = new ResultQueryFilter(
                null,
                query.itemId(),
                query.itemKind(),
                query.practiceSlug(),
                query.groupSlug(),
                developer,
                query.outcome() == null ? null : List.of(query.outcome()),
                query.severities(),
                null,
                null,
                null,
                null,
                null,
                null);
        var rows = read.list(ws, filter, query.sort(), query.displayableOnly(), page);
        var permitted = authorization.permitsAll(ws, rows.getContent(), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        var projected = views.views(ws, rows.getContent()).stream()
                .map(v -> presentation.list(v, permitted.contains(v.result().getId())))
                .toList();
        return new PageImpl<>(projected, page, rows.getTotalElements());
    }

    public List<DeveloperPracticeSummaryDTO> ownSummary(long ws) {
        long developer = this.developer.currentDeveloperIdElseThrow();
        var rows = read.forDevelopersSince(ws, List.of(developer), Instant.EPOCH, false);
        var grouped = views.views(ws, rows).stream()
                .collect(Collectors.groupingBy(v -> v.practice().getSlug()));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    var v = e.getValue();
                    var c = new EnumMap<ResultOutcome, Integer>(ResultOutcome.class);
                    v.forEach(r -> c.merge(r.result().getOutcome(), 1, Integer::sum));
                    return new DeveloperPracticeSummaryDTO(
                            e.getKey(),
                            presentation.name(v.getFirst()),
                            v.size(),
                            c.getOrDefault(ResultOutcome.MET, 0),
                            c.getOrDefault(ResultOutcome.NOT_MET, 0),
                            c.getOrDefault(ResultOutcome.DOES_NOT_APPLY, 0),
                            c.getOrDefault(ResultOutcome.COULD_NOT_DETERMINE, 0),
                            v.stream()
                                    .map(r -> r.result().getRecordedAt())
                                    .max(Instant::compareTo)
                                    .orElse(null));
                })
                .toList();
    }

    public ResultDetailDTO ownResult(long ws, UUID id) {
        long developer = this.developer.currentDeveloperIdElseThrow();
        var r = results.findByIdAndDeveloperIdAndWorkspaceId(id, developer, ws)
                .orElseThrow(() -> new EntityNotFoundException("Result", id.toString()));
        return presentation.detail(
                views.views(ws, List.of(r)).getFirst(),
                authorization.permits(ws, r, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY));
    }

    public List<ResultDetailDTO> details(long ws, long developer, List<Result> rows, Set<UUID> permitted) {
        return views
                .views(
                        ws,
                        rows.stream()
                                .filter(r -> Objects.equals(r.getDeveloperId(), developer))
                                .toList())
                .stream()
                .map(v -> presentation.detail(v, permitted.contains(v.result().getId())))
                .toList();
    }
}
