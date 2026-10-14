package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.result.dto.GoingWellEvidenceDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.GoingWellPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.result.trend.WorkResolution;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.practices.spi.Currentness;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class GoingWellQueryService {
    private final PracticeStandingService standings;
    private final CurrentDeveloperLookup developer;
    private final JsonMapper json;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<GoingWellPracticeDTO> own(long workspaceId) {
        var account = developer.currentDeveloperId();
        if (account.isEmpty()) return List.of();
        var snapshot = standings
                .getStandingSnapshots(workspaceId, account.orElseThrow(), List.of(clock.instant()))
                .getFirst();
        return snapshot.practices().values().stream()
                .filter(p -> p.isHolding(standings.standingWindow()))
                .map(p -> {
                    var clean = p.trend().cleanWork();
                    var proof = p.evidence().stream()
                            .filter(v -> clean.reviewIds().contains(v.result().getReviewId())
                                    && v.result().getOutcome() == ResultOutcome.MET
                                    && v.result().getEndedAt() == null
                                    && v.result().getEvidenceVersion() == 1
                                    && v.version() != null
                                    && ResultVisibilityPolicy.currentness(
                                                    v.result(),
                                                    v.version(),
                                                    v.practice().getCurrentVersion())
                                            == Currentness.CURRENT)
                            .sorted(Comparator.comparing(
                                            (ResultView v) -> v.result().getRecordedAt())
                                    .reversed()
                                    .thenComparing(v -> v.result().getId().toString()))
                            .toList();
                    if (proof.stream()
                                    .map(v -> v.result().getItemId())
                                    .distinct()
                                    .count()
                            < WorkResolution.CLEAN_NEEDED) return Optional.<GoingWellPracticeDTO>empty();
                    var practice = proof.getFirst().practice();
                    var definition =
                            json.convertValue(practice.getCurrentVersion().getDefinition(), PracticeDefinition.class);
                    var evidence = proof.stream()
                            .limit(5)
                            .map(v -> new GoingWellEvidenceDTO(
                                    v.result().getId(),
                                    v.result().getReviewId(),
                                    v.item(),
                                    v.result().getRecordedAt()))
                            .toList();
                    return Optional.of(new GoingWellPracticeDTO(
                            practice.getSlug(),
                            definition.name(),
                            practice.getGroup() == null
                                    ? null
                                    : practice.getGroup().getSlug(),
                            definition.guidance().goingWell(),
                            proof.getLast().result().getRecordedAt(),
                            Math.toIntExact(proof.stream()
                                    .map(v -> v.result().getItemId())
                                    .distinct()
                                    .count()),
                            evidence,
                            proof.size() > evidence.size()));
                })
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(GoingWellPracticeDTO::practiceName)
                        .thenComparing(GoingWellPracticeDTO::practiceSlug))
                .toList();
    }
}
