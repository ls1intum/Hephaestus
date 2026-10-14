package de.tum.cit.aet.hephaestus.practices.review.report;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.model.ResultMark;
import de.tum.cit.aet.hephaestus.practices.result.ProblemRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultMarkRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultProofMapper;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewItemRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewOrder;
import de.tum.cit.aet.hephaestus.practices.review.ReviewPracticeRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.review.dto.BoundModelSlotDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.FrozenModelDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewMethodDTO;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewPractice;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReasonCountDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ProblemChangesDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.RemovalGuardDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanCountsDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanPracticeReportDTO;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedProblemSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedResultSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.Comparison;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.ProblemBaselineState;
import de.tum.cit.aet.hephaestus.practices.spi.RemovalGuardState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewItemState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewMethod;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScanReportService {
    public static final Duration RETENTION = Duration.ofDays(400);
    private static final int READ_BATCH = 256;
    private final ReviewRepository reviews;
    private final ReviewPracticeRepository assignments;
    private final ReviewItemRepository memberships;
    private final ResultRepository results;
    private final ProblemRepository problems;
    private final ResultMarkRepository marks;
    private final PracticeVersionRepository versions;
    private final ResultProofMapper proof;
    private final EvidenceAuthorization authorization;
    private final ProblemComparison comparison;
    private final ScanMembershipQuery queries;
    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;
    private final EntityManager entities;

    @Transactional
    public FrozenScanReport freeze(long workspaceId, UUID reviewId) {
        var review = require(workspaceId, reviewId);
        reviews.flush();
        var report = calculate(review, true);
        review.setReport(json.valueToTree(report));
        return report;
    }

    public FrozenScanReport recorded(Review review) {
        return review.getReport() == null
                ? calculate(review, false)
                : json.convertValue(review.getReport(), FrozenScanReport.class);
    }

    public Review require(long workspaceId, UUID reviewId) {
        return reviews.findByIdAndWorkspaceId(reviewId, workspaceId)
                .filter(r -> r.getKind() == ReviewKind.SCAN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The scan does not exist."));
    }

    public boolean expired(Review review) {
        return (review.getCompletedAt() != null
                        && !clock.instant().isBefore(review.getCompletedAt().plus(RETENTION)))
                || (review.getReport() != null
                        && review.getReport().path("expired").asBoolean());
    }

    public void requireRaw(Review review) {
        if (expired(review)) throw new ResponseStatusException(HttpStatus.GONE, "The scan's raw proof has expired.");
    }

    public Set<UUID> permitted(Review review, Collection<Result> rows) {
        return authorization.permitsAll(review.getWorkspaceId(), rows, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW);
    }

    public Set<UUID> unmarked(Review review, Collection<Result> rows) {
        if (rows.isEmpty()) return Set.of();
        var marked = marks
                .findByWorkspaceIdAndResultIdInAndRemovedAtIsNull(
                        review.getWorkspaceId(),
                        rows.stream().map(Result::getId).toList())
                .stream()
                .map(ResultMark::getResultId)
                .collect(Collectors.toSet());
        return rows.stream()
                .filter(r -> !marked.contains(r.getId()))
                .map(Result::getId)
                .collect(Collectors.toSet());
    }

    public Map<String, Long> markedCounts(Review review) {
        return jdbc
                .sql("SELECT mark.kind,COUNT(DISTINCT mark.result_id) AS count FROM result_mark mark JOIN"
                        + " review_item member ON member.result_id=mark.result_id AND"
                        + " member.workspace_id=mark.workspace_id WHERE member.workspace_id=:ws AND"
                        + " member.review_id=:scan AND mark.removed_at IS NULL GROUP BY mark.kind")
                .param("ws", review.getWorkspaceId())
                .param("scan", review.getId())
                .query((rs, n) -> Map.entry(rs.getString("kind"), rs.getLong("count")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public ScanCountsDTO currentCounts(Review review) {
        var count = recorded(review).recordedCounts();
        return withOpen(count, expired(review) ? 0 : openProblems(review, null));
    }

    public ScanPracticeReportDTO currentPractice(Review review, ScanPracticeReportDTO practice) {
        return new ScanPracticeReportDTO(
                practice.practiceId(),
                practice.practiceVersionId(),
                practice.number(),
                practice.practiceSlug(),
                practice.practiceName(),
                practice.reviewDigest(),
                withOpen(
                        practice.counts(),
                        expired(review) ? 0 : openProblems(review, Long.parseLong(practice.practiceId()))),
                practice.listingComplete(),
                practice.method(),
                practice.comparison(),
                practice.status(),
                practice.reason());
    }

    private static ScanCountsDTO withOpen(ScanCountsDTO c, int open) {
        return new ScanCountsDTO(
                c.files(),
                c.practiceItemPairs(),
                c.reviewed(),
                c.reused(),
                c.met(),
                c.notMet(),
                c.doesNotApply(),
                c.couldNotDetermine(),
                c.problems(),
                open,
                c.withheld(),
                c.notReviewed());
    }

    private int openProblems(Review review, @Nullable Long practiceId) {
        int[] count = {0};
        eachProof(review, practiceId, true, rows -> {
            var allowed = permitted(review, rows);
            if (allowed.isEmpty()) return;
            count[0] = Math.addExact(
                    count[0],
                    Math.toIntExact(
                            jdbc.sql("SELECT COUNT(*) FROM problem WHERE workspace_id=:ws AND result_id=ANY(:ids)")
                                    .param("ws", review.getWorkspaceId())
                                    .param("ids", new SqlArrayValue("uuid", allowed.toArray()))
                                    .query((rs, n) -> rs.getLong(1))
                                    .single()));
        });
        return count[0];
    }

    public int withheldProofPairs(Review review) {
        int[] denied = {0};
        eachProof(
                review,
                null,
                false,
                rows -> denied[0] = Math.addExact(
                        denied[0], rows.size() - permitted(review, rows).size()));
        return denied[0];
    }

    private void eachProof(Review review, @Nullable Long practiceId, boolean openOnly, Consumer<List<Result>> read) {
        UUID after = null;
        while (true) {
            var ids = queries.proofIds(review, after, READ_BATCH, practiceId, openOnly);
            if (ids.isEmpty()) return;
            var rows = results.findByWorkspaceIdAndIdIn(review.getWorkspaceId(), ids);
            read.accept(rows);
            rows.forEach(this::detach);
            if (ids.size() < READ_BATCH) return;
            after = ids.getLast();
        }
    }

    private ScanCountsDTO counts(Review review, @Nullable Long practiceId, boolean complete) {
        String selected = practiceId == null ? "" : " AND m.practice_id=:practice";
        var params = new HashMap<String, Object>();
        params.put("ws", review.getWorkspaceId());
        params.put("scan", review.getId());
        if (practiceId != null) params.put("practice", practiceId);
        var count = jdbc.sql("""
            SELECT COUNT(DISTINCT m.item_id) AS files,COUNT(*) AS pairs,
             COUNT(*) FILTER(WHERE m.state IN ('JUDGED','REUSED')) AS reviewed,
             COUNT(*) FILTER(WHERE m.state='REUSED') AS reused,
             COUNT(*) FILTER(WHERE r.outcome='MET') AS met,
             COUNT(*) FILTER(WHERE r.outcome='NOT_MET') AS not_met,
             COUNT(*) FILTER(WHERE r.outcome='DOES_NOT_APPLY') AS does_not_apply,
             COUNT(*) FILTER(WHERE r.outcome='COULD_NOT_DETERMINE') AS could_not_determine,
             COUNT(*) FILTER(WHERE m.state='WITHHELD') AS withheld,
             COUNT(*) FILTER(WHERE m.state='LISTED') AS pending
            FROM review_item m LEFT JOIN result r ON r.id=m.result_id AND r.workspace_id=m.workspace_id
            WHERE m.workspace_id=:ws AND m.review_id=:scan
            """ + selected)
                .params(params)
                .query((rs, n) -> new ScanCountsDTO(
                        complete ? Math.toIntExact(rs.getLong("files")) : null,
                        complete ? Math.toIntExact(rs.getLong("pairs")) : null,
                        Math.toIntExact(rs.getLong("reviewed")),
                        Math.toIntExact(rs.getLong("reused")),
                        Math.toIntExact(rs.getLong("met")),
                        Math.toIntExact(rs.getLong("not_met")),
                        Math.toIntExact(rs.getLong("does_not_apply")),
                        Math.toIntExact(rs.getLong("could_not_determine")),
                        0,
                        0,
                        Math.toIntExact(rs.getLong("withheld")),
                        review.getStatus().isTerminal() ? Math.toIntExact(rs.getLong("pending")) : 0))
                .single();
        int children = Math.toIntExact(
                jdbc.sql("SELECT COUNT(*) FROM review_item m JOIN problem p ON p.result_id=m.result_id AND"
                                + " p.workspace_id=m.workspace_id WHERE m.workspace_id=:ws AND m.review_id=:scan"
                                + selected)
                        .params(params)
                        .query((rs, n) -> rs.getLong(1))
                        .single());
        return new ScanCountsDTO(
                count.files(),
                count.practiceItemPairs(),
                count.reviewed(),
                count.reused(),
                count.met(),
                count.notMet(),
                count.doesNotApply(),
                count.couldNotDetermine(),
                children,
                expired(review) ? 0 : openProblems(review, practiceId),
                count.withheld(),
                count.notReviewed());
    }

    private FrozenScanReport calculate(Review review, boolean close) {
        var assigned =
                assignments.findByWorkspaceIdAndReviewIdOrderByPracticeIdAsc(review.getWorkspaceId(), review.getId());
        boolean listing = review.getInventoryReceipt() != null
                && review.getInventoryReceipt().path("complete").asBoolean()
                && assigned.stream().allMatch(p -> Boolean.TRUE.equals(p.getListingComplete()));
        var baseline = baseline(review);
        var previous = baseline == null
                ? Map.<Long, ReviewPractice>of()
                : assignments
                        .findByWorkspaceIdAndReviewIdOrderByPracticeIdAsc(review.getWorkspaceId(), baseline.getId())
                        .stream()
                        .collect(Collectors.toMap(ReviewPractice::getPracticeId, Function.identity()));
        var practiceReports = new ArrayList<ScanPracticeReportDTO>();
        var comparisons = new HashMap<Long, Comparison>();
        for (var assignment : assigned) {
            var version = versions.findByIdAndWorkspaceId(
                            Objects.requireNonNull(assignment.getPracticeVersionId()), review.getWorkspaceId())
                    .filter(v -> v.getWorkspaceId() == review.getWorkspaceId())
                    .orElseThrow();
            var definition = json.convertValue(version.getDefinition(), PracticeDefinition.class);
            var prior = previous.get(assignment.getPracticeId());
            var comparable = ProblemComparison.comparability(
                    standard(prior),
                    Objects.requireNonNull(standard(assignment)),
                    prior != null
                            && Boolean.TRUE.equals(prior.getListingComplete())
                            && baseline != null
                            && !expired(baseline));
            comparisons.put(assignment.getPracticeId(), comparable);
            practiceReports.add(new ScanPracticeReportDTO(
                    Long.toString(assignment.getPracticeId()),
                    Long.toString(Objects.requireNonNull(version.getId())),
                    version.getNumber(),
                    version.getPractice().getSlug(),
                    definition.name(),
                    version.getReviewDigest(),
                    counts(review, assignment.getPracticeId(), Boolean.TRUE.equals(assignment.getListingComplete())),
                    Boolean.TRUE.equals(assignment.getListingComplete()),
                    method(Objects.requireNonNull(assignment.getMethod())),
                    close ? comparable : Comparison.INCOMPLETE,
                    assignment.getStatus(),
                    assignment.getReason()));
        }
        var changes = close
                ? comparePairs(review, baseline, comparisons)
                : new EnumMap<ProblemBaselineState, Integer>(ProblemBaselineState.class);
        var kinds = comparisons.values().stream().distinct().toList();
        var comparable = !close
                ? Comparison.INCOMPLETE
                : kinds.size() == 1
                        ? kinds.getFirst()
                        : kinds.isEmpty()
                                ? baseline == null ? Comparison.FIRST_SCAN : Comparison.INCOMPLETE
                                : Comparison.MIXED;
        var receipt = review.getInventoryReceipt();
        var removal = new RemovalGuardDTO(
                receipt == null
                        ? RemovalGuardState.INCOMPLETE_LISTING
                        : RemovalGuardState.valueOf(receipt.path("removalGuard").asString("INCOMPLETE_LISTING")),
                receipt == null ? 0 : receipt.path("previousFiles").asInt(),
                receipt == null ? 0 : receipt.path("wouldRemove").asInt());
        return new FrozenScanReport(
                1,
                listing,
                baseline == null ? null : baseline.getId(),
                comparable,
                counts(review, null, listing),
                new ProblemChangesDTO(
                        changes.getOrDefault(ProblemBaselineState.NEW, 0),
                        changes.getOrDefault(ProblemBaselineState.UNCHANGED, 0),
                        changes.getOrDefault(ProblemBaselineState.UPDATED, 0),
                        changes.getOrDefault(ProblemBaselineState.ABSENT, 0)),
                practiceReports,
                reasons(review, ReviewItemState.WITHHELD),
                review.getStatus().isTerminal() ? reasons(review, ReviewItemState.LISTED) : List.of(),
                removal,
                false,
                0,
                0);
    }

    private Map<ProblemBaselineState, Integer> comparePairs(
            Review review, @Nullable Review baseline, Map<Long, Comparison> comparisons) {
        var changes = new EnumMap<ProblemBaselineState, Integer>(ProblemBaselineState.class);
        ScanReportCursor after = null;
        while (true) {
            var pairs = queries.comparisonPairs(review, baseline == null ? null : baseline.getId(), after, READ_BATCH);
            if (pairs.isEmpty()) break;
            for (var key : pairs) {
                var member = memberships
                        .findByWorkspaceIdAndReviewIdAndPracticeIdAndItemId(
                                review.getWorkspaceId(), review.getId(), key.practiceId(), key.itemId())
                        .orElseThrow();
                var result = results.findByIdAndWorkspaceId(
                                Objects.requireNonNull(member.getResultId()), review.getWorkspaceId())
                        .orElseThrow();
                var rows = new ArrayList<Result>();
                rows.add(result);
                var priorMember = baseline == null
                        ? null
                        : memberships
                                .findByWorkspaceIdAndReviewIdAndPracticeIdAndItemId(
                                        review.getWorkspaceId(), baseline.getId(), key.practiceId(), key.itemId())
                                .orElse(null);
                var previous = priorMember == null || priorMember.getResultId() == null
                        ? null
                        : results.findByIdAndWorkspaceId(priorMember.getResultId(), review.getWorkspaceId())
                                .orElse(null);
                if (previous != null) rows.add(previous);
                var allowed = permitted(review, rows);
                var unmarked = unmarked(review, rows);
                var comparisonFacts = new ArrayList<ScanProblemComparison>();
                if (result.getEvidenceVersion() == 1 && allowed.contains(result.getId())) {
                    var current = proof.snapshot(result);
                    var currentKeys = identities(current, key.path());
                    var old =
                            previous != null && previous.getEvidenceVersion() == 1 && allowed.contains(previous.getId())
                                    ? proof.snapshot(previous)
                                    : null;
                    var previousKeys =
                            old == null ? Map.<String, AdmittedProblemSnapshot>of() : identities(old, key.path());
                    boolean comparable = comparisons.get(member.getPracticeId()) == Comparison.COMPARABLE;
                    for (var entry : currentKeys.entrySet()) {
                        var prior = comparable ? previousKeys.get(entry.getKey()) : null;
                        var state = prior == null
                                ? ProblemBaselineState.NEW
                                : comparison
                                                .content(prior.candidate())
                                                .equals(comparison.content(
                                                        entry.getValue().candidate()))
                                        ? ProblemBaselineState.UNCHANGED
                                        : ProblemBaselineState.UPDATED;
                        comparisonFacts.add(new ScanProblemComparison(
                                entry.getValue().id(), state, prior == null ? null : prior.id()));
                        changes.merge(state, 1, Integer::sum);
                    }
                    if (old != null
                            && previous != null
                            && ProblemComparison.provesAbsence(
                                    old.candidate(),
                                    current.candidate(),
                                    comparable,
                                    member.getState() == ReviewItemState.JUDGED,
                                    !unmarked.contains(result.getId()) || !unmarked.contains(previous.getId()),
                                    allowed.contains(result.getId()) && allowed.contains(previous.getId())))
                        for (var prior : previousKeys.entrySet())
                            if (!currentKeys.containsKey(prior.getKey())) {
                                comparisonFacts.add(new ScanProblemComparison(
                                        prior.getValue().id(),
                                        ProblemBaselineState.ABSENT,
                                        prior.getValue().id()));
                                changes.merge(ProblemBaselineState.ABSENT, 1, Integer::sum);
                            }
                }
                ObjectNode detail = member.getDetail() == null
                        ? json.createObjectNode()
                        : (ObjectNode) member.getDetail().deepCopy();
                detail.set("problemComparison", json.valueToTree(comparisonFacts));
                member.setDetail(detail);
                memberships.saveAndFlush(member);
                for (var row : rows) {
                    problems.findByWorkspaceIdAndResultIdOrderByPositionAsc(review.getWorkspaceId(), row.getId())
                            .forEach(this::detach);
                    detach(row);
                }
                detach(member);
                if (priorMember != null) detach(priorMember);
            }
            if (pairs.size() < READ_BATCH) break;
            after = pairs.getLast();
        }
        return changes;
    }

    public Map<String, AdmittedProblemSnapshot> identities(AdmittedResultSnapshot result, String ownPath) {
        var ordinals = new HashMap<String, Integer>();
        var keys = new LinkedHashMap<String, AdmittedProblemSnapshot>();
        for (var problem : result.problems()) {
            var own = problem.candidate().citations().stream()
                    .filter(c -> c.path().equals(ownPath))
                    .findFirst();
            if (problem.candidate().key() == null && own.isEmpty()) continue;
            var quote = own.isEmpty()
                    ? ""
                    : ProblemComparison.normalize(own.orElseThrow().quote());
            int ordinal = ordinals.merge(quote, 1, Integer::sum) - 1;
            keys.put(
                    comparison.identity(
                            result.candidate().practiceId(),
                            result.candidate().itemId(),
                            problem.candidate(),
                            ownPath,
                            ordinal),
                    problem);
        }
        return keys;
    }

    private ProblemComparison.@Nullable Standard standard(@Nullable ReviewPractice assignment) {
        if (assignment == null || assignment.getPracticeVersionId() == null || assignment.getMethod() == null)
            return null;
        var version = versions.findByIdAndWorkspaceId(assignment.getPracticeVersionId(), assignment.getWorkspaceId())
                .orElseThrow();
        var definition = json.convertValue(version.getDefinition(), PracticeDefinition.class);
        return new ProblemComparison.Standard(
                ProblemComparison.sha256(
                        json.writeValueAsString(comparison.canonical(json.valueToTree(definition.scan())))),
                version.getReviewDigest(),
                assignment.getMethod().modelDigest(),
                assignment.getMethod().epoch());
    }

    private @Nullable Review baseline(Review current) {
        return reviews
                .findByWorkspaceIdAndItemIdOrderByOrderedAtDesc(current.getWorkspaceId(), current.getItemId())
                .stream()
                .filter(r -> r.getKind() == ReviewKind.SCAN
                        && r.getStatus().isTerminal()
                        && !r.getId().equals(current.getId())
                        && new ReviewOrder(r.getOrderedAt(), r.getCreatedAt(), r.getId())
                                        .compareTo(new ReviewOrder(
                                                current.getOrderedAt(), current.getCreatedAt(), current.getId()))
                                < 0
                        && r.getChecks()
                                .path("ref")
                                .asString()
                                .equals(current.getChecks().path("ref").asString()))
                .findFirst()
                .orElse(null);
    }

    private List<ReasonCountDTO> reasons(Review review, ReviewItemState state) {
        return jdbc.sql("SELECT COALESCE(reason,:fallback) AS reason,COUNT(*) AS count FROM review_item WHERE"
                        + " workspace_id=:ws AND review_id=:scan AND state=:state GROUP BY"
                        + " 1 ORDER BY 1")
                .param("ws", review.getWorkspaceId())
                .param("scan", review.getId())
                .param("state", state.name())
                .param(
                        "fallback",
                        review.getStopReason() == null
                                ? "EXECUTION_INCOMPLETE"
                                : review.getStopReason().name())
                .query((rs, n) -> new ReasonCountDTO(
                        rs.getString("reason"),
                        rs.getString("reason").replace('_', ' ').toLowerCase(Locale.ROOT),
                        Math.toIntExact(rs.getLong("count"))))
                .list();
    }

    public static ReviewMethodDTO method(ReviewMethod method) {
        return new ReviewMethodDTO(
                method.epoch(),
                method.modelDigest(),
                method.models().stream()
                        .map(s -> new BoundModelSlotDTO(
                                s.slot(),
                                s.kind(),
                                s.model() == null
                                        ? null
                                        : new FrozenModelDTO(
                                                s.model().modelId(),
                                                s.model().providerProtocol(),
                                                s.model().providerModelId(),
                                                s.model().reasoningEffort(),
                                                s.model().options())))
                        .toList());
    }

    private void detach(Object entity) {
        if (entities.contains(entity)) entities.detach(entity);
    }

    @Transactional
    public void expire(long workspaceId, UUID reviewId) {
        var review = reviews.lockByIdAndWorkspaceId(reviewId, workspaceId)
                .filter(r -> r.getKind() == ReviewKind.SCAN)
                .orElseThrow();
        if (!expired(review)) throw new IllegalStateException("The raw proof is still retained.");
        var frozen = recorded(review);
        int count = jdbc.sql("DELETE FROM review_item WHERE workspace_id=:ws AND review_id=:scan")
                .param("ws", workspaceId)
                .param("scan", reviewId)
                .update();
        review.setReport(json.valueToTree(new FrozenScanReport(
                frozen.schemaVersion(),
                frozen.listingComplete(),
                frozen.baselineScanId(),
                frozen.comparison(),
                frozen.recordedCounts(),
                frozen.problemChanges(),
                frozen.practices(),
                frozen.withheldReasons(),
                frozen.notReviewedReasons(),
                frozen.removalGuard(),
                true,
                count,
                frozen.redactedProofPairs())));
    }

    @Transactional
    public int eraseResultReferences(long workspaceId, Collection<UUID> resultIds) {
        if (resultIds.isEmpty()) return 0;
        var ids = new SqlArrayValue("uuid", resultIds.toArray());
        var affected = jdbc.sql("SELECT review_id,COUNT(*) AS count FROM review_item WHERE workspace_id=:ws AND"
                        + " result_id=ANY(:ids) GROUP BY review_id ORDER BY review_id")
                .param("ws", workspaceId)
                .param("ids", ids)
                .query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), rs.getInt(2)))
                .list();
        var frozen = new HashMap<UUID, FrozenScanReport>();
        for (var entry : affected) {
            var review =
                    reviews.lockByIdAndWorkspaceId(entry.getKey(), workspaceId).orElseThrow();
            if (review.getKind() == ReviewKind.SCAN) frozen.put(entry.getKey(), recorded(review));
        }
        jdbc.sql("UPDATE review_item m SET detail=jsonb_set(m.detail,'{problemComparison}',COALESCE((SELECT"
                        + " jsonb_agg(CASE WHEN (c->>'previousProblemId')::uuid IN (SELECT id FROM problem WHERE"
                        + " workspace_id=:ws AND result_id=ANY(:ids)) THEN c-'previousProblemId' ELSE c END) FROM"
                        + " jsonb_array_elements(COALESCE(m.detail->'problemComparison','[]'::jsonb)) c WHERE"
                        + " (c->>'problemId')::uuid NOT IN (SELECT id FROM problem WHERE workspace_id=:ws AND"
                        + " result_id=ANY(:ids))),'[]'::jsonb)) WHERE m.workspace_id=:ws AND"
                        + " jsonb_exists(m.detail,'problemComparison')")
                .param("ws", workspaceId)
                .param("ids", new SqlArrayValue("uuid", resultIds.toArray()))
                .update();
        int deleted = jdbc.sql("DELETE FROM review_item WHERE workspace_id=:ws AND result_id=ANY(:ids)")
                .param("ws", workspaceId)
                .param("ids", new SqlArrayValue("uuid", resultIds.toArray()))
                .update();
        for (var entry : affected)
            if (frozen.containsKey(entry.getKey())) {
                var review = reviews.findByIdAndWorkspaceId(entry.getKey(), workspaceId)
                        .orElseThrow();
                var f = frozen.get(entry.getKey());
                review.setReport(json.valueToTree(new FrozenScanReport(
                        f.schemaVersion(),
                        f.listingComplete(),
                        f.baselineScanId(),
                        f.comparison(),
                        f.recordedCounts(),
                        f.problemChanges(),
                        f.practices(),
                        f.withheldReasons(),
                        f.notReviewedReasons(),
                        f.removalGuard(),
                        f.expired(),
                        f.expiredProofPairs(),
                        f.redactedProofPairs() + entry.getValue())));
            }
        return deleted;
    }
}
