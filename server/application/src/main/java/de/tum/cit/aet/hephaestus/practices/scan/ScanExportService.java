package de.tum.cit.aet.hephaestus.practices.scan;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanExport;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanExportLocation;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanExportProblem;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanReportExporter;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.model.PracticeVersion;
import de.tum.cit.aet.hephaestus.practices.result.ResultProofMapper;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewItemRepository;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewItem;
import de.tum.cit.aet.hephaestus.practices.review.report.ProblemComparison;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanEvidencePolicy;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanReportCursor;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanReportService;
import de.tum.cit.aet.hephaestus.practices.spi.Comparison;
import de.tum.cit.aet.hephaestus.practices.spi.ProblemBaselineState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScanExportService {
    private final ScanQueryService query;
    private final ScanReportItemsService items;
    private final ScanReportService reports;
    private final ScanEvidencePolicy eligibility;
    private final ProblemComparison comparison;
    private final PracticeVersionRepository versions;
    private final ResultProofMapper proof;
    private final ReviewItemRepository members;
    private final ResultRepository results;
    private final ScanReportExporter exporter;
    private final JsonMapper json;

    public byte[] sarif(long workspaceId, UUID id) {
        var review = reports.require(workspaceId, id);
        reports.requireRaw(review);
        if (!review.getStatus().isTerminal())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wait for the scan to finish before exporting.");
        var frozen = reports.recorded(review);
        var summary = query.summary(review);
        if (summary.repository().url() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The repository source is unavailable for export.");
        var exportVersions = new HashMap<>(versions
                .findByWorkspaceIdAndIdIn(
                        workspaceId,
                        frozen.practices().stream()
                                .map(p -> Long.parseLong(p.practiceVersionId()))
                                .toList())
                .stream()
                .collect(Collectors.toMap(PracticeVersion::getId, Function.identity())));
        var exported = new ArrayList<ScanExportProblem>();
        ScanReportCursor after = null;
        while (true) {
            var page = items.problemRows(
                    review, new ScanReportItemsService.ProblemFilter(null, null, null, null, false), after, 256);
            if (page.isEmpty()) break;
            var unmarked = eligibility.unmarked(
                    review,
                    page.stream().map(ScanReportItemsService.ProblemRow::result).toList());
            var pageProof = proof.snapshots(
                    workspaceId,
                    page.stream()
                            .map(ScanReportItemsService.ProblemRow::result)
                            .filter(result -> unmarked.contains(result.getId()))
                            .toList());
            var missingVersions = page.stream()
                    .map(p -> p.result().getPracticeVersionId())
                    .filter(Objects::nonNull)
                    .filter(v -> !exportVersions.containsKey(v))
                    .distinct()
                    .toList();
            if (!missingVersions.isEmpty())
                versions.findByWorkspaceIdAndIdIn(workspaceId, missingVersions)
                        .forEach(v -> exportVersions.put(v.getId(), v));
            var memberRows = members.findByWorkspaceIdAndReviewIdAndItemIdIn(
                    workspaceId,
                    id,
                    page.stream().map(p -> p.key().itemId()).distinct().toList());
            var currentRows = results.findByWorkspaceIdAndIdIn(
                    workspaceId,
                    memberRows.stream()
                            .map(ReviewItem::getResultId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList());
            var currentAllowed = eligibility.permitted(review, currentRows);
            var currentUnmarked = eligibility.unmarked(review, currentRows);
            var currentByKey = memberRows.stream()
                    .filter(m -> m.getResultId() != null)
                    .collect(Collectors.toMap(m -> new Key(m.getPracticeId(), m.getItemId()), ReviewItem::getResultId));
            for (var row : page) {
                if (!unmarked.contains(row.result().getId())) continue;
                if (row.dto().baselineState() == ProblemBaselineState.ABSENT) {
                    var currentId = currentByKey.get(
                            new Key(row.key().practiceId(), row.key().itemId()));
                    if (currentId == null
                            || !currentAllowed.contains(currentId)
                            || !currentUnmarked.contains(currentId)) continue;
                }
                var result = row.result();
                var candidate = Objects.requireNonNull(pageProof.get(result.getId()));
                var ownPath = Objects.requireNonNull(row.dto().item().path());
                var identity = comparison.identities(candidate, ownPath).entrySet().stream()
                        .filter(e ->
                                e.getValue().id().equals(row.dto().problem().id()))
                        .findFirst()
                        .orElseThrow()
                        .getKey();
                var version = Objects.requireNonNull(exportVersions.get(result.getPracticeVersionId()));
                var definition = json.convertValue(version.getDefinition(), PracticeDefinition.class);
                var p = candidate.problems().stream()
                        .filter(child -> child.id().equals(row.dto().problem().id()))
                        .findFirst()
                        .orElseThrow()
                        .candidate();
                exported.add(new ScanExportProblem(
                        row.dto().problem().id(),
                        result.getId(),
                        result.getItemId(),
                        Objects.requireNonNull(result.getItemVersion()),
                        candidate.candidate().practiceVersionId(),
                        row.dto().practiceSlug(),
                        definition.name(),
                        definition.criteria(),
                        version.getReviewDigest(),
                        p.severity().name(),
                        p.summary(),
                        ownPath,
                        identity,
                        row.dto().baselineState() == null
                                ? null
                                : row.dto().baselineState().name(),
                        row.dto().baselineState() == ProblemBaselineState.ABSENT,
                        p.citations().stream()
                                .map(c -> new ScanExportLocation(c.path(), c.startLine(), c.endLine(), c.quote()))
                                .toList()));
            }
            after = page.getLast().key();
            if (page.size() < 256) break;
        }
        var method = frozen.practices().stream().findFirst().orElseThrow().method();
        var counts = reports.currentCounts(review);
        var recordedCount = frozen.recordedCounts();
        var marked = reports.markedCounts(review);
        int unavailableProof = reports.withheldProofPairs(review) + frozen.redactedProofPairs();
        var selectors = frozen.practices().stream()
                .map(p -> {
                    var pinned = json.convertValue(
                            Objects.requireNonNull(exportVersions.get(Long.parseLong(p.practiceVersionId())))
                                    .getDefinition(),
                            PracticeDefinition.class);
                    var selected = new TreeMap<String, Object>();
                    selected.put("practiceId", p.practiceId());
                    selected.put("reviewDigest", p.reviewDigest());
                    selected.put("scan", Objects.requireNonNull(pinned.scan()));
                    return selected;
                })
                .toList();
        var exportCounts = new TreeMap<String, Long>();
        exportCounts.put("reviewed", (long) counts.reviewed());
        exportCounts.put("reused", (long) counts.reused());
        exportCounts.put("withheld", (long) counts.withheld());
        exportCounts.put("notReviewed", (long) counts.notReviewed());
        exportCounts.put("openProblems", (long) counts.openProblems());
        exportCounts.put("recordedProblems", (long) recordedCount.problems());
        exportCounts.put("exportedProblems", (long) exported.size());
        exportCounts.put("excludedIncorrect", marked.getOrDefault("INCORRECT", 0L));
        exportCounts.put("excludedAccepted", marked.getOrDefault("ACCEPTED", 0L));
        exportCounts.put("withheldProofPairs", (long) unavailableProof);
        var reasons = new ArrayList<>(frozen.practices().stream()
                .map(p -> p.comparison().name())
                .distinct()
                .toList());
        if (unavailableProof > 0) reasons.add("PROOF_UNAVAILABLE");
        return exporter.export(new ScanExport(
                id,
                workspaceId,
                review.getItemId(),
                summary.repository().url(),
                Objects.requireNonNull(review.getItemVersion()),
                ProblemComparison.sha256(json.writeValueAsString(selectors)),
                method.modelDigest(),
                method.epoch(),
                false,
                false,
                review.getStatus() == ReviewStatus.COMPLETE && unavailableProof == 0,
                frozen.listingComplete(),
                frozen.baselineScanId(),
                frozen.comparison() == Comparison.COMPARABLE,
                exportCounts,
                reasons,
                exported));
    }

    private record Key(long practiceId, long itemId) {}
}
