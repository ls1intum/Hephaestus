package de.tum.cit.aet.hephaestus.practices.acrossworkspace;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto.PracticeWorkspaceOverviewDTO;
import de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto.PracticeWorkspaceTileDTO;
import de.tum.cit.aet.hephaestus.practices.dto.GroupRefDTO;
import de.tum.cit.aet.hephaestus.practices.result.ResultReadQuery;
import de.tum.cit.aet.hephaestus.practices.result.ResultViewQuery;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.result.trend.PracticeTrendService;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewCoverageService;
import de.tum.cit.aet.hephaestus.practices.review.dto.OutcomeVectorDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class PracticesAcrossWorkspaceService {
    private final PracticeRepository practices;
    private final PracticeReviewCoverageService scope;
    private final ResultReadQuery read;
    private final ResultViewQuery views;
    private final ResultVisibilityPolicy visibility;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PracticeWorkspaceOverviewDTO read(WorkspaceContext ws) {
        var developers = scope.eligibleAccountIds(ws.id());
        var rows = read.forDevelopersSince(
                ws.id(), developers, clock.instant().minus(Duration.ofDays(PracticeTrendService.LOOKBACK_DAYS)), true);
        var permitted = visibility.permitsAll(ws.id(), rows, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        var byPractice =
                views
                        .views(
                                ws.id(),
                                rows.stream()
                                        .filter(r -> permitted.contains(r.getId()))
                                        .toList())
                        .stream()
                        .collect(Collectors.groupingBy(v -> v.practice().getId()));
        var tiles = practices.findByWorkspaceId(ws.id()).stream()
                .sorted(Comparator.comparingInt(p -> p.getPosition()))
                .map(p -> {
                    var evidence = byPractice.getOrDefault(p.getId(), List.of());
                    var counts = new EnumMap<ResultOutcome, Integer>(ResultOutcome.class);
                    evidence.forEach(v -> counts.merge(v.result().getOutcome(), 1, Integer::sum));
                    var group = p.getGroup();
                    return new PracticeWorkspaceTileDTO(
                            p.getSlug(),
                            Objects.requireNonNull(p.getCurrentVersion().getName()),
                            group == null
                                    ? null
                                    : new GroupRefDTO(
                                            group.getSlug(), group.getName(), group.getIcon(), group.getColor()),
                            (int) evidence.stream()
                                    .map(v -> v.result().getDeveloperId())
                                    .distinct()
                                    .count(),
                            new OutcomeVectorDTO(
                                    counts.getOrDefault(ResultOutcome.MET, 0),
                                    counts.getOrDefault(ResultOutcome.NOT_MET, 0),
                                    counts.getOrDefault(ResultOutcome.DOES_NOT_APPLY, 0),
                                    counts.getOrDefault(ResultOutcome.COULD_NOT_DETERMINE, 0)));
                })
                .toList();
        return new PracticeWorkspaceOverviewDTO(developers.size(), tiles);
    }
}
