package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeVersion;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.spi.Currentness;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ResultVisibilityPolicy {

    private final EvidenceAuthorization evidenceAuthorization;
    private final ResultMarkRepository marks;
    private final PracticeRepository practices;
    private final PracticeVersionRepository versions;

    public Set<UUID> permitsAll(long workspaceId, Collection<Result> results, SourceUsePurpose purpose) {
        return permitted(workspaceId, results, purpose, false);
    }

    public Set<UUID> permitsForNewDelivery(long workspaceId, Collection<Result> results, SourceUsePurpose purpose) {
        return permitted(workspaceId, results, purpose, true);
    }

    public Set<UUID> permitsShown(long workspaceId, Collection<Result> results, SourceUsePurpose purpose) {
        List<Result> owned = results.stream()
                .filter(result -> result.getWorkspaceId() == workspaceId)
                .toList();
        Map<Long, PracticeVersion> pinned = pinnedVersions(workspaceId, owned);
        List<Result> verifiable = owned.stream()
                .filter(result -> result.getPracticeVersionId() != null)
                .filter(result -> pinned.containsKey(result.getPracticeVersionId()))
                .toList();
        return evidenceAuthorization.permitsAll(workspaceId, withoutIncorrect(workspaceId, verifiable), purpose);
    }

    public Set<UUID> permitsHistory(long workspaceId, Collection<Result> results, SourceUsePurpose purpose) {
        return evidenceAuthorization.permitsAll(
                workspaceId,
                results.stream()
                        .filter(result -> result.getWorkspaceId() == workspaceId)
                        .toList(),
                purpose);
    }

    public static Currentness currentness(Result result, @Nullable PracticeVersion pinned, PracticeVersion current) {
        if (result.getEndedAt() != null) {
            return Currentness.STALE;
        }
        if (pinned == null
                || result.getEvidenceVersion() == 0
                || result.getSummary() == null
                || result.getExplanation() == null
                || result.getEvidence() == null
                || result.getCoverage() == null) {
            return Currentness.UNVERIFIABLE;
        }
        return pinned.getReviewDigest().equals(current.getReviewDigest()) ? Currentness.CURRENT : Currentness.STALE;
    }

    public Set<UUID> permitsAt(long workspaceId, Collection<Result> rows, SourceUsePurpose purpose, Instant at) {
        var owned = rows.stream()
                .filter(r -> r.getWorkspaceId() == workspaceId
                        && !r.getRecordedAt().isAfter(at)
                        && (r.getEndedAt() == null || r.getEndedAt().isAfter(at)))
                .toList();
        var pinned = pinnedVersions(workspaceId, owned);
        var current = practices.findByWorkspaceId(workspaceId).stream()
                .collect(Collectors.toMap(Practice::getId, Function.identity()));
        var incorrect = owned.stream()
                .filter(r ->
                        marks.findByWorkspaceIdAndResultIdOrderByCreatedAtDescIdDesc(workspaceId, r.getId()).stream()
                                .anyMatch(m -> m.getKind() == ResultMarkKind.INCORRECT
                                        && !m.getCreatedAt().isAfter(at)
                                        && (m.getRemovedAt() == null
                                                || m.getRemovedAt().isAfter(at))))
                .map(Result::getId)
                .collect(Collectors.toSet());
        return evidenceAuthorization.permitsAll(
                workspaceId,
                owned.stream()
                        .filter(r -> !incorrect.contains(r.getId())
                                && r.getEvidenceVersion() == 1
                                && pinned.containsKey(r.getPracticeVersionId())
                                && current.containsKey(r.getPracticeId())
                                && pinned.get(r.getPracticeVersionId())
                                        .getReviewDigest()
                                        .equals(current.get(r.getPracticeId())
                                                .getCurrentVersion()
                                                .getReviewDigest()))
                        .toList(),
                purpose);
    }

    private Set<UUID> permitted(
            long workspaceId, Collection<Result> results, SourceUsePurpose purpose, boolean newDelivery) {
        List<Result> owned = results.stream()
                .filter(result -> result.getWorkspaceId() == workspaceId)
                .toList();
        if (owned.isEmpty()) {
            return Set.of();
        }
        Map<Long, PracticeVersion> pinned = pinnedVersions(workspaceId, owned);
        Map<Long, Practice> current = practices.findByWorkspaceId(workspaceId).stream()
                .collect(Collectors.toMap(practice -> Objects.requireNonNull(practice.getId()), Function.identity()));
        List<Result> eligible = owned.stream()
                .filter(result -> {
                    Practice practice = current.get(result.getPracticeId());
                    return practice != null
                            && currentness(
                                            result,
                                            pinned.get(result.getPracticeVersionId()),
                                            practice.getCurrentVersion())
                                    == Currentness.CURRENT;
                })
                .toList();
        List<Result> unmarked = withoutIncorrect(workspaceId, eligible);
        return newDelivery
                ? evidenceAuthorization.permitsForNewDelivery(workspaceId, unmarked, purpose)
                : evidenceAuthorization.permitsAll(workspaceId, unmarked, purpose);
    }

    private Map<Long, PracticeVersion> pinnedVersions(long workspaceId, Collection<Result> results) {
        Set<Long> ids = results.stream()
                .map(Result::getPracticeVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return versions.findAllById(ids).stream()
                .filter(version -> version.getWorkspaceId() == workspaceId)
                .collect(Collectors.toMap(version -> Objects.requireNonNull(version.getId()), Function.identity()));
    }

    private List<Result> withoutIncorrect(long workspaceId, List<Result> results) {
        if (results.isEmpty()) {
            return List.of();
        }
        Set<UUID> incorrect = marks
                .findByWorkspaceIdAndResultIdInAndRemovedAtIsNull(
                        workspaceId, results.stream().map(Result::getId).toList())
                .stream()
                .filter(mark -> mark.getKind() == ResultMarkKind.INCORRECT)
                .map(mark -> mark.getResultId())
                .collect(Collectors.toSet());
        return results.stream()
                .filter(result -> !incorrect.contains(result.getId()))
                .toList();
    }
}
