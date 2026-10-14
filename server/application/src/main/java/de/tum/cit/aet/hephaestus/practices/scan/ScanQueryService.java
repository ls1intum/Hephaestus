package de.tum.cit.aet.hephaestus.practices.scan;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanExport;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanExportLocation;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanExportProblem;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanReportExporter;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ProblemRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultPresentation;
import de.tum.cit.aet.hephaestus.practices.result.ResultProofMapper;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultViewQuery;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewCoverageService;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemQuery;
import de.tum.cit.aet.hephaestus.practices.review.ProductReferenceQuery;
import de.tum.cit.aet.hephaestus.practices.review.ReviewItemRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewPracticeRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewServiceImpl;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewItem;
import de.tum.cit.aet.hephaestus.practices.review.report.ProblemComparison;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanMembershipQuery;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanReportCursor;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanReportService;
import de.tum.cit.aet.hephaestus.practices.scan.dto.JudgedScanReportItemDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ListedScanReportItemDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.NotReviewedScanReportItemDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ReusedScanReportItemDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanAvailabilityDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanDetailDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanPracticeRefDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanPracticeReportDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanProgressDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanReportDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanReportItemDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanReportItemsPageDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanReportProblemDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanReportProblemsPageDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.StartScanRequestDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.WithheldScanReportItemDTO;
import de.tum.cit.aet.hephaestus.practices.spi.Comparison;
import de.tum.cit.aet.hephaestus.practices.spi.ProblemBaselineState;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewItemState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequest;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedByKind;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ScanQueryService {
    private final ReviewRepository reviews;
    private final ReviewPracticeRepository assignments;
    private final ReviewService service;
    private final ReviewRuntimeQuery runtime;
    private final ScanReportService reports;
    private final ProductItemQuery items;
    private final ProductReferenceQuery references;
    private final PracticeRepository practices;
    private final PracticeVersionRepository versions;
    private final WorkspaceRepository workspaces;
    private final PracticeReviewCoverageService coverage;
    private final ResultPresentation presentation;
    private final ResultProofMapper proof;
    private final ResultRepository results;
    private final ProblemRepository problems;
    private final ScanReportExporter exporter;
    private final ScanMembershipQuery membershipQuery;
    private final ReviewItemRepository members;
    private final ResultViewQuery views;
    private final JsonMapper json;

    @Transactional(readOnly = true)
    public Page<ScanSummaryDTO> list(
            long workspaceId, @Nullable Long repositoryItemId, @Nullable ReviewStatus status, Pageable pageable) {
        return reviews.findScans(ReviewKind.SCAN, workspaceId, repositoryItemId, status, pageable)
                .map(this::summary);
    }

    @Transactional
    public ScanSummaryDTO start(long workspaceId, long actorId, UUID key, StartScanRequestDTO request) {
        var budget = parseBudget(request.budgetUsd());
        var existing = reviews.findByWorkspaceIdAndRequestKey(workspaceId, key);
        if (existing.isPresent()) {
            var prior = existing.orElseThrow();
            var ref = request.ref() == null ? prior.getChecks().path("ref").asString() : request.ref();
            var digest = digest(request, ref, budget);
            if (prior.getKind() != ReviewKind.SCAN || !digest.equals(prior.getRequestDigest()))
                throw error(HttpStatus.CONFLICT, "This request key has a different body.", "scanId", prior.getId());
            return summary(prior);
        }
        var workspace = workspaces.findByIdForUpdate(workspaceId).orElseThrow();
        var committed = reviews.findByWorkspaceIdAndRequestKey(workspaceId, key);
        if (committed.isPresent()) {
            var prior = committed.orElseThrow();
            var replayRef =
                    request.ref() == null ? prior.getChecks().path("ref").asString() : request.ref();
            if (prior.getKind() != ReviewKind.SCAN
                    || !digest(request, replayRef, budget).equals(prior.getRequestDigest()))
                throw error(HttpStatus.CONFLICT, "This request key has a different body.", "scanId", prior.getId());
            return summary(prior);
        }
        var active = reviews.findFirstByWorkspaceIdAndKindAndStatusInOrderByCreatedAtAsc(
                workspaceId, ReviewKind.SCAN, List.of(ReviewStatus.QUEUED, ReviewStatus.RUNNING));
        if (active.isPresent())
            throw error(
                    HttpStatus.CONFLICT,
                    "A scan is already active in this workspace.",
                    "activeScanId",
                    active.orElseThrow().getId());
        long repository = parseId(request.repositoryItemId());
        var item = items.require(workspaceId, repository);
        if (!item.kind().value().equals("scm.repository"))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Only repositories support scans.");
        if (!coverage.admitsRepositoryItem(workspace, repository))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Select a monitored repository in this workspace’s review scope.");
        var pinned = items.pin(item, null);
        var defaultRef = pinned.state().get("defaultBranch");
        if (defaultRef == null || defaultRef.isBlank())
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "The repository's default branch is unavailable.");
        var ref = request.ref() == null ? defaultRef : request.ref();
        if (!ref.equals(defaultRef))
            throw error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Only the default branch supports scans.",
                    "supportedRef",
                    defaultRef);
        if (request.expectedVersion() != null
                && !request.expectedVersion().equals(pinned.version().value()))
            throw error(
                    HttpStatus.CONFLICT,
                    "The repository version changed.",
                    "currentVersion",
                    pinned.version().value());
        var selected = practices.findByWorkspaceId(workspaceId).stream()
                .filter(p -> {
                    var d = json.convertValue(p.getCurrentVersion().getDefinition(), PracticeDefinition.class);
                    return d.scan() != null && d.reviewedBy().by() == ReviewedByKind.HEPHAESTUS;
                })
                .filter(p -> request.practiceSlugs() == null
                        || request.practiceSlugs().contains(p.getSlug()))
                .sorted(Comparator.comparing(Practice::getId))
                .toList();
        if (request.practiceSlugs() != null
                && (request.practiceSlugs().isEmpty()
                        || request.practiceSlugs().stream().distinct().count()
                                != request.practiceSlugs().size()
                        || selected.size() != request.practiceSlugs().size()))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Select distinct eligible practices from this workspace.");
        if (selected.isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "No practice in this workspace supports scans.");
        var id = service.request(new ReviewRequest(
                workspaceId,
                repository,
                ReviewKind.SCAN,
                pinned.version(),
                ReviewOrigin.REQUEST,
                null,
                actorId,
                null,
                budget,
                key,
                digest(request, ref, budget),
                selected.stream().map(Practice::getId).toList(),
                request.reuse() == null || request.reuse()));
        var review = reports.require(workspaceId, id);
        var checks = (ObjectNode) review.getChecks().deepCopy();
        checks.put("ref", ref);
        review.setChecks(checks);
        return summary(review);
    }

    private String digest(StartScanRequestDTO request, String ref, BigDecimal budget) {
        if (request.practiceSlugs() != null
                && (request.practiceSlugs().isEmpty()
                        || request.practiceSlugs().stream().distinct().count()
                                != request.practiceSlugs().size()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select distinct practices.");
        var body = json.createObjectNode();
        body.put("repositoryItemId", Long.toString(parseId(request.repositoryItemId())));
        body.put("ref", ref);
        body.set("expectedVersion", json.valueToTree(request.expectedVersion()));
        body.set(
                "practiceSlugs",
                json.valueToTree(
                        request.practiceSlugs() == null
                                ? null
                                : request.practiceSlugs().stream().sorted().toList()));
        body.put("budgetUsd", budget.stripTrailingZeros().toPlainString());
        body.put("reuse", request.reuse() == null || request.reuse());
        return ProblemComparison.sha256(json.writeValueAsString(body));
    }

    public static BigDecimal parseBudget(String value) {
        if (!value.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,4})?"))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Enter a decimal budget with at most four decimal places.");
        var budget = new BigDecimal(value);
        if (budget.compareTo(new BigDecimal("0.0001")) < 0 || budget.compareTo(new BigDecimal("100000")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a budget from 0.0001 to 100000 USD.");
        return budget;
    }

    private static long parseId(String id) {
        if (!id.matches("[1-9][0-9]*"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a positive item id.");
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The item id is too large.", failure);
        }
    }

    private static ErrorResponseException error(HttpStatus status, String message, String key, Object value) {
        var detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setProperty(key, value);
        return new ErrorResponseException(status, detail, null);
    }

    @Transactional(readOnly = true)
    public ScanSummaryDTO summary(Review review) {
        var frozen = reports.recorded(review);
        var refs = references.items(review.getWorkspaceId(), List.of(review.getItemId()));
        var account = review.getRequestedById() == null
                ? null
                : references
                        .accounts(review.getWorkspaceId(), List.of(review.getRequestedById()))
                        .get(review.getRequestedById());
        return new ScanSummaryDTO(
                review.getId(),
                Objects.requireNonNull(refs.get(review.getItemId())),
                review.getChecks().path("ref").asString(),
                Objects.requireNonNull(review.getItemVersion()),
                review.getOrigin(),
                review.getStatus(),
                account,
                review.getCreatedAt(),
                review.getChecks().path("startedAt").isString()
                        ? Instant.parse(review.getChecks().path("startedAt").asString())
                        : null,
                review.getCompletedAt(),
                assignments
                        .findByWorkspaceIdAndReviewIdOrderByPracticeIdAsc(review.getWorkspaceId(), review.getId())
                        .size(),
                reports.currentCounts(review),
                frozen.baselineScanId(),
                review.getStopReason());
    }

    @Transactional(readOnly = true)
    public ScanDetailDTO detail(long workspaceId, UUID id) {
        var review = reports.require(workspaceId, id);
        var summary = summary(review);
        var report = reports.recorded(review);
        var usage = runtime.usage(workspaceId, id);
        var jobs = runtime.runtime(workspaceId, id)
                .map(ReviewRuntimeSnapshot::jobs)
                .orElse(List.of());
        return new ScanDetailDTO(
                summary.id(),
                summary.repository(),
                summary.ref(),
                summary.itemVersion(),
                summary.origin(),
                summary.status(),
                summary.requestedBy(),
                summary.createdAt(),
                summary.startedAt(),
                summary.completedAt(),
                summary.practiceCount(),
                summary.counts(),
                summary.baselineScanId(),
                summary.stopReason(),
                report.practices().stream()
                        .map(p -> new ScanPracticeRefDTO(
                                p.practiceId(),
                                p.practiceVersionId(),
                                p.number(),
                                p.practiceSlug(),
                                p.practiceName(),
                                p.reviewDigest()))
                        .toList(),
                Objects.requireNonNull(review.getBudgetUsd()).toPlainString(),
                usage.costUsd() == null ? null : usage.costUsd().toPlainString(),
                new ScanProgressDTO(
                        summary.counts().practiceItemPairs(),
                        summary.counts().reviewed() + summary.counts().withheld(),
                        (int) jobs.stream()
                                .filter(j -> j.status() == ReviewJobStatus.RUNNING)
                                .count(),
                        (int) jobs.stream()
                                .filter(j -> j.status() == ReviewJobStatus.QUEUED)
                                .count()));
    }

    @Transactional
    public ScanDetailDTO cancel(long workspaceId, UUID id) {
        reports.require(workspaceId, id);
        service.cancel(workspaceId, id, "CANCELLED_BY_ADMIN");
        return detail(workspaceId, id);
    }

    @Transactional(readOnly = true)
    public ScanReportDTO report(long workspaceId, UUID id) {
        var review = reports.require(workspaceId, id);
        var recorded = reports.recorded(review);
        int withheld = reports.expired(review) ? 0 : reports.withheldProofPairs(review);
        var expired = reports.expired(review);
        return new ScanReportDTO(
                summary(review),
                review.getStatus() == ReviewStatus.COMPLETE,
                recorded.listingComplete(),
                recorded.baselineScanId(),
                recorded.comparison(),
                ReviewServiceImpl.terminal(review.getStatus()) ? recorded.recordedCounts() : null,
                reports.currentCounts(review),
                recorded.problemChanges(),
                recorded.practices().stream()
                        .map(p -> reports.currentPractice(review, p))
                        .toList(),
                recorded.withheldReasons(),
                recorded.notReviewedReasons(),
                recorded.removalGuard(),
                new ScanAvailabilityDTO(
                        expired,
                        review.getCompletedAt() == null
                                ? null
                                : review.getCompletedAt().plus(Duration.ofDays(400)),
                        expired ? 0 : withheld + recorded.redactedProofPairs(),
                        expired
                                ? recorded.expiredProofPairs() == 0
                                        ? recorded.recordedCounts().reviewed()
                                        : recorded.expiredProofPairs()
                                : 0));
    }

    @Transactional(readOnly = true)
    public ScanReportItemsPageDTO items(
            long workspaceId, UUID id, @Nullable String cursor, int size, ItemFilter filter) {
        var review = reports.require(workspaceId, id);
        reports.requireRaw(review);
        var after = cursor(cursor, id, false);
        var pinned = reports.recorded(review).practices().stream()
                .collect(Collectors.toMap(p -> Long.parseLong(p.practiceId()), p -> p));
        var keys = membershipQuery.items(review, after, size + 1, filter);
        boolean more = keys.size() > size;
        var page = keys.subList(0, Math.min(size, keys.size()));
        var refs = references.items(
                workspaceId, page.stream().map(ScanReportCursor::itemId).toList());
        var rows = new ArrayList<MemberRow>();
        var resultsById = new HashMap<UUID, Result>();
        for (var key : page) {
            var member = members.findByWorkspaceIdAndReviewIdAndPracticeIdAndItemId(
                            workspaceId, id, key.practiceId(), key.itemId())
                    .orElseThrow();
            rows.add(new MemberRow(
                    member,
                    Objects.requireNonNull(refs.get(key.itemId())),
                    Objects.requireNonNull(pinned.get(key.practiceId()))));
            if (member.getResultId() != null)
                results.findByIdAndWorkspaceId(member.getResultId(), workspaceId)
                        .ifPresent(r -> resultsById.put(r.getId(), r));
        }
        var permitted = reports.permitted(review, resultsById.values());
        var content = rows.stream()
                .map(r -> item(review, r, resultsById.get(r.member().getResultId()), permitted))
                .toList();
        return new ScanReportItemsPageDTO(content, more ? encode(page.getLast()) : null, more);
    }

    private ScanReportItemDTO item(Review review, MemberRow row, @Nullable Result result, Set<UUID> permitted) {
        var member = row.member();
        var p = row.practice();
        var version = Objects.requireNonNull(member.getItemVersion());
        var state = itemStatus(review, member);
        var reason = member.getReason() != null
                ? member.getReason()
                : review.getStopReason() == null ? null : review.getStopReason().name();
        if (result != null) {
            var permittedProof = permitted.contains(result.getId());
            var practice = practices.findById(result.getPracticeId()).orElseThrow();
            var pinned = result.getPracticeVersionId() == null
                    ? null
                    : versions.findById(result.getPracticeVersionId()).orElse(null);
            var current = ResultVisibilityPolicy.currentness(result, pinned, practice.getCurrentVersion());
            var count = problems.findByWorkspaceIdAndResultIdOrderByPositionAsc(review.getWorkspaceId(), result.getId())
                    .size();
            return member.getState() == ReviewItemState.REUSED
                    ? new ReusedScanReportItemDTO(
                            row.item(),
                            p.practiceSlug(),
                            p.practiceName(),
                            version,
                            result.getId(),
                            result.getReviewId(),
                            result.getOutcome(),
                            result.getSeverity(),
                            permittedProof ? result.getSummary() : null,
                            count,
                            current,
                            result.getRecordedAt(),
                            "REUSED")
                    : new JudgedScanReportItemDTO(
                            row.item(),
                            p.practiceSlug(),
                            p.practiceName(),
                            version,
                            "JUDGED",
                            result.getId(),
                            result.getReviewId(),
                            result.getOutcome(),
                            result.getSeverity(),
                            permittedProof ? result.getSummary() : null,
                            count,
                            current,
                            result.getRecordedAt());
        }
        var previous = results.findCurrentClaim(
                        review.getWorkspaceId(), member.getPracticeId(), member.getItemId(), null)
                .filter(r -> reports.permitted(review, List.of(r)).contains(r.getId())
                        && presentation.markedAt(r, ResultMarkKind.INCORRECT) == null)
                .flatMap(r -> views.views(review.getWorkspaceId(), List.of(r)).stream()
                        .findFirst())
                .map(v -> presentation.list(v, true))
                .orElse(null);
        return switch (state) {
            case "WITHHELD" ->
                new WithheldScanReportItemDTO(
                        row.item(),
                        p.practiceSlug(),
                        p.practiceName(),
                        version,
                        "WITHHELD",
                        Objects.requireNonNull(member.getReason()),
                        Objects.requireNonNull(member.getReason()));
            case "NOT_REVIEWED" ->
                new NotReviewedScanReportItemDTO(
                        row.item(),
                        p.practiceSlug(),
                        p.practiceName(),
                        version,
                        reason,
                        reason,
                        previous,
                        "NOT_REVIEWED");
            default ->
                new ListedScanReportItemDTO(
                        row.item(), p.practiceSlug(), p.practiceName(), version, "LISTED", reason, reason, previous);
        };
    }

    private static String itemStatus(Review review, ReviewItem item) {
        return item.getState() == ReviewItemState.LISTED && ReviewServiceImpl.terminal(review.getStatus())
                ? "NOT_REVIEWED"
                : item.getState().name();
    }

    @Transactional(readOnly = true)
    public ScanReportProblemsPageDTO problems(
            long workspaceId, UUID id, @Nullable String cursor, int size, ProblemFilter filter) {
        var review = reports.require(workspaceId, id);
        reports.requireRaw(review);
        var after = cursor(cursor, id, true);
        var rows = problemRows(review, filter, after, size + 1);
        boolean more = rows.size() > size;
        var page = rows.subList(0, Math.min(size, rows.size()));
        return new ScanReportProblemsPageDTO(
                page.stream().map(ProblemRow::dto).toList(),
                more ? encode(page.getLast().key()) : null,
                more);
    }

    private List<ProblemRow> problemRows(
            Review review, ProblemFilter filter, @Nullable ScanReportCursor after, int limit) {
        var frozen = reports.recorded(review);
        var pinned = frozen.practices().stream().collect(Collectors.toMap(p -> Long.parseLong(p.practiceId()), p -> p));
        var output = new ArrayList<ProblemRow>();
        while (output.size() < limit) {
            var keys = membershipQuery.problems(review, after, 256, filter);
            if (keys.isEmpty()) break;
            var rows = results
                    .findByWorkspaceIdAndIdIn(
                            review.getWorkspaceId(),
                            keys.stream()
                                    .map(ScanMembershipQuery.ProblemKey::resultId)
                                    .distinct()
                                    .toList())
                    .stream()
                    .collect(Collectors.toMap(Result::getId, r -> r));
            var allowed = reports.permitted(review, rows.values());
            var unmarked = reports.unmarked(review, rows.values());
            var refs = references.items(
                    review.getWorkspaceId(),
                    keys.stream().map(k -> k.cursor().itemId()).distinct().toList());
            for (var key : keys) {
                var result = rows.get(key.resultId());
                if (result == null
                        || !allowed.contains(result.getId())
                        || (filter.openOnly() && !unmarked.contains(result.getId()))) continue;
                var child = problems.findByIdAndWorkspaceId(
                                Objects.requireNonNull(key.cursor().problemId()), review.getWorkspaceId())
                        .orElseThrow();
                var practice = Objects.requireNonNull(pinned.get(key.cursor().practiceId()));
                var item = Objects.requireNonNull(refs.get(key.cursor().itemId()));
                var dto = new ScanReportProblemDTO(
                        presentation.problem(child, true),
                        result.getId(),
                        item,
                        practice.practiceSlug(),
                        practice.practiceName(),
                        key.state(),
                        key.previousProblemId(),
                        key.state() == ProblemBaselineState.ABSENT ? "REPAIRED" : null);
                output.add(new ProblemRow(key.cursor(), dto, result));
                if (output.size() == limit) break;
            }
            if (keys.size() < 256) break;
            after = keys.getLast().cursor();
        }
        return List.copyOf(output);
    }

    @Transactional(readOnly = true)
    public byte[] sarif(long workspaceId, UUID id) {
        var review = reports.require(workspaceId, id);
        reports.requireRaw(review);
        if (!ReviewServiceImpl.terminal(review.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wait for the scan to finish before exporting.");
        var frozen = reports.recorded(review);
        var summary = summary(review);
        var exported = new ArrayList<ScanExportProblem>();
        for (var row : problemRows(review, new ProblemFilter(null, null, null, null, false), null, Integer.MAX_VALUE)) {

            if (!reports.unmarked(review, List.of(row.result()))
                    .contains(row.result().getId())) continue;
            if (row.dto().baselineState() == ProblemBaselineState.ABSENT) {
                var current = members.findByWorkspaceIdAndReviewIdAndPracticeIdAndItemId(
                                workspaceId,
                                id,
                                row.key().practiceId(),
                                row.key().itemId())
                        .flatMap(member -> member.getResultId() == null
                                ? Optional.<Result>empty()
                                : results.findByIdAndWorkspaceId(member.getResultId(), workspaceId));
                if (current.isEmpty()
                        || !reports.permitted(review, List.of(current.orElseThrow()))
                                .contains(current.orElseThrow().getId())
                        || !reports.unmarked(review, List.of(current.orElseThrow()))
                                .contains(current.orElseThrow().getId())) continue;
            }
            var result = row.result();
            var candidate = proof.snapshot(result);
            var ownPath = Objects.requireNonNull(row.dto().item().path());
            var identity = reports.identities(candidate, ownPath).entrySet().stream()
                    .filter(e -> e.getValue().id().equals(row.dto().problem().id()))
                    .findFirst()
                    .orElseThrow()
                    .getKey();
            var version = versions.findById(Objects.requireNonNull(result.getPracticeVersionId()))
                    .orElseThrow();
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
                    result.getPracticeVersionId(),
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
        if (summary.repository().url() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The repository source is unavailable for export.");
        var method = frozen.practices().stream().findFirst().orElseThrow().method();
        var counts = reports.currentCounts(review);
        var recordedCount = frozen.recordedCounts();
        var marked = reports.markedCounts(review);
        int unavailableProof = reports.withheldProofPairs(review) + frozen.redactedProofPairs();
        var selectors = frozen.practices().stream()
                .map(p -> {
                    var pinned = json.convertValue(
                            versions.findById(Long.parseLong(p.practiceVersionId()))
                                    .orElseThrow()
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

    private @Nullable ScanReportCursor cursor(@Nullable String value, UUID scanId, boolean problem) {
        if (value == null) return null;
        if (value.length() > 2048) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The cursor is too long.");
        try {
            var decoded = Base64.getUrlDecoder().decode(value);
            var cursor = json.readerFor(ScanReportCursor.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(decoded);
            var typed = (ScanReportCursor) cursor;
            if (!typed.scanId().equals(scanId)
                    || typed.path() == null
                    || typed.path().length() > 1024
                    || typed.practiceId() < 1
                    || typed.itemId() < 1
                    || (typed.problemId() != null) != problem
                    || typed.position() < 0) throw new IllegalArgumentException("Invalid cursor identity");
            return typed;
        } catch (RuntimeException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The scan cursor is invalid.", failure);
        }
    }

    private String encode(ScanReportCursor cursor) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.writeValueAsString(cursor).getBytes(StandardCharsets.UTF_8));
    }

    private record MemberRow(ReviewItem member, ItemRefDTO item, ScanPracticeReportDTO practice) {}

    private record ProblemRow(ScanReportCursor key, ScanReportProblemDTO dto, Result result) {}

    public record ItemFilter(
            @Nullable String practiceSlug,
            @Nullable ResultOutcome outcome,
            @Nullable String status,
            @Nullable Boolean reused,
            @Nullable String path) {}

    public record ProblemFilter(
            @Nullable String practiceSlug,
            @Nullable Severity severity,
            @Nullable String path,
            @Nullable ProblemBaselineState baselineState,
            boolean openOnly) {}
}
